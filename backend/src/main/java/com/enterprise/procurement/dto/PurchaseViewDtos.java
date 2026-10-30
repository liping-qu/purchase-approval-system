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
}
