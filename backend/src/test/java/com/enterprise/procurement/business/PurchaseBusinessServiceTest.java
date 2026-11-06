package com.enterprise.procurement.business;

import com.enterprise.procurement.dto.PurchaseRequestDtos;
import com.enterprise.procurement.dto.PurchaseViewDtos;
import com.enterprise.procurement.exception.ForbiddenException;
import com.enterprise.procurement.exception.StateConflictException;
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
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
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
    @Mock IdempotencyService idempotency;

    @InjectMocks PurchaseBusinessService service;

    /**
     * 默认"没有幂等键"：所有既有用例都走不做幂等的分支，行为与改造前一致。
     * 需要验证幂等的用例自己覆盖这个桩。
     */
    @org.junit.jupiter.api.BeforeEach
    void stubIdempotencyAsDisabled() {
        when(idempotency.claim(any(), any(), any(), any()))
                .thenReturn(new IdempotencyService.Claim(false, null));
    }

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
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("不能审批本人提交的申请");
        verify(requests, never()).transition(anyLong(), anyString(), anyString(), anyInt());
    }

    @Test
    @DisplayName("跨部门不能审批（双方部门都不为空）")
    void cannotApproveOtherDepartment() {
        user(4L, 2L, "MANAGER");
        request(100L, 1L, 1L, "PENDING");

        assertThatThrownBy(() -> service.approve(100L, "u4", new PurchaseRequestDtos.Approval("APPROVE", "ok")))
                .isInstanceOf(ForbiddenException.class)
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
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("CAS 影响行数为 0（并发下已被他人处理）→ 409 冲突，而不是 400 参数错")
    void approveConflictWhenCasMisses() {
        user(3L, 1L, "MANAGER");
        request(100L, 1L, 1L, "PENDING");
        when(requests.transition(100L, "PENDING", "APPROVED", 2)).thenReturn(0);

        assertThatThrownBy(() -> service.approve(100L, "u3", new PurchaseRequestDtos.Approval("APPROVE", "ok")))
                .isInstanceOf(StateConflictException.class)
                .hasMessage("申请状态已变更");
    }

    // ---------------------------------------------------------------- 草稿竞态

    @Test
    @DisplayName("编辑期间被并发提交：条件更新 0 行 → 冲突，绝不用 updateById 把状态写回去")
    void updateDraftLosesRaceToSubmit() {
        user(1L, 1L, "EMPLOYEE");
        request(100L, 1L, 1L, "DRAFT"); // 读到 DRAFT，但提交发生在读与写之间
        when(catalog.product(1L)).thenReturn(new ProductEntity());
        when(requests.updateDraftFields(eq(100L), eq(1L), eq("改后的理由"), any())).thenReturn(0);

        var dto = new PurchaseRequestDtos.Update("改后的理由",
                List.of(new PurchaseRequestDtos.Item(1L, 2, new java.math.BigDecimal("3.50"))));
        assertThatThrownBy(() -> service.updateDraft(100L, "u1", dto))
                .isInstanceOf(StateConflictException.class)
                .hasMessageContaining("审批流程");
        // 关键回归点：全字段覆盖的旧路径必须死掉，否则 PENDING 会被写回 DRAFT
        verify(requests, never()).updateById(any(PurchaseRequestEntity.class));
    }

    @Test
    @DisplayName("编辑草稿成功：明细重写并归并，总额走条件更新落库")
    void updateDraftRewritesItemsAndTotal() {
        user(1L, 1L, "EMPLOYEE");
        request(100L, 1L, 1L, "DRAFT");
        ProductEntity p = new ProductEntity();
        p.setId(1L);
        p.setName("A4 纸");
        when(catalog.product(1L)).thenReturn(p);
        when(requests.updateDraftFields(eq(100L), eq(1L), eq("新理由"), any())).thenReturn(1);

        var dto = new PurchaseRequestDtos.Update("新理由", List.of(
                new PurchaseRequestDtos.Item(1L, 2, new java.math.BigDecimal("3.50")),
                new PurchaseRequestDtos.Item(1L, 3, new java.math.BigDecimal("3.50"))));
        service.updateDraft(100L, "u1", dto);

        verify(requestItems).deleteByRequest(100L);
        ArgumentCaptor<PurchaseRequestItemEntity> captor = ArgumentCaptor.forClass(PurchaseRequestItemEntity.class);
        verify(requestItems, times(1)).insert(captor.capture()); // 两行同商品归并成一行
        assertThat(captor.getValue().getQuantity()).isEqualTo(5);
    }

    @Test
    @DisplayName("删除草稿：已不是 DRAFT 时条件删除 0 行 → 冲突，不执行无条件 DELETE")
    void deleteDraftOnlyWhenStillDraft() {
        user(1L, 1L, "EMPLOYEE");
        request(100L, 1L, 1L, "DRAFT");
        when(requests.deleteDraftRow(100L, 1L)).thenReturn(0);

        assertThatThrownBy(() -> service.deleteDraft(100L, "u1"))
                .isInstanceOf(StateConflictException.class)
                .hasMessageContaining("审批流程");
        verify(requests, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("建单带幂等键：成功后把业务主键写回幂等记录，重试才能回放")
    void createDraftRecordsIdempotencyResult() {
        user(1L, 1L, "EMPLOYEE");
        ProductEntity p = new ProductEntity();
        p.setId(1L);
        p.setName("A4 纸");
        when(catalog.product(1L)).thenReturn(p);
        when(documentNo.nextRequestNo()).thenReturn("PR20261106000001");
        when(requests.updateDraftFields(anyLong(), eq(1L), anyString(), any())).thenReturn(1);
        doAnswer(inv -> {
            ((PurchaseRequestEntity) inv.getArgument(0)).setId(500L); // 模拟自增主键回填
            return 1;
        }).when(requests).insert(any(PurchaseRequestEntity.class));

        var dto = new PurchaseRequestDtos.Create("买打印纸",
                List.of(new PurchaseRequestDtos.Item(1L, 2, new java.math.BigDecimal("3.00"))), false);
        PurchaseRequestEntity created = service.createDraft("u1", dto, "key-abc-123");

        assertThat(created.getId()).isEqualTo(500L);
        verify(idempotency).complete("key-abc-123", 1L, 500L);
    }

    @Test
    @DisplayName("建单即提交：一次请求完成 DRAFT→PENDING，不再有第二跳失败留孤儿草稿的窗口")
    void createDraftWithSubmitTransitionsInSameCall() {
        user(1L, 1L, "EMPLOYEE");
        ProductEntity p = new ProductEntity();
        p.setId(1L);
        p.setName("A4 纸");
        when(catalog.product(1L)).thenReturn(p);
        when(documentNo.nextRequestNo()).thenReturn("PR20261106000002");
        when(requests.updateDraftFields(anyLong(), eq(1L), anyString(), any())).thenReturn(1);
        doAnswer(inv -> {
            ((PurchaseRequestEntity) inv.getArgument(0)).setId(501L);
            return 1;
        }).when(requests).insert(any(PurchaseRequestEntity.class));
        when(requests.transition(501L, "DRAFT", "PENDING", 0)).thenReturn(1);
        PurchaseRequestEntity afterSubmit = new PurchaseRequestEntity();
        afterSubmit.setId(501L);
        afterSubmit.setStatus("PENDING");
        afterSubmit.setVersion(1);
        when(requests.selectById(501L)).thenReturn(afterSubmit);

        var dto = new PurchaseRequestDtos.Create("立即提交",
                List.of(new PurchaseRequestDtos.Item(1L, 1, new java.math.BigDecimal("3.00"))), true);
        PurchaseRequestEntity created = service.createDraft("u1", dto, null);

        assertThat(created.getStatus()).isEqualTo("PENDING");
        verify(requests).transition(501L, "DRAFT", "PENDING", 0);
    }

    @Test
    @DisplayName("建单命中幂等：直接返回上次那张单，不再插入第二张")
    void createDraftReplaysExistingWhenKeyAlreadyUsed() {
        user(1L, 1L, "EMPLOYEE");
        when(idempotency.claim(eq("key-abc-123"), eq(1L), eq("CREATE_REQUEST"), any()))
                .thenReturn(new IdempotencyService.Claim(true, 500L));
        PurchaseRequestEntity existing = new PurchaseRequestEntity();
        existing.setId(500L);
        existing.setStatus("DRAFT");
        when(requests.selectById(500L)).thenReturn(existing);

        var dto = new PurchaseRequestDtos.Create("买打印纸",
                List.of(new PurchaseRequestDtos.Item(1L, 2, new java.math.BigDecimal("3.00"))), false);
        PurchaseRequestEntity got = service.createDraft("u1", dto, "key-abc-123");

        assertThat(got.getId()).isEqualTo(500L);
        verify(requests, never()).insert(any(PurchaseRequestEntity.class));
    }

    @Test
    @DisplayName("调整库存命中幂等：直接返回成功，库存与流水都不再动")
    void repeatedAdjustWithSameKeyDoesNotTouchStock() {
        when(catalog.product(1L)).thenReturn(new ProductEntity());
        when(idempotency.claim(eq("st-key-0001"), eq(9L), eq("ADJUST_STOCK"),
                eq(IdempotencyService.fingerprint(1L, 5))))
                .thenReturn(new IdempotencyService.Claim(true, null));

        service.adjustStock(1L, 5, 9L, "st-key-0001");

        verify(stock, never()).upsertAndAdd(anyLong(), anyInt());
        verify(transactions, never()).insert(any(InventoryTransactionEntity.class));
    }

    @Test
    @DisplayName("重复提交：已是 PENDING 且是本人 → 幂等成功，不再走一次状态流转")
    void submitIsIdempotentWhenAlreadyPending() {
        user(1L, 1L, "EMPLOYEE");
        request(100L, 1L, 1L, "PENDING");

        assertThat(service.submit(100L, "u1").getStatus()).isEqualTo("PENDING");
        verify(requests, never()).transition(anyLong(), anyString(), anyString(), anyInt());
    }

    // ---------------------------------------------------------------- 入库

    @Test
    @DisplayName("重复入库：前置状态检查即拒绝，不会去动库存")
    void deliveredConflictDoesNotTouchStock() {
        PurchaseOrderEntity o = new PurchaseOrderEntity();
        o.setId(7L);
        o.setVersion(1);
        o.setStatus("DELIVERED");
        when(orders.selectById(7L)).thenReturn(o);

        assertThatThrownBy(() -> service.delivered(7L, 5L))
                .isInstanceOf(StateConflictException.class)
                .hasMessage("订单已入库，请勿重复操作");
        verify(stock, never()).upsertAndAdd(anyLong(), anyInt());
        verify(orders, never()).transition(anyLong(), anyString(), anyString(), anyInt());
    }

    @Test
    @DisplayName("多商品入库按商品号升序加锁：交叉订单不会互持行锁导致死锁")
    void deliveredLocksProductRowsInAscendingOrder() {
        PurchaseOrderEntity o = new PurchaseOrderEntity();
        o.setId(7L);
        o.setOrderNo("PO20261029000001");
        o.setVersion(1);
        o.setStatus("CREATED");
        when(orders.selectById(7L)).thenReturn(o);
        when(orders.transition(7L, "CREATED", "DELIVERED", 1)).thenReturn(1);

        PurchaseOrderItemEntity big = new PurchaseOrderItemEntity();   // 明细顺序故意倒着放
        big.setProductId(9L);
        big.setQuantity(1);
        PurchaseOrderItemEntity small = new PurchaseOrderItemEntity();
        small.setProductId(5L);
        small.setQuantity(2);
        when(orderItems.byOrder(7L)).thenReturn(List.of(big, small));

        service.delivered(7L, 5L);

        InOrder inOrder = inOrder(stock);
        inOrder.verify(stock).upsertAndAdd(5L, 2); // 先锁小号
        inOrder.verify(stock).upsertAndAdd(9L, 1); // 再锁大号——所有事务同序，环消失
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
    @DisplayName("出库扣不到（并发下抢输了/本来就不够）：抛 409 冲突，不写流水")
    void adjustOutboundFailsWhenStockNotEnough() {
        when(catalog.product(1L)).thenReturn(new ProductEntity());
        when(stock.deduct(1L, 999)).thenReturn(0);

        assertThatThrownBy(() -> service.adjustStock(1L, -999, 9L))
                .isInstanceOf(StateConflictException.class)
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
    @DisplayName("订单分页：普通员工走带归属条件的分页查询，total 来自服务端而不是当前页行数")
    void orderPageScopesQueryForEmployee() {
        user(2L, 5L, "EMPLOYEE");
        PurchaseOrderRow o = new PurchaseOrderRow();
        o.setId(7L);
        o.setOrderNo("PO20261029000001");
        o.setStatus("CREATED");

        com.baomidou.mybatisplus.extension.plugins.pagination.Page<PurchaseOrderRow> page =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 10);
        page.setRecords(List.of(o));
        page.setTotal(37L);
        when(orders.pageFor(any(), eq(2L), eq(5L), isNull(), isNull(), isNull())).thenReturn(page);

        var result = service.orderPage("u2", null, null, null, 1, 10);

        assertThat(result.total()).isEqualTo(37L);
        assertThat(result.records()).singleElement()
                .satisfies(r -> assertThat(r.orderNo()).isEqualTo("PO20261029000001"));
        verify(orders, never()).pageAll(any(), any(), any(), any());
    }

    @Test
    @DisplayName("编辑已提交的单：状态不对属于冲突（409），不是参数错（400）")
    void updateNonDraftIsConflictNotBadRequest() {
        user(1L, 1L, "EMPLOYEE");
        request(100L, 1L, 1L, "PENDING");

        var dto = new PurchaseRequestDtos.Update("还想改",
                List.of(new PurchaseRequestDtos.Item(1L, 1, new java.math.BigDecimal("1.00"))));
        assertThatThrownBy(() -> service.updateDraft(100L, "u1", dto))
                .isInstanceOf(StateConflictException.class)
                .hasMessageContaining("审批流程");
    }

    @Test
    @DisplayName("对已下单的申请再下单：错误码精确到 REQUEST_ALREADY_ORDERED")
    void createOrderOnPurchasingGivesPreciseCode() {
        user(4L, 5L, "PURCHASER");
        request(100L, 1L, 1L, "PURCHASING");

        assertThatThrownBy(() -> service.createOrder(100L, 1L))
                .isInstanceOf(StateConflictException.class)
                .satisfies(e -> assertThat(((StateConflictException) e).errorCode())
                        .isEqualTo(com.enterprise.procurement.exception.ErrorCode.REQUEST_ALREADY_ORDERED));
    }

    @Test
    @DisplayName("订单详情：带出明细行 —— 改前点开订单看不到买了什么")
    void orderDetailCarriesItems() {
        user(9L, null, "ADMIN");
        PurchaseOrderEntity o = new PurchaseOrderEntity();
        o.setId(7L);
        o.setOrderNo("PO20261029000001");
        o.setRequestId(100L);
        o.setVersion(1);
        o.setStatus("CREATED");
        o.setTotalAmount(new java.math.BigDecimal("45.00"));
        when(orders.selectById(7L)).thenReturn(o);
        when(orders.rowById(7L)).thenReturn(orderRowRow());

        PurchaseOrderItemEntity item = new PurchaseOrderItemEntity();
        item.setId(3L);
        item.setProductId(1L);
        item.setProductName("A4 纸");
        item.setQuantity(5);
        item.setUnitPrice(new java.math.BigDecimal("9.00"));
        item.setAmount(new java.math.BigDecimal("45.00"));
        when(orderItems.byOrder(7L)).thenReturn(List.of(item));

        var detail = service.orderDetail(7L, "u9");

        assertThat(detail.supplier()).isEqualTo("华夏办公");
        assertThat(detail.items()).singleElement()
                .satisfies(i -> assertThat(i.productName()).isEqualTo("A4 纸"));
    }

    @Test
    @DisplayName("审批留痕：越权时 404 且不查审批记录（不泄露这张单是否被批过）")
    void approvalHistoryRequiresVisibility() {
        user(2L, 1L, "EMPLOYEE");
        request(100L, 1L, 1L, "PENDING");

        assertThatThrownBy(() -> service.approvalHistory(100L, "u2"))
                .isInstanceOf(NoSuchElementException.class);
        verify(approvals, never()).rowsByRequest(anyLong());
    }

    private PurchaseOrderRow orderRowRow() {
        PurchaseOrderRow r = new PurchaseOrderRow();
        r.setId(7L);
        r.setOrderNo("PO20261029000001");
        r.setRequestId(100L);
        r.setRequestNo("PR20261029000001");
        r.setReason("买纸");
        r.setSupplier("华夏办公");
        r.setAmount(new java.math.BigDecimal("45.00"));
        r.setStatus("CREATED");
        r.setCreateTime(java.time.LocalDateTime.of(2026, 10, 29, 20, 0));
        return r;
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
