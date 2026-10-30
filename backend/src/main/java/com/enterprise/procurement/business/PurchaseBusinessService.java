package com.enterprise.procurement.business;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.enterprise.procurement.dto.PurchaseRequestDtos;
import com.enterprise.procurement.model.Enums.ApplicationStatus;
import com.enterprise.procurement.model.Enums.OrderStatus;
import com.enterprise.procurement.persistence.entity.*;
import com.enterprise.procurement.persistence.mapper.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 采购主流程：申请 → 提交 → 审批 → 下单 → 入库。
 *
 * <p>本类同时承担"谁能看什么、谁能做什么"的判断。这类规则很集中、也和业务强相关，
 * 放在 Controller 里会散成 5 处重复的 if，放在独立的安全组件里又会脱离状态语义，
 * 因此统一收敛到这里，由 {@link Viewer} 表达"当前操作者"。</p>
 */
@Service
public class PurchaseBusinessService {

    /** 采购员需要看到的"已批准及之后"的状态集合。 */
    private static final Set<String> ORDERABLE_STATUSES =
            Set.of(ApplicationStatus.APPROVED.name(), ApplicationStatus.PURCHASING.name(),
                    ApplicationStatus.COMPLETED.name());

    private final PurchaseRequestMapper requests;
    private final PurchaseRequestItemMapper requestItems;
    private final ApprovalRecordMapper approvals;
    private final PurchaseOrderMapper orders;
    private final PurchaseOrderItemMapper orderItems;
    private final InventoryStockMapper stock;
    private final InventoryTransactionMapper transactions;
    private final CatalogMapper catalog;
    private final DocumentNoGenerator documentNo;

    public PurchaseBusinessService(PurchaseRequestMapper r, PurchaseRequestItemMapper ri, ApprovalRecordMapper a,
                                   PurchaseOrderMapper o, PurchaseOrderItemMapper oi, InventoryStockMapper s,
                                   InventoryTransactionMapper t, CatalogMapper c, DocumentNoGenerator documentNo) {
        this.requests = r;
        this.requestItems = ri;
        this.approvals = a;
        this.orders = o;
        this.orderItems = oi;
        this.stock = s;
        this.transactions = t;
        this.catalog = c;
        this.documentNo = documentNo;
    }

    // ------------------------------------------------------------------ 身份

    /** 当前操作者：只带授权判断所需的三个维度，避免把整个用户实体在业务层乱传。 */
    public record Viewer(Long id, Long departmentId, Set<String> roles) {
        boolean has(String role) { return roles.contains(role); }
        boolean admin() { return has("ADMIN"); }
    }

    public Viewer viewer(String username) {
        SystemUserRow u = catalog.user(username);
        if (u == null) {
            throw new NoSuchElementException("用户不存在或已停用");
        }
        return new Viewer(u.getId(), u.getDepartmentId(), new HashSet<>(catalog.roleCodes(u.getId())));
    }

    public Long userId(String username) {
        return viewer(username).id();
    }

    // ------------------------------------------------------------------ 可见性

    /**
     * 申请单的可见范围。
     *
     * <p>改造前 {@code GET /api/purchase-requests/{id}} 不做任何归属判断，
     * 任何登录用户把 id 从 1 递增就能读到别人的事由与金额（IDOR）。
     * 这里按"最小可见"收口：</p>
     * <ul>
     *   <li>ADMIN：全部；</li>
     *   <li>申请人本人：自己的；</li>
     *   <li>MANAGER：本部门的（审批需要看上下文）；</li>
     *   <li>PURCHASER：已通过审批的单据（下单需要看明细）；</li>
     *   <li>其余：不可见。</li>
     * </ul>
     * <p>不可见时抛 404 而不是 403——403 等于告诉对方"这条记录确实存在"，
     * 仍然可以被用来枚举 id 区间。</p>
     */
    private boolean canViewRequest(Viewer v, PurchaseRequestEntity r) {
        if (v.admin()) return true;
        if (Objects.equals(v.id(), r.getApplicantId())) return true;
        if (v.has("MANAGER") && v.departmentId() != null
                && Objects.equals(v.departmentId(), r.getDepartmentId())) return true;
        return v.has("PURCHASER") && ORDERABLE_STATUSES.contains(r.getStatus());
    }

