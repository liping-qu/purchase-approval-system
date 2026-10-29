package com.enterprise.procurement.business;

import com.enterprise.procurement.dto.DashboardDtos;
import com.enterprise.procurement.persistence.entity.StatusCountRow;
import com.enterprise.procurement.persistence.mapper.DashboardMapper;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 工作台统计服务。
 *
 * <p>只做一件事：把 4 条聚合 SQL 和 1 条 top-N 查询组装成一个视图对象。
 * 不含业务写操作，因此不需要 {@code @Transactional}——单个 SELECT 在自动提交下即可，
 * 但要注意多次查询之间不是同一快照，极端并发下"总数"和"各状态数"可能差 1，
 * 这在本场景（工作台展示）是可接受的，如果将来要求强一致再合并为一条 UNION 查询。</p>
 */
@Service
public class DashboardService {

    private static final String PENDING = "PENDING";
    private static final String APPROVED = "APPROVED";
    private static final String REJECTED = "REJECTED";
    private static final String PURCHASING = "PURCHASING";
    private static final String COMPLETED = "COMPLETED";

    private final DashboardMapper mapper;

    public DashboardService(DashboardMapper mapper) {
        this.mapper = mapper;
    }

    public DashboardDtos.Summary summary() {
        Map<String, Long> byStatus = new HashMap<>();
        long total = 0L;
        for (StatusCountRow row : mapper.statusCounts()) {
            if (row.getStatus() == null) {
                continue;
            }
            long count = row.getTotal() == null ? 0L : row.getTotal();
            byStatus.merge(row.getStatus(), count, Long::sum);
            total += count;
        }

        List<DashboardDtos.RecentRequest> recent = mapper.recentRequests().stream()
                .map(row -> new DashboardDtos.RecentRequest(
                        row.getId(),
                        row.getRequestNo(),
                        row.getReason(),
                        row.getApplicant(),
                        row.getAmount(),
                        row.getStatus(),
                        row.getCreateTime()))
                .toList();

        return new DashboardDtos.Summary(
                total,
                byStatus.getOrDefault(PENDING, 0L),
                byStatus.getOrDefault(APPROVED, 0L),
                byStatus.getOrDefault(PURCHASING, 0L),
                byStatus.getOrDefault(COMPLETED, 0L),
                byStatus.getOrDefault(REJECTED, 0L),
                mapper.orderCount(),
                mapper.supplierCount(),
                mapper.productCount(),
                mapper.zeroStockCount(),
                recent);
    }
}
