package com.enterprise.procurement.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 工作台聚合视图。
 *
 * <p>字段名与前端 {@code views/Dashboard.vue} 保持一一对应，避免"接口有数据、页面是空的"。
 * 这里刻意不使用实体对象直出，因为工作台是跨表聚合视图，不是某张表的镜像。</p>
 */
public final class DashboardDtos {

    private DashboardDtos() {
    }

    /**
     * @param totalRequests     采购申请总数
     * @param pendingRequests   待审批数量（PENDING）
     * @param approvedRequests  审批通过数量（APPROVED）
     * @param purchasingRequests 采购中数量（PURCHASING）
     * @param completedRequests 已完成数量（COMPLETED）
     * @param rejectedRequests  已驳回数量（REJECTED）
     * @param orderCount        采购订单总数
     * @param supplierCount     有效供应商数
     * @param productCount      有效商品数
     * @param zeroStockCount    零库存商品数（quantity &lt;= 0）
     * @param recent            最近 5 条采购申请
     */
    public record Summary(long totalRequests,
                          long pendingRequests,
                          long approvedRequests,
                          long purchasingRequests,
                          long completedRequests,
                          long rejectedRequests,
                          long orderCount,
                          long supplierCount,
                          long productCount,
                          long zeroStockCount,
                          List<RecentRequest> recent) {
    }

    public record RecentRequest(Long id,
                                String requestNo,
                                String reason,
                                String applicant,
                                BigDecimal amount,
                                String status,
                                LocalDateTime createTime) {
    }
}