    private boolean canViewOrder(Viewer v, PurchaseOrderEntity o) {
        if (v.admin() || v.has("PURCHASER") || v.has("WAREHOUSE")) return true;
        PurchaseRequestEntity r = requests.selectById(o.getRequestId());
        if (r == null) return false;
        if (Objects.equals(v.id(), r.getApplicantId())) return true;
        return v.has("MANAGER") && v.departmentId() != null
                && Objects.equals(v.departmentId(), r.getDepartmentId());
    }

    // ------------------------------------------------------------------ 状态机

    /**
     * 申请单状态流转：先查合法迁移表，再执行带版本号的 CAS。
     *
     * <p>两层各管一件事。</p>
     * <ul>
     *   <li>第一层 {@code canGoTo} 拦的是<b>写错的代码</b>——比如把 PENDING 直接改成 COMPLETED，
     *       这种错误跟并发无关，应该在开发期就炸掉；</li>
     *   <li>第二层 {@code WHERE status=? AND version=?} 拦的是<b>并发</b>——两个请求都读了
     *       {@code version=2}，只有一个 UPDATE 能命中，另一个影响行数为 0，
     *       于是同一张单不会被审批两次、入库两次。</li>
     * </ul>
     */
    private void moveRequest(Long id, ApplicationStatus from, ApplicationStatus to,
                             Integer version, String conflictMessage) {
        if (!from.canGoTo(to)) {
            throw new IllegalStateException("非法状态流转: " + from + " → " + to);
        }
        if (requests.transition(id, from.name(), to.name(), version) != 1) {
            throw new IllegalStateException(conflictMessage);
        }
    }

    private void moveOrder(Long id, OrderStatus from, OrderStatus to, Integer version, String conflictMessage) {
        if (!from.canGoTo(to)) {
            throw new IllegalStateException("非法状态流转: " + from + " → " + to);
        }
        if (orders.transition(id, from.name(), to.name(), version) != 1) {
            throw new IllegalStateException(conflictMessage);
        }
    }

    // ------------------------------------------------------------------ 查询

    private PurchaseRequestEntity requireRequest(Long id) {
        PurchaseRequestEntity r = requests.selectById(id);
        if (r == null) throw new NoSuchElementException("采购申请不存在");
        return r;
    }

    private List<PurchaseRequestItemEntity> items(Long requestId) {
        return requestItems.byRequest(requestId);
    }

    public PurchaseRequestEntity get(Long id, String username) {
        PurchaseRequestEntity r = requireRequest(id);
        if (!canViewRequest(viewer(username), r)) throw new NoSuchElementException("采购申请不存在");
        return r;
    }

    public List<PurchaseRequestItemEntity> requestItems(Long id, String username) {
        get(id, username); // 复用可见性校验，防止"详情看不到、明细接口却能看到"
        return items(id);
    }

    public List<PurchaseRequestEntity> list(String username) {
        return requests.byApplicant(viewer(username).id());
    }

    /**
     * 待办审批列表。
     *
     * <p>改造前这里是 {@code .eq(dept != null, departmentId, dept)}：部门为 null 时
     * <b>条件整段不拼接</b>，于是返回全公司待审申请——一个"没填部门"的账号反而拿到了
     * 最大视野。正确语义是：没有部门就没有可审范围，返回空。</p>
     */
    public List<PurchaseRequestEntity> pending(String username) {
        Viewer v = viewer(username);
        var q = Wrappers.<PurchaseRequestEntity>lambdaQuery()
                .eq(PurchaseRequestEntity::getStatus, ApplicationStatus.PENDING.name());
        if (v.admin()) {
            return requests.selectList(q.orderByAsc(PurchaseRequestEntity::getCreatedAt));
        }
        if (v.departmentId() == null) {
            return List.of();
        }
        return requests.selectList(q.eq(PurchaseRequestEntity::getDepartmentId, v.departmentId())
                .orderByAsc(PurchaseRequestEntity::getCreatedAt));
    }

