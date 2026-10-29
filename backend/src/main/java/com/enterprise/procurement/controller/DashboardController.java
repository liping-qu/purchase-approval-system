package com.enterprise.procurement.controller;

import com.enterprise.procurement.business.DashboardService;
import com.enterprise.procurement.dto.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 工作台统计接口。
 *
 * <p>说明：本类原先依赖已删除的内存版 {@code ProcurementService}，统计口径是
 * "把内存 Map 的 size() 念出来"，重启即归零，且返回的键名（applicationCount / pendingCount）
 * 与前端读取的键名（totalRequests / pendingRequests）完全对不上。现在改为真实 SQL 聚合，
 * 并且键名与 {@code views/Dashboard.vue} 对齐。</p>
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/summary")
    public ApiResponse<?> summary() {
        return ApiResponse.ok(dashboardService.summary());
    }
}
