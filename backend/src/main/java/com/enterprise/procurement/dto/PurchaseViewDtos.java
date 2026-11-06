package com.enterprise.procurement.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 主流程（申请 / 审批 / 订单）对外的读模型。
 *
 * <p>为什么不再直接返回实体：实体是"表结构镜像"，字段是 {@code applicantId}、
 * {@code totalAmount}、{@code createdAt}；页面要的是"张三"、"技术部"、"¥45,800"、
 * "2026-10-29 21:03"。这一层错位以前是靠前端猜字段名来桥接的，猜错了就整列空白，
 * 而且不会有任何报错——最难发现的一类 bug。</p>
 *
 * <p>现在由服务端把"展示态"定死在这里：字段名与前端表格的 {@code prop} 完全同名，
 * 前端不再做任何字段改名，接口返回什么就直接渲染什么。</p>
 */
public final class PurchaseViewDtos {

    private PurchaseViewDtos() {
    }

    /** 列表行。不含明细，列表页也用不到。 */
    public record RequestRow(Long id,
                             String requestNo,
                             String reason,
                             String applicant,
                             String department,
                             BigDecimal amount,
                             String status,
                             LocalDateTime createTime) {
    }

    /** 明细行。{@code productName} 是下单时的快照，商品改名后历史单据不受影响。 */
    public record ItemRow(Long id,
                          Long productId,
                          String productName,
                          Integer quantity,
                          BigDecimal unitPrice,
                          BigDecimal amount) {
    }

    /**
     * 详情。
     *
     * <p>包含明细，所以"详情弹窗里看不到商品"这个症状不需要前端再发一次请求——
     * 详情页一次性拿到渲染所需的全部数据。</p>
     */
    public record RequestDetail(Long id,
                                String requestNo,
                                String reason,
                                String applicant,
                                String department,
                                BigDecimal amount,
                                String status,
                                LocalDateTime createTime,
                                List<ItemRow> items) {
    }

    /** 订单行。{@code requestNo} 让页面能显示"关联申请"，不必再点查一次。 */
    public record OrderRow(Long id,
                           String orderNo,
                           Long requestId,
                           String requestNo,
                           String reason,
                           String supplier,
                           BigDecimal amount,
                           String status,
                           LocalDateTime createTime) {
    }

    /**
     * 订单详情。
     *
     * <p>改造前订单详情只返回行数据，<b>没有明细</b>：采购员点开一张单，看不到
     * 到底买了哪几样、各多少件。明细一直存在 {@code purchase_order_item} 里，
     * 只是没有接口读它。</p>
     *
     * <p>字段是 {@link OrderRow} 的超集，前端原有取值（orderNo/supplier/amount…）
     * 一个都不用改，只是多了一个 {@code items} 可以用。</p>
     */
    public record OrderDetail(Long id,
                              String orderNo,
                              Long requestId,
                              String requestNo,
                              String reason,
                              String supplier,
                              BigDecimal amount,
                              String status,
                              LocalDateTime createTime,
                              List<ItemRow> items) {
    }

    /**
     * 审批留痕。
     *
     * @param action APPROVE / REJECT —— 前端据此渲染绿色通过 / 红色驳回
     */
    public record ApprovalRow(Long id,
                              String approver,
                              String action,
                              String comment,
                              LocalDateTime createTime) {
    }
}