    public List<PurchaseOrderEntity> orderList(String username) {
        Viewer v = viewer(username);
        var all = orders.selectList(Wrappers.<PurchaseOrderEntity>lambdaQuery()
                .orderByDesc(PurchaseOrderEntity::getCreatedAt));
        if (v.admin() || v.has("PURCHASER") || v.has("WAREHOUSE")) return all;
        return all.stream().filter(o -> canViewOrder(v, o)).toList();
    }

    public PurchaseOrderEntity getOrder(Long id, String username) {
        PurchaseOrderEntity o = orders.selectById(id);
        if (o == null) throw new NoSuchElementException("订单不存在");
        if (!canViewOrder(viewer(username), o)) throw new NoSuchElementException("订单不存在");
        return o;
    }

    public List<InventoryStockEntity> stock() {
        return stock.selectList(null);
    }

    public List<InventoryTransactionEntity> transactions() {
        return transactions.recent();
    }

    // ------------------------------------------------------------------ 申请单

    @Transactional
    public PurchaseRequestEntity createDraft(String username, PurchaseRequestDtos.Create dto) {
        Viewer v = viewer(username);
        PurchaseRequestEntity r = new PurchaseRequestEntity();
        r.setRequestNo(documentNo.nextRequestNo());
        r.setApplicantId(v.id());
        r.setDepartmentId(v.departmentId());
        r.setReason(dto.reason());
        r.setStatus(ApplicationStatus.DRAFT.name());
        r.setVersion(0);
        r.setCreatedAt(LocalDateTime.now());
        r.setUpdatedAt(LocalDateTime.now());
        r.setTotalAmount(BigDecimal.ZERO);
        requests.insert(r);
        fillItems(r, dto.items());
        return r;
    }

    /**
     * 写明细。
     *
     * <p>先按 {@code productId} 归并：同一件商品填两行，合并成一行累加数量。
     * 改造前不归并，导致同一张单里出现两条 {@code product_id} 相同的明细，
     * 入库时第二条用的还是事务内第一次读到的旧 {@code version}，
     * CAS 必然失败 → 整单回滚 → 用户怎么重试都失败。</p>
     */
    private void fillItems(PurchaseRequestEntity r, List<PurchaseRequestDtos.Item> input) {
        Map<Long, Integer> quantityByProduct = new LinkedHashMap<>();
        Map<Long, BigDecimal> priceByProduct = new LinkedHashMap<>();
        for (PurchaseRequestDtos.Item in : input) {
            quantityByProduct.merge(in.productId(), in.quantity(), Integer::sum);
            priceByProduct.putIfAbsent(in.productId(), in.unitPrice());
        }

        BigDecimal total = BigDecimal.ZERO;
        for (var entry : quantityByProduct.entrySet()) {
            Long productId = entry.getKey();
            ProductEntity p = catalog.product(productId);
            if (p == null) throw new IllegalArgumentException("商品不存在: " + productId);

            BigDecimal unitPrice = priceByProduct.get(productId);
            BigDecimal amount = unitPrice.multiply(BigDecimal.valueOf(entry.getValue()));

            PurchaseRequestItemEntity i = new PurchaseRequestItemEntity();
            i.setRequestId(r.getId());
            i.setProductId(p.getId());
            i.setProductName(p.getName()); // 快照：商品改名后历史单据不受影响
            i.setQuantity(entry.getValue());
            i.setUnitPrice(unitPrice);
            i.setAmount(amount);
            requestItems.insert(i);
            total = total.add(amount);
        }
        r.setTotalAmount(total);
        requests.updateById(r);
    }

    @Transactional
    public PurchaseRequestEntity updateDraft(Long id, String username, PurchaseRequestDtos.Update dto) {
        PurchaseRequestEntity r = requireRequest(id);
        if (!r.getApplicantId().equals(viewer(username).id())
                || !ApplicationStatus.DRAFT.name().equals(r.getStatus())) {
            throw new IllegalStateException("仅草稿本人可编辑");
        }
        requestItems.deleteByRequest(id);
        r.setReason(dto.reason());
        r.setUpdatedAt(LocalDateTime.now());
        fillItems(r, dto.items());
        return r;
    }

