package com.enterprise.procurement.controller.v2;

import com.enterprise.procurement.aspect.OperationLog;
import com.enterprise.procurement.business.PurchaseBusinessService;
import com.enterprise.procurement.dto.ApiResponse;
import com.enterprise.procurement.dto.PurchaseRequestDtos;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

/**
 * 审批。
 *
 * <p>角色准入用 {@code @PreAuthorize} 做粗粒度过滤（不是 MANAGER/ADMIN 直接 403），
 * 部门、本人、状态这些细粒度规则在 service 里判——两层的分工要清楚：
 * 注解负责"这类人能不能进这个功能"，service 负责"这个人能不能动这条数据"。</p>
 */
@RestController
@RequestMapping("/api/approvals")
public class ApprovalController {

    private final PurchaseBusinessService service;

    public ApprovalController(PurchaseBusinessService service) {
        this.service = service;
    }

    @GetMapping("/pending")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ApiResponse<?> pending(@RequestParam(required = false) String keyword, Principal principal) {
        return ApiResponse.ok(service.pending(principal.getName(), keyword));
    }

    @PostMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @OperationLog(value = "审批采购申请", module = "审批")
    public ApiResponse<?> approve(@PathVariable Long id,
                                  @Valid @RequestBody PurchaseRequestDtos.Approval dto,
                                  Principal principal) {
        return ApiResponse.ok(service.approve(id, principal.getName(), dto));
    }
}
