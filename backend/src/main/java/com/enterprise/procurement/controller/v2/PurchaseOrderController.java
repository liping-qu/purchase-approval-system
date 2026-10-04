package com.enterprise.procurement.controller.v2;

import com.enterprise.procurement.aspect.OperationLog;
import com.enterprise.procurement.business.PurchaseBusinessService;
import com.enterprise.procurement.dto.ApiResponse;
import com.enterprise.procurement.dto.PurchaseRequestDtos;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/purchase-orders")
public class PurchaseOrderController {
    private final PurchaseBusinessService service;
    public PurchaseOrderController(PurchaseBusinessService service) { this.service = service; }
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','PURCHASER')")
    @OperationLog("创建采购订单")
    public ApiResponse<?> create(@Valid @RequestBody PurchaseRequestDtos.CreateOrder request, @RequestParam(required = false) Long requestId) {
        Long id = request.requestId() != null ? request.requestId() : requestId;
        if (id == null) throw new IllegalArgumentException("requestId 必填");
        return ApiResponse.ok(service.createOrder(id, request.supplierId()));
    }
    @GetMapping
    public ApiResponse<?> list() { return ApiResponse.ok(service.orderList()); }
    @GetMapping("/{id}")
    public ApiResponse<?> get(@PathVariable Long id) { return ApiResponse.ok(service.getOrder(id)); }
    @PostMapping("/{id}/delivered")
    @PreAuthorize("hasAnyRole('ADMIN','WAREHOUSE')")
    @OperationLog("确认订单入库")
    public ApiResponse<?> delivered(@PathVariable Long id, java.security.Principal principal) {
        return ApiResponse.ok("已入库", service.delivered(id, service.userId(principal.getName())));
    }
}