    @Transactional
    public void deleteDraft(Long id, String username) {
        PurchaseRequestEntity r = requireRequest(id);
        if (!r.getApplicantId().equals(viewer(username).id())
                || !ApplicationStatus.DRAFT.name().equals(r.getStatus())) {
            throw new IllegalStateException("仅草稿本人可删除");
        }
        requestItems.deleteByRequest(id);
        requests.deleteById(id);
    }

    @Transactional
    public PurchaseRequestEntity submit(Long id, String username) {
        PurchaseRequestEntity r = requireRequest(id);
        if (!r.getApplicantId().equals(viewer(username).id())) throw new IllegalStateException("无权提交");
        moveRequest(id, ApplicationStatus.DRAFT, ApplicationStatus.PENDING, r.getVersion(), "申请状态已变更");
        return requireRequest(id);
    }

    @Transactional
    public PurchaseRequestEntity withdraw(Long id, String username) {
        PurchaseRequestEntity r = requireRequest(id);
        if (!r.getApplicantId().equals(viewer(username).id())) throw new IllegalStateException("无权撤回");
        moveRequest(id, ApplicationStatus.PENDING, ApplicationStatus.DRAFT, r.getVersion(), "仅待审批申请可撤回");
        return requireRequest(id);
    }

    // ------------------------------------------------------------------ 审批

    /**
     * 审批。
     *
     * <p>三条断言，缺一条都能造成实质越权：</p>
     * <ol>
     *   <li><b>不能审批自己提交的申请</b>——原实现没有这条，一个有 MANAGER 角色的人
     *       可以自己提交自己批准，整个审批流程形同虚设；</li>
     *   <li><b>必须同部门，且双方部门都不为 null</b>——原实现用
     *       {@code Objects.equals(r.dept, u.dept)}，两个 null 会被判为"相等"从而放行；</li>
     *   <li><b>当前必须是 PENDING</b>——由状态机 + CAS 保证，重复审批会因影响行数为 0 被拒。</li>
     * </ol>
     */
    @Transactional
    public PurchaseRequestEntity approve(Long id, String username, PurchaseRequestDtos.Approval dto) {
        PurchaseRequestEntity r = requireRequest(id);
        Viewer v = viewer(username);

        if (Objects.equals(r.getApplicantId(), v.id())) {
            throw new IllegalStateException("不能审批本人提交的申请");
        }
        if (!v.admin()) {
            if (v.departmentId() == null || r.getDepartmentId() == null
                    || !Objects.equals(v.departmentId(), r.getDepartmentId())) {
                throw new IllegalStateException("仅本部门负责人可审批");
            }
        }

        String action = dto.action().toUpperCase(Locale.ROOT);
        if (!Set.of("APPROVE", "REJECT").contains(action)) {
            throw new IllegalArgumentException("action 必须为 APPROVE 或 REJECT");
        }
        ApplicationStatus target = "APPROVE".equals(action)
                ? ApplicationStatus.APPROVED
                : ApplicationStatus.REJECTED;
        moveRequest(id, ApplicationStatus.PENDING, target, r.getVersion(), "申请状态已变更");

        ApprovalRecordEntity record = new ApprovalRecordEntity();
        record.setRequestId(id);
        record.setApproverId(v.id());
        record.setAction(action);
        record.setComment(dto.comment());
        record.setCreatedAt(LocalDateTime.now());
        approvals.insert(record);
        return requireRequest(id);
    }

    // ------------------------------------------------------------------ 订单与库存

