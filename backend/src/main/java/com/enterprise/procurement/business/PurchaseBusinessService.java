package com.enterprise.procurement.business;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.enterprise.procurement.dto.PageResult;
import com.enterprise.procurement.dto.PurchaseRequestDtos;
import com.enterprise.procurement.dto.PurchaseViewDtos;
import com.enterprise.procurement.exception.ErrorCode;
import com.enterprise.procurement.exception.ForbiddenException;
import com.enterprise.procurement.exception.StateConflictException;
import com.enterprise.procurement.model.Enums.ApplicationStatus;
import com.enterprise.procurement.model.Enums.OrderStatus;
import com.enterprise.procurement.persistence.entity.*;
import com.enterprise.procurement.persistence.mapper.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

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
    private final IdempotencyService idempotency;

    public PurchaseBusinessService(PurchaseRequestMapper r, PurchaseRequestItemMapper ri, ApprovalRecordMapper a,
                                   PurchaseOrderMapper o, PurchaseOrderItemMapper oi, InventoryStockMapper s,
                                   InventoryTransactionMapper t, CatalogMapper c, DocumentNoGenerator documentNo,
                                   IdempotencyService idempotency) {
        this.requests = r;
        this.requestItems = ri;
        this.approvals = a;
        this.orders = o;
        this.orderItems = oi;
        this.stock = s;
        this.transactions = t;
        this.catalog = c;
        this.documentNo = documentNo;
        this.idempotency = idempotency;
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
    private boolean canViewRequest(Viewer v, Long applicantId, Long departmentId, String status) {
        if (v.admin()) return true;
        if (Objects.equals(v.id(), applicantId)) return true;
        if (v.has("MANAGER") && v.departmentId() != null
                && Objects.equals(v.departmentId(), departmentId)) return true;
        return v.has("PURCHASER") && ORDERABLE_STATUSES.contains(status);
    }

    private boolean canViewRequest(Viewer v, PurchaseRequestEntity r) {
        return canViewRequest(v, r.getApplicantId(), r.getDepartmentId(), r.getStatus());
    }

    private boolean canViewOrder(Viewer v, PurchaseOrderEntity o) {
        if (v.admin() || v.has("PURCHASER") || v.has("WAREHOUSE")) return true;
        PurchaseRequestEntity r = requests.selectById(o.getRequestId());
        if (r == null) return false;
        return canViewRequest(v, r);
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
            // 竞态输家：抛 StateConflictException → 409，而不是 IllegalStateException → 400。
            // "被人抢先"和"参数写错"必须能用状态码区分开。
            throw new StateConflictException(conflictMessage);
        }
    }

    private void moveOrder(Long id, OrderStatus from, OrderStatus to, Integer version, String conflictMessage) {
        if (!from.canGoTo(to)) {
            throw new IllegalStateException("非法状态流转: " + from + " → " + to);
        }
        if (orders.transition(id, from.name(), to.name(), version) != 1) {
            throw new StateConflictException(conflictMessage);
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

    /**
     * 我的申请列表。
     *
     * <p>改造前只按 {@code applicantId} 查实体，前端发的 {@code keyword} / {@code status}
     * 被完全忽略——用户输入关键词点"查询"，结果毫无变化。现在两个条件都下推到 SQL，
     * 顺便把姓名/部门名用 JOIN 一次带出。</p>
     */
    public List<PurchaseViewDtos.RequestRow> list(String username, String keyword, String status) {
        return requests.rows(viewer(username).id(), blankToNull(keyword), blankToNull(status))
                .stream().map(PurchaseBusinessService::toRequestRow).toList();
    }

    /**
     * 申请详情（含明细）。
     *
     * <p>顺序很重要：先取展示行，再用它身上的 applicantId/departmentId 做归属判断，
     * 判不过就抛 404。这样既不为了鉴权多查一次实体，也不把"记录是否存在"泄露给越权方。</p>
     */
    public PurchaseViewDtos.RequestDetail detail(Long id, String username) {
        PurchaseRequestRow row = requests.rowById(id);
        if (row == null || !canViewRequest(viewer(username), row.getApplicantId(),
                row.getDepartmentId(), row.getStatus())) {
            throw new NoSuchElementException("采购申请不存在");
        }
        List<PurchaseViewDtos.ItemRow> detailItems = items(id).stream()
                .map(i -> new PurchaseViewDtos.ItemRow(i.getId(), i.getProductId(), i.getProductName(),
                        i.getQuantity(), i.getUnitPrice(), i.getAmount()))
                .toList();
        return new PurchaseViewDtos.RequestDetail(row.getId(), row.getRequestNo(), row.getReason(),
                row.getApplicant(), row.getDepartment(), row.getAmount(), row.getStatus(),
                row.getCreateTime(), detailItems);
    }

    /**
     * 待办审批列表。
     *
     * <p>改造前这里是 {@code .eq(dept != null, departmentId, dept)}：部门为 null 时
     * <b>条件整段不拼接</b>，于是返回全公司待审申请——一个"没填部门"的账号反而拿到了
     * 最大视野。正确语义是：没有部门就没有可审范围，返回空。</p>
     */
    public List<PurchaseViewDtos.RequestRow> pending(String username, String keyword) {
        Viewer v = viewer(username);
        String kw = blankToNull(keyword);
        if (v.admin()) {
            return requests.pendingRows(null, kw).stream()
                    .map(PurchaseBusinessService::toRequestRow).toList();
        }
        if (v.departmentId() == null) {
            return List.of();
        }
        return requests.pendingRows(v.departmentId(), kw).stream()
                .map(PurchaseBusinessService::toRequestRow).toList();
    }

    /**
     * 订单列表。
     *
     * <p>改造前对非特权角色是"查全量再逐行点查申请单"的 N+1，一条订单一次查询。
     * 现在把归属条件下推到 SQL，一次扫描完成；供应商名称也由 JOIN 带出。</p>
     */
    /**
     * 订单分页。
     *
     * <p>改造前这个列表既没有分页也没有过滤，一次返回全表。现在 page/size 由分页插件
     * 翻译成 LIMIT，{@code total} 是真实总数；{@code size} 夹在 1–100，
     * 防止 {@code ?size=999999} 把整表读进内存（插件层还有 200 的硬上限）。</p>
     *
     * <p>归属裁剪仍在 SQL 层：特权角色走 {@code pageAll}，其他人走 {@code pageFor}，
     * 不存在"先查全量再在内存里筛"的退化路径。</p>
     */
    public PageResult<PurchaseViewDtos.OrderRow> orderPage(String username, String status, String keyword,
                                                           Long supplierId, int page, int size) {
        Viewer v = viewer(username);
        int p = Math.max(1, page);
        int s = Math.min(Math.max(1, size), 100);
        String st = blankToNull(status);
        String kw = blankToNull(keyword);

        IPage<PurchaseOrderRow> result = (v.admin() || v.has("PURCHASER") || v.has("WAREHOUSE"))
                ? orders.pageAll(new Page<>(p, s), st, kw, supplierId)
                : orders.pageFor(new Page<>(p, s), v.id(), v.departmentId(), st, kw, supplierId);

        List<PurchaseViewDtos.OrderRow> rows = result.getRecords().stream()
                .map(PurchaseBusinessService::toOrderRow).toList();
        return PageResult.of(rows, result.getTotal(), result.getCurrent(), result.getSize());
    }

    /**
     * 订单详情（含明细）。
     *
     * <p>改造前只有行数据，采购员看不到买了什么。明细本来就在
     * {@code purchase_order_item} 里，缺的只是一个读它的接口。</p>
     */
    public PurchaseViewDtos.OrderDetail orderDetail(Long id, String username) {
        PurchaseOrderEntity o = getOrder(id, username); // 归属判断，越权即 404
        PurchaseOrderRow row = orders.rowById(o.getId());
        List<PurchaseViewDtos.ItemRow> detailItems = orderItems.byOrder(id).stream()
                .map(i -> new PurchaseViewDtos.ItemRow(i.getId(), i.getProductId(), i.getProductName(),
                        i.getQuantity(), i.getUnitPrice(), i.getAmount()))
                .toList();

        if (row == null) {
            // JOIN 落空（申请单被物理删除等极端情况）：退回实体字段，保证详情不返回 null
            return new PurchaseViewDtos.OrderDetail(o.getId(), o.getOrderNo(), o.getRequestId(), null, null,
                    null, o.getTotalAmount(), o.getStatus(), o.getCreatedAt(), detailItems);
        }
        return new PurchaseViewDtos.OrderDetail(row.getId(), row.getOrderNo(), row.getRequestId(),
                row.getRequestNo(), row.getReason(), row.getSupplier(), row.getAmount(), row.getStatus(),
                row.getCreateTime(), detailItems);
    }

    /**
     * 审批留痕。
     *
     * <p>复用申请详情的可见性判断：能看到这张单的人，才能看到它是谁批的。
     * 改造前 {@code approval_record} 只写不读，页面上"谁批的、批注是什么"完全不可见，
     * 出了问题只能翻数据库。</p>
     */
    public List<PurchaseViewDtos.ApprovalRow> approvalHistory(Long id, String username) {
        get(id, username); // 不存在/无权 → 404，不额外暴露记录是否存在
        return approvals.rowsByRequest(id).stream()
                .map(a -> new PurchaseViewDtos.ApprovalRow(a.getId(), a.getApprover(),
                        a.getAction(), a.getComment(), a.getCreateTime()))
                .toList();
    }

    /**
     * 库存流水分页。
     *
     * <p>改造前 {@code SELECT * ... ORDER BY created_at DESC} 无 LIMIT，
     * 流水是只增表，跑久了必然把整表读进内存。日期的 {@code to} 按"含当天"处理
     * （SQL 里用 {@code < to + 1 天}），否则用户选 11-01 到 11-01 会查不到任何数据。</p>
     */
    public PageResult<InventoryTransactionRow> transactionPage(Long productId, String type,
                                                               LocalDate from, LocalDate to,
                                                               int page, int size) {
        int p = Math.max(1, page);
        int s = Math.min(Math.max(1, size), 100);
        IPage<InventoryTransactionRow> result = transactions.pageRows(new Page<>(p, s),
                productId, blankToNull(type), from, to);
        return PageResult.of(result.getRecords(), result.getTotal(), result.getCurrent(), result.getSize());
    }

    /** 可下单的申请：审批通过且尚未生成订单。采购下单页的下拉数据源。 */
    public List<PurchaseViewDtos.RequestRow> orderableRequests() {
        return requests.orderableRows().stream().map(PurchaseBusinessService::toRequestRow).toList();
    }

    private static PurchaseViewDtos.RequestRow toRequestRow(PurchaseRequestRow r) {
        return new PurchaseViewDtos.RequestRow(r.getId(), r.getRequestNo(), r.getReason(),
                r.getApplicant(), r.getDepartment(), r.getAmount(), r.getStatus(), r.getCreateTime());
    }

    private static PurchaseViewDtos.OrderRow toOrderRow(PurchaseOrderRow r) {
        return new PurchaseViewDtos.OrderRow(r.getId(), r.getOrderNo(), r.getRequestId(), r.getRequestNo(),
                r.getReason(), r.getSupplier(), r.getAmount(), r.getStatus(), r.getCreateTime());
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private PurchaseOrderEntity getOrder(Long id, String username) {
        PurchaseOrderEntity o = orders.selectById(id);
        if (o == null) throw new NoSuchElementException("订单不存在");
        if (!canViewOrder(viewer(username), o)) throw new NoSuchElementException("订单不存在");
        return o;
    }

    public List<InventoryStockRow> stock() {
        return stock.stockRows();
    }

    /**
     * 手工调整库存：正数入库、负数出库。
     *
     * <p>为什么必须走这里而不是直接改 inventory_stock：库存变化必须留流水，
     * 否则对账时"这个商品的 37 件是怎么来的"永远查不清。入库只加不减，
     * 用 upsert 一条语句搞定；出库要先判断够不够，把判断和扣减压进同一条
     * 带 {@code quantity >= n} 条件的 UPDATE，避免并发下把库存扣成负数。</p>
     *
     * @param quantity 正数入库、负数出库，0 无意义直接拒绝
     */
    @Transactional
    public void adjustStock(Long productId, Integer quantity, Long operatorId) {
        adjustStock(productId, quantity, operatorId, null);
    }

    /**
     * 带幂等键的库存调整。
     *
     * <p>这是四个并发场景里<b>唯一没有任何天然唯一键保护</b>的写操作：每调一次都会
     * 生成新的 ST 单号，{@code uk_inventory_tx_ref} 拦不住第二次。网络重试或连点两次
     * 就会重复加减库存，所以必须靠客户端幂等键兜住。</p>
     */
    @Transactional
    public void adjustStock(Long productId, Integer quantity, Long operatorId, String idemKey) {
        if (quantity == null || quantity == 0) {
            throw new IllegalArgumentException("调整数量不能为 0");
        }
        if (catalog.product(productId) == null) {
            throw new IllegalArgumentException("商品不存在或已停用");
        }

        var claim = idempotency.claim(idemKey, operatorId, "ADJUST_STOCK",
                IdempotencyService.fingerprint(productId, quantity));
        if (claim.replay()) {
            // 上次已经成功执行过：再执行一次就是重复加/减库存，直接返回成功
            return;
        }

        if (quantity > 0) {
            stock.upsertAndAdd(productId, quantity);
        } else if (stock.deduct(productId, -quantity) == 0) {
            // 并发下抢输了或本来就不够——都是"当前状态不允许"，409 而不是 400
            throw new StateConflictException(ErrorCode.STOCK_INSUFFICIENT, "库存不足，无法出库");
        }

        InventoryTransactionEntity tx = new InventoryTransactionEntity();
        tx.setProductId(productId);
        tx.setQuantity(Math.abs(quantity));
        tx.setType(quantity > 0 ? "IN" : "OUT");
        tx.setReferenceNo(documentNo.nextStockNo());
        tx.setOperatorId(operatorId);
        tx.setCreatedAt(LocalDateTime.now());
        transactions.insert(tx);

        idempotency.complete(idemKey, operatorId, null);
    }

    // ------------------------------------------------------------------ 申请单

    @Transactional
    public PurchaseRequestEntity createDraft(String username, PurchaseRequestDtos.Create dto) {
        return createDraft(username, dto, null);
    }

    /**
     * 带幂等键的建单。
     *
     * <p>创建申请是唯一"同一个用户合法地可以提交两张相同单据"的写操作，
     * 因此既没有天然唯一键，也无法靠状态机区分"重试"与"真的想再建一张"。
     * 只有客户端知道这次点击是不是上一次的重发，所以幂等键必须由客户端提供。</p>
     *
     * <p>命中重放时返回<b>上次创建的那张单</b>，而不是报错——对用户来说，
     * "我已经提交过了"就应该看到那张单，而不是一条红色提示。</p>
     */
    @Transactional
    public PurchaseRequestEntity createDraft(String username, PurchaseRequestDtos.Create dto, String idemKey) {
        Viewer v = viewer(username);
        var claim = idempotency.claim(idemKey, v.id(), "CREATE_REQUEST", fingerprintOf(dto));
        if (claim.replay()) {
            PurchaseRequestEntity existing = claim.resultId() == null ? null : requests.selectById(claim.resultId());
            if (existing == null) {
                throw new StateConflictException("该请求已处理，但对应申请单已不存在，请刷新后重试");
            }
            return existing;
        }

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
        BigDecimal total = fillItems(r.getId(), dto.items());
        // 新插入的行在同一事务里，条件必然命中；仍检查行数是防御将来有人改这段代码
        if (requests.updateDraftFields(r.getId(), v.id(), dto.reason(), total) != 1) {
            throw new StateConflictException("草稿创建失败，请重试");
        }
        r.setTotalAmount(total);

        if (Boolean.TRUE.equals(dto.submit())) {
            // 建单与提交在同一个事务里完成。
            // 改造前前端是"POST 建草稿 → POST 提交"两跳，第二跳失败（网络断开/服务重启）
            // 就会留下一张"用户以为已提交、实际还是草稿"的孤儿单，而且用户不会知道。
            // 现在要么全成、要么全不成，重试时幂等键还能把已建的单原样还回来。
            moveRequest(r.getId(), ApplicationStatus.DRAFT, ApplicationStatus.PENDING,
                    r.getVersion(), "申请状态已变更");
            idempotency.complete(idemKey, v.id(), r.getId());
            return requireRequest(r.getId());
        }

        idempotency.complete(idemKey, v.id(), r.getId());
        return r;
    }

    /** 请求指纹：事由 + 按商品号排序后的明细，明细顺序不同不算新请求。 */
    private static String fingerprintOf(PurchaseRequestDtos.Create dto) {
        String items = dto.items().stream()
                .sorted(Comparator.comparing(PurchaseRequestDtos.Item::productId)
                        .thenComparing(PurchaseRequestDtos.Item::quantity))
                .map(i -> i.productId() + ":" + i.quantity() + ":" + i.unitPrice().stripTrailingZeros().toPlainString())
                .collect(Collectors.joining(","));
        return IdempotencyService.fingerprint(dto.reason(), items);
    }

    /**
     * 写明细。
     *
     * <p>先按 {@code productId} 归并：同一件商品填两行，合并成一行累加数量。
     * 改造前不归并，导致同一张单里出现两条 {@code product_id} 相同的明细，
     * 入库时第二条用的还是事务内第一次读到的旧 {@code version}，
     * CAS 必然失败 → 整单回滚 → 用户怎么重试都失败。</p>
     *
     * <p>只写明细、算总额，<b>不再回写主表</b>——主表更新统一走
     * {@link PurchaseRequestMapper#updateDraftFields} 的条件语句，
     * 杜绝 updateById 全字段覆盖绕过状态条件的老路径。</p>
     */
    private BigDecimal fillItems(Long requestId, List<PurchaseRequestDtos.Item> input) {
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
            i.setRequestId(requestId);
            i.setProductId(p.getId());
            i.setProductName(p.getName()); // 快照：商品改名后历史单据不受影响
            i.setQuantity(entry.getValue());
            i.setUnitPrice(unitPrice);
            i.setAmount(amount);
            requestItems.insert(i);
            total = total.add(amount);
        }
        return total;
    }

    @Transactional
    public PurchaseRequestEntity updateDraft(Long id, String username, PurchaseRequestDtos.Update dto) {
        Viewer v = viewer(username);
        PurchaseRequestEntity r = requireRequest(id);
        if (!Objects.equals(r.getApplicantId(), v.id())) throw new ForbiddenException("仅草稿本人可编辑");
        if (!ApplicationStatus.DRAFT.name().equals(r.getStatus())) {
            // 状态不对属于"冲突"而不是"参数错"：用户点的是同一张单，只是它已经进了审批流程。
            // 改造前这里抛 IllegalStateException → 400，和"事由填错"同码，前端无法提示"刷新"。
            throw new StateConflictException("申请已进入审批流程，本次修改未生效");
        }

        requestItems.deleteByRequest(id);
        BigDecimal total = fillItems(id, dto.items());
        // 竞态闭合点：读判之后单据若被并发提交（DRAFT→PENDING），这里影响行数为 0。
        // 改造前是 updateById 全字段覆盖——status/version 会被实体里的旧值写回去，
        // 等于把"已提交"悄悄改回"草稿"，后续审批全部建立在被污染的版本号上。
        if (requests.updateDraftFields(id, v.id(), dto.reason(), total) != 1) {
            throw new StateConflictException("申请已进入审批流程，本次修改未生效");
        }
        r.setReason(dto.reason());
        r.setTotalAmount(total);
        return r;
    }

    @Transactional
    public void deleteDraft(Long id, String username) {
        Viewer v = viewer(username);
        PurchaseRequestEntity r = requireRequest(id);
        if (!Objects.equals(r.getApplicantId(), v.id())) throw new ForbiddenException("仅草稿本人可删除");
        // 条件删除：只有仍是 DRAFT 才删得动；明细/审批记录靠 ON DELETE CASCADE 一并清理。
        // 改造前先读后判再无条件 DELETE——读与删之间单据被提交，仍会被连审批记录一起删掉。
        if (requests.deleteDraftRow(id, v.id()) != 1) {
            throw new StateConflictException("申请已进入审批流程，无法删除");
        }
    }

    @Transactional
    public PurchaseRequestEntity submit(Long id, String username) {
        PurchaseRequestEntity r = requireRequest(id);
        Viewer v = viewer(username);
        if (!Objects.equals(r.getApplicantId(), v.id())) throw new ForbiddenException("无权提交");
        if (ApplicationStatus.PENDING.name().equals(r.getStatus())) {
            // 幂等成功：只有提交能把 DRAFT 变成 PENDING，所以"已是 PENDING 且是本人"
            // 只可能是上一次提交已经成功（网络超时重试 / 连点两次）。
            // 这时报 409 会让用户以为失败，正确做法是原样返回当前单据。
            return r;
        }
        moveRequest(id, ApplicationStatus.DRAFT, ApplicationStatus.PENDING, r.getVersion(), "申请状态已变更");
        return requireRequest(id);
    }

    @Transactional
    public PurchaseRequestEntity withdraw(Long id, String username) {
        PurchaseRequestEntity r = requireRequest(id);
        if (!r.getApplicantId().equals(viewer(username).id())) throw new ForbiddenException("无权撤回");
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
            throw new ForbiddenException("不能审批本人提交的申请");
        }
        if (!v.admin()) {
            if (v.departmentId() == null || r.getDepartmentId() == null
                    || !Objects.equals(v.departmentId(), r.getDepartmentId())) {
                throw new ForbiddenException("仅本部门负责人可审批");
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
            // 区分两种"下不了单"：已经下过单（PURCHASING/COMPLETED）给 REQUEST_ALREADY_ORDERED，
            // 其余（草稿/待审/驳回）给通用 STATE_CONFLICT。
            // 改造前一律 throw IllegalStateException("申请未审批通过") → 400，
            // 既分不清状态，也让唯一键兜底那条分支永远走不到（前置检查先命中）。
            boolean alreadyOrdered = ApplicationStatus.PURCHASING.name().equals(r.getStatus())
                    || ApplicationStatus.COMPLETED.name().equals(r.getStatus());
            if (alreadyOrdered) {
                throw new StateConflictException(ErrorCode.REQUEST_ALREADY_ORDERED, "该采购申请已生成订单，请勿重复下单");
            }
            throw new StateConflictException("申请未审批通过，无法下单");
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
            // 这属于"业务上不该重复"的正常竞态结果，翻译成 409 而不是让 500 冒出来。
            throw new StateConflictException(ErrorCode.REQUEST_ALREADY_ORDERED, "该采购申请已生成订单，请勿重复下单");
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
     *   <li><b>重复入库先给人话</b>：订单已是 DELIVERED 时直接 409"订单已入库"，
     *       而不是让状态机抛"非法状态流转: DELIVERED → DELIVERED"这种内部术语；</li>
     *   <li><b>库存改成原子 upsert</b>（{@code INSERT ... ON DUPLICATE KEY UPDATE quantity = quantity + ?}），
     *       顺带修掉"首次入库并发插入主键冲突"和"同单重复商品用旧 version 更新必失败"两个问题；</li>
     *   <li><b>明细先按商品归并、再按商品号升序入库</b>：保证每个商品只产生一条流水，
     *       与 {@code uk_inventory_tx_ref} 唯一键保持一致；同时所有事务以相同顺序获取
     *       库存行锁——改造前按明细插入顺序入库，两张订单的商品集合交叉时会互相持有
     *       对方想要的行锁，InnoDB 检测到死锁（ERROR 1213）回滚一方，接口表现成偶发 500。</li>
     * </ol>
     */
    @Transactional
    public PurchaseOrderEntity delivered(Long id, Long operatorId) {
        PurchaseOrderEntity o = orders.selectById(id);
        if (o == null) throw new NoSuchElementException("订单不存在");
        if (OrderStatus.DELIVERED.name().equals(o.getStatus())) {
            throw new StateConflictException(ErrorCode.ORDER_ALREADY_DELIVERED, "订单已入库，请勿重复操作");
        }

        moveOrder(id, OrderStatus.CREATED, OrderStatus.DELIVERED, o.getVersion(), "订单已被他人处理，请刷新后重试");

        // TreeMap：按 productId 升序遍历，统一所有事务的加锁顺序
        Map<Long, Integer> quantityByProduct = new TreeMap<>();
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

        // 订单入库完成，申请单也要走到终态。
        // 改造前这里断了一环：订单变 DELIVERED，申请却永远停在 PURCHASING，
        // 于是"已完成"这个状态从来不会出现，工作台的完成率恒为 0。
        // 顺序放在最后：库存与流水已经写好，这里失败会整笔回滚，不会留下"货入库了单没结"的半成品。
        PurchaseRequestEntity r = requests.selectById(o.getRequestId());
        if (r != null && ApplicationStatus.PURCHASING.name().equals(r.getStatus())) {
            moveRequest(r.getId(), ApplicationStatus.PURCHASING, ApplicationStatus.COMPLETED,
                    r.getVersion(), "申请状态已变更");
        }
        return orders.selectById(id);
    }
}
