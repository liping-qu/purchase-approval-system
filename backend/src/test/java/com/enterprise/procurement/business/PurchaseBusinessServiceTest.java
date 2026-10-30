package com.enterprise.procurement.business;

import com.enterprise.procurement.dto.PurchaseRequestDtos;
import com.enterprise.procurement.dto.PurchaseViewDtos;
import com.enterprise.procurement.persistence.entity.InventoryTransactionEntity;
import com.enterprise.procurement.persistence.entity.ProductEntity;
import com.enterprise.procurement.persistence.entity.PurchaseOrderEntity;
import com.enterprise.procurement.persistence.entity.PurchaseOrderItemEntity;
import com.enterprise.procurement.persistence.entity.PurchaseOrderRow;
import com.enterprise.procurement.persistence.entity.PurchaseRequestEntity;
import com.enterprise.procurement.persistence.entity.PurchaseRequestItemEntity;
import com.enterprise.procurement.persistence.entity.PurchaseRequestRow;
import com.enterprise.procurement.persistence.entity.SystemUserRow;
import com.enterprise.procurement.persistence.mapper.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 业务规则单元测试：把"越权/并发"这类最容易回归的判断钉死。
 *
 * <p>这些用例全部用 Mock 隔离数据库，跑一次不到一秒，适合放进提交前的快速门禁。
 * 真正的 SQL 正确性（CAS 语句怎么写、唯一键能不能挡住）不在这里验证，
 * 那部分靠并发实测覆盖 —— 单测替代不了它，两者是互补而非替代关系。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PurchaseBusinessServiceTest {

    @Mock PurchaseRequestMapper requests;
    @Mock PurchaseRequestItemMapper requestItems;
    @Mock ApprovalRecordMapper approvals;
    @Mock PurchaseOrderMapper orders;
    @Mock PurchaseOrderItemMapper orderItems;
    @Mock InventoryStockMapper stock;
    @Mock InventoryTransactionMapper transactions;
    @Mock CatalogMapper catalog;
    @Mock DocumentNoGenerator documentNo;

    @InjectMocks PurchaseBusinessService service;

    // ---------------------------------------------------------------- 测试数据

    private SystemUserRow user(long id, Long departmentId, String... roles) {
        SystemUserRow row = new SystemUserRow();
        row.setId(id);
        row.setUsername("u" + id);
        row.setDepartmentId(departmentId);
        row.setTokenVersion(0);
        when(catalog.user("u" + id)).thenReturn(row);
        when(catalog.roleCodes(id)).thenReturn(List.of(roles));
        return row;
    }

    private PurchaseRequestEntity request(long id, long applicantId, Long departmentId, String status) {
        PurchaseRequestEntity r = new PurchaseRequestEntity();
        r.setId(id);
        r.setApplicantId(applicantId);
        r.setDepartmentId(departmentId);
        r.setStatus(status);
        r.setVersion(2);
        when(requests.selectById(id)).thenReturn(r);
        return r;
    }

    // ---------------------------------------------------------------- 可见性

    @Test
    @DisplayName("同部门但不是申请人、也不是负责人 —— 读不到别人的申请")
    void otherEmployeeCannotReadRequest() {
        user(2L, 1L, "EMPLOYEE");
        request(100L, 1L, 1L, "PENDING");

        assertThatThrownBy(() -> service.get(100L, "u2"))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessage("采购申请不存在");
    }

    @Test
    @DisplayName("本人可以读自己的申请")
    void applicantCanReadOwnRequest() {
        user(1L, 1L, "EMPLOYEE");
        request(100L, 1L, 1L, "PENDING");

        assertThat(service.get(100L, "u1").getId()).isEqualTo(100L);
    }

    @Test
    @DisplayName("管理员可以读任何部门的申请")
    void adminCanReadAnything() {
        user(9L, null, "ADMIN");
        request(100L, 1L, 7L, "PENDING");

        assertThat(service.get(100L, "u9")).isNotNull();
    }

    // ---------------------------------------------------------------- 待办

    @Test
    @DisplayName("没有部门的负责人：待办为空，且不发出任何待办查询")
    void pendingIsEmptyWhenNoDepartment() {
        user(5L, null, "MANAGER");

        assertThat(service.pending("u5", null)).isEmpty();
        verify(requests, never()).pendingRows(any(), any());
    }

    // ---------------------------------------------------------------- 审批

    @Test
    @DisplayName("不能审批本人提交的申请")
    void cannotApproveOwnRequest() {
        user(3L, 1L, "MANAGER");
        request(100L, 3L, 1L, "PENDING");

        assertThatThrownBy(() -> service.approve(100L, "u3", new PurchaseRequestDtos.Approval("APPROVE", "ok")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("不能审批本人提交的申请");
        verify(requests, never()).transition(anyLong(), anyString(), anyString(), anyInt());
    }

    @Test
    @DisplayName("跨部门不能审批（双方部门都不为空）")
    void cannotApproveOtherDepartment() {
        user(4L, 2L, "MANAGER");
        request(100L, 1L, 1L, "PENDING");

        assertThatThrownBy(() -> service.approve(100L, "u4", new PurchaseRequestDtos.Approval("APPROVE", "ok")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("仅本部门负责人可审批");
    }

    @Test
    @DisplayName("审批人没有部门时不能审批 —— 两个 null 不算相等")
    void nullDepartmentMustNotPass() {
        user(6L, null, "MANAGER");
        PurchaseRequestEntity r = new PurchaseRequestEntity();
        r.setId(100L);
        r.setApplicantId(1L);
        r.setDepartmentId(null);
        r.setStatus("PENDING");
        r.setVersion(1);
        when(requests.selectById(100L)).thenReturn(r);

        assertThatThrownBy(() -> service.approve(100L, "u6", new PurchaseRequestDtos.Approval("APPROVE", "ok")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("CAS 影响行数为 0（并发下已被他人处理）时报状态已变更")
    void approveConflictWhenCasMisses() {
        user(3L, 1L, "MANAGER");
        request(100L, 1L, 1L, "PENDING");
        when(requests.transition(100L, "PENDING", "APPROVED", 2)).thenReturn(0);

        assertThatThrownBy(() -> service.approve(100L, "u3", new PurchaseRequestDtos.Approval("APPROVE", "ok")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("申请状态已变更");
    }

    // ---------------------------------------------------------------- 入库

    @Test
    @DisplayName("重复入库：状态流转失败即拒绝，不会去动库存")
    void deliveredConflictDoesNotTouchStock() {
        PurchaseOrderEntity o = new PurchaseOrderEntity();
        o.setId(7L);
        o.setVersion(1);
        o.setStatus("DELIVERED");
        when(orders.selectById(7L)).thenReturn(o);
        when(orders.transition(7L, "CREATED", "DELIVERED", 1)).thenReturn(0);

        assertThatThrownBy(() -> service.delivered(7L, 5L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("订单已处理");
        verify(stock, never()).upsertAndAdd(anyLong(), anyInt());
    }

    @Test
    @DisplayName("同一订单里重复商品：归并后只累加一次库存、只写一条流水")
    void deliveredMergesDuplicateProducts() {
        PurchaseOrderEntity o = new PurchaseOrderEntity();
        o.setId(7L);
        o.setOrderNo("PO20261029000001");
        o.setVersion(1);
        o.setStatus("CREATED");
        when(orders.selectById(7L)).thenReturn(o);
        when(orders.transition(7L, "CREATED", "DELIVERED", 1)).thenReturn(1);

        PurchaseOrderItemEntity a = new PurchaseOrderItemEntity();
        a.setProductId(1L);
        a.setQuantity(2);
        PurchaseOrderItemEntity b = new PurchaseOrderItemEntity();
        b.setProductId(1L);
        b.setQuantity(3);
        when(orderItems.byOrder(7L)).thenReturn(List.of(a, b));

        service.delivered(7L, 5L);

        // 2 + 3 合成一次 5，而不是两次调用（两次会撞 uk_inventory_tx_ref）
        verify(stock, times(1)).upsertAndAdd(1L, 5);
        verify(transactions, times(1)).insert(any(InventoryTransactionEntity.class));
    }

    // ---------------------------------------------------------------- 手工调整库存

    @Test
    @DisplayName("手工入库：走 upsert 累加，并写一条 IN 流水（带单号与操作人）")
    void adjustInboundAddsStockAndWritesTransaction() {
        when(catalog.product(1L)).thenReturn(new ProductEntity());
        when(documentNo.nextStockNo()).thenReturn("ST20261030000001");

        service.adjustStock(1L, 5, 9L);

        verify(stock).upsertAndAdd(1L, 5);
        ArgumentCaptor<InventoryTransactionEntity> captor = ArgumentCaptor.forClass(InventoryTransactionEntity.class);
        verify(transactions).insert(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo("IN");
        assertThat(captor.getValue().getQuantity()).isEqualTo(5);
        assertThat(captor.getValue().getReferenceNo()).isEqualTo("ST20261030000001");
        assertThat(captor.getValue().getOperatorId()).isEqualTo(9L);
    }

    @Test
    @DisplayName("手工出库：扣减成功写一条 OUT 流水，数量取绝对值")
    void adjustOutboundDeductsAndWritesTransaction() {
        when(catalog.product(1L)).thenReturn(new ProductEntity());
        when(stock.deduct(1L, 3)).thenReturn(1);
        when(documentNo.nextStockNo()).thenReturn("ST20261030000002");

        service.adjustStock(1L, -3, 9L);

        verify(stock).deduct(1L, 3);
        verify(stock, never()).upsertAndAdd(anyLong(), anyInt());
        ArgumentCaptor<InventoryTransactionEntity> captor = ArgumentCaptor.forClass(InventoryTransactionEntity.class);
        verify(transactions).insert(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo("OUT");
        assertThat(captor.getValue().getQuantity()).isEqualTo(3);
    }

    @Test
    @DisplayName("出库扣不到（并发下抢输了/本来就不够）：抛库存不足，不写流水")
    void adjustOutboundFailsWhenStockNotEnough() {
        when(catalog.product(1L)).thenReturn(new ProductEntity());
        when(stock.deduct(1L, 999)).thenReturn(0);

        assertThatThrownBy(() -> service.adjustStock(1L, -999, 9L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("库存不足");
        verify(transactions, never()).insert(any(InventoryTransactionEntity.class));
    }

    @Test
    @DisplayName("调整数量为 0 或 null：直接 400，连商品都不用查")
    void adjustRejectsZeroOrNullQuantity() {
        assertThatThrownBy(() -> service.adjustStock(1L, 0, 9L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不能为 0");
        assertThatThrownBy(() -> service.adjustStock(1L, null, 9L))
                .isInstanceOf(IllegalArgumentException.class);
        verify(stock, never()).upsertAndAdd(anyLong(), anyInt());
        verify(stock, never()).deduct(anyLong(), anyInt());
    }

    @Test
    @DisplayName("商品不存在或已停用：拒绝调整，避免给不存在的商品造库存")
    void adjustRejectsUnknownProduct() {
        when(catalog.product(404L)).thenReturn(null);

        assertThatThrownBy(() -> service.adjustStock(404L, 1, 9L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("商品不存在");
        verify(stock, never()).upsertAndAdd(anyLong(), anyInt());
    }

    // ---------------------------------------------------------------- 展示态契约

    private PurchaseRequestRow row(long id, long applicantId, Long departmentId, String status) {
        PurchaseRequestRow r = new PurchaseRequestRow();
        r.setId(id);
        r.setRequestNo("PR20261029000001");
        r.setReason("采购开发设备");
        r.setApplicant("张三");
        r.setDepartment("技术部");
        r.setAmount(new java.math.BigDecimal("45800.00"));
        r.setStatus(status);
        r.setCreateTime(java.time.LocalDateTime.of(2026, 10, 29, 21, 3));
        r.setApplicantId(applicantId);
        r.setDepartmentId(departmentId);
        return r;
    }

    @Test
    @DisplayName("详情：展示态带出申请人/部门姓名与明细，页面不再需要自己拼")
    void detailCarriesNamesAndItems() {
        user(1L, 1L, "EMPLOYEE");
        when(requests.rowById(100L)).thenReturn(row(100L, 1L, 1L, "PENDING"));

        PurchaseRequestItemEntity item = new PurchaseRequestItemEntity();
        item.setId(9L);
        item.setProductId(3L);
        item.setProductName("Mac Mini");
        item.setQuantity(2);
        item.setUnitPrice(new java.math.BigDecimal("12000.00"));
        item.setAmount(new java.math.BigDecimal("24000.00"));
        when(requestItems.byRequest(100L)).thenReturn(List.of(item));

        PurchaseViewDtos.RequestDetail detail = service.detail(100L, "u1");

        assertThat(detail.applicant()).isEqualTo("张三");
        assertThat(detail.department()).isEqualTo("技术部");
        assertThat(detail.amount()).isEqualByComparingTo("45800.00");
        assertThat(detail.items()).singleElement()
                .satisfies(i -> assertThat(i.productName()).isEqualTo("Mac Mini"));
    }

    @Test
    @DisplayName("详情：别人读不到，且不会去查明细（越权即短路）")
    void detailRejectsNonOwnerWithoutTouchingItems() {
        user(2L, 1L, "EMPLOYEE");
        when(requests.rowById(100L)).thenReturn(row(100L, 1L, 1L, "PENDING"));

        assertThatThrownBy(() -> service.detail(100L, "u2"))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessage("采购申请不存在");
        verify(requestItems, never()).byRequest(anyLong());
    }

    @Test
    @DisplayName("订单列表：普通员工走带归属条件的查询，而不是拉全量再逐行点查")
    void orderListUsesScopedQueryForEmployee() {
        user(2L, 5L, "EMPLOYEE");
        PurchaseOrderRow o = new PurchaseOrderRow();
        o.setId(7L);
        o.setOrderNo("PO20261029000001");
        o.setRequestNo("PR20261029000001");
        o.setSupplier("鼎盛办公");
        o.setStatus("CREATED");
        when(orders.rowsFor(2L, 5L)).thenReturn(List.of(o));

        List<PurchaseViewDtos.OrderRow> rows = service.orderList("u2");

        assertThat(rows).singleElement().satisfies(r -> {
            assertThat(r.requestNo()).isEqualTo("PR20261029000001");
            assertThat(r.supplier()).isEqualTo("鼎盛办公");
        });
        verify(orders, never()).rows();
    }

    @Test
    @DisplayName("可下单申请：只返回审批通过且尚无订单的单据")
    void orderableRequestsMapToViewRows() {
        when(requests.orderableRows()).thenReturn(List.of(row(100L, 1L, 1L, "APPROVED")));

        assertThat(service.orderableRequests()).singleElement()
                .satisfies(r -> {
                    assertThat(r.id()).isEqualTo(100L);
                    assertThat(r.status()).isEqualTo("APPROVED");
                });
    }

    @Test
    @DisplayName("订单入库后申请单同步走到 COMPLETED —— 否则完成率永远是 0")
    void deliveredCompletesLinkedRequest() {
        PurchaseOrderEntity o = new PurchaseOrderEntity();
        o.setId(7L);
        o.setOrderNo("PO20261029000001");
        o.setRequestId(100L);
        o.setVersion(1);
        o.setStatus("CREATED");
        when(orders.selectById(7L)).thenReturn(o);
        when(orders.transition(7L, "CREATED", "DELIVERED", 1)).thenReturn(1);
        when(orderItems.byOrder(7L)).thenReturn(List.of());

        PurchaseRequestEntity r = new PurchaseRequestEntity();
        r.setId(100L);
        r.setStatus("PURCHASING");
        r.setVersion(3);
        when(requests.selectById(100L)).thenReturn(r);
        when(requests.transition(100L, "PURCHASING", "COMPLETED", 3)).thenReturn(1);

        service.delivered(7L, 5L);

        verify(requests).transition(100L, "PURCHASING", "COMPLETED", 3);
    }
}