    /**
     * 由已批准的申请生成订单。
     *
     * <p>重复下单有三道防线：</p>
     * <ol>
     *   <li>{@code status == APPROVED} 的前置判断（挡住明显的手快连点）；</li>
     *   <li>APPROVED → PURCHASING 的 CAS，两个并发请求只有一个能改成功，
     *       另一个抛错并整笔回滚（订单行一起消失）；</li>
     *   <li>{@code purchase_order.request_id} 上的唯一键，防止绕过 service 的写入。</li>
     * </ol>
     */
    @Transactional
    public PurchaseOrderEntity createOrder(Long requestId, Long supplierId) {
        PurchaseRequestEntity r = requireRequest(requestId);
        if (!ApplicationStatus.APPROVED.name().equals(r.getStatus())) {
            throw new IllegalStateException("申请未审批通过");
        }
        // catalog.supplier 带 active=1 条件：已停用的供应商不能再下单，提示要能区分"没这个供应商"
        if (catalog.supplier(supplierId) == null) throw new IllegalArgumentException("供应商不存在或已停用");

        PurchaseOrderEntity o = new PurchaseOrderEntity();
        o.setOrderNo(documentNo.nextOrderNo());
        o.setRequestId(requestId);
        o.setSupplierId(supplierId);
        o.setTotalAmount(r.getTotalAmount());
        o.setStatus(OrderStatus.CREATED.name());
        o.setVersion(0);
        o.setCreatedAt(LocalDateTime.now());
        o.setUpdatedAt(LocalDateTime.now());
        try {
            orders.insert(o);
        } catch (DuplicateKeyException e) {
            // 并发下单时，第二个事务会卡在 uk_order_request 上，等第一个提交后拿到重复键错误。
            // 这属于"业务上不该重复"的正常竞态结果，翻译成 4xx 而不是让 500 冒出来。
            throw new IllegalStateException("该采购申请已生成订单，请勿重复下单");
        }

        for (PurchaseRequestItemEntity ri : items(requestId)) {
            PurchaseOrderItemEntity oi = new PurchaseOrderItemEntity();
            oi.setOrderId(o.getId());
            oi.setProductId(ri.getProductId());
            oi.setProductName(ri.getProductName());
            oi.setQuantity(ri.getQuantity());
            oi.setUnitPrice(ri.getUnitPrice());
            oi.setAmount(ri.getAmount());
            orderItems.insert(oi);
        }
        moveRequest(requestId, ApplicationStatus.APPROVED, ApplicationStatus.PURCHASING,
                r.getVersion(), "申请状态已变更");
        return o;
    }

    /**
     * 确认收货入库。
     *
     * <p>三个改造点：</p>
     * <ol>
     *   <li><b>状态流转仍用 CAS</b>，重复提交第二次必然被拒；</li>
     *   <li><b>库存改成原子 upsert</b>（{@code INSERT ... ON DUPLICATE KEY UPDATE quantity = quantity + ?}），
     *       顺带修掉"首次入库并发插入主键冲突"和"同单重复商品用旧 version 更新必失败"两个问题；</li>
     *   <li><b>明细先按商品归并</b>再入库，保证每个商品只产生一条流水，
     *       与 {@code uk_inventory_tx_ref} 唯一键保持一致。</li>
     * </ol>
     */
    @Transactional
    public PurchaseOrderEntity delivered(Long id, Long operatorId) {
        PurchaseOrderEntity o = orders.selectById(id);
        if (o == null) throw new NoSuchElementException("订单不存在");

        moveOrder(id, OrderStatus.CREATED, OrderStatus.DELIVERED, o.getVersion(), "订单已处理");

        Map<Long, Integer> quantityByProduct = new LinkedHashMap<>();
        for (PurchaseOrderItemEntity i : orderItems.byOrder(id)) {
            quantityByProduct.merge(i.getProductId(), i.getQuantity(), Integer::sum);
        }

        for (var entry : quantityByProduct.entrySet()) {
            Long productId = entry.getKey();
            Integer quantity = entry.getValue();

            // 一条语句完成"没有就建、有就累加"，不再需要先 select 再判断
            stock.upsertAndAdd(productId, quantity);

            InventoryTransactionEntity tx = new InventoryTransactionEntity();
            tx.setProductId(productId);
            tx.setQuantity(quantity);
            tx.setType("IN");
            tx.setReferenceNo(o.getOrderNo());
            tx.setOperatorId(operatorId);
            tx.setCreatedAt(LocalDateTime.now());
            transactions.insert(tx);
        }
        return orders.selectById(id);
    }
}
