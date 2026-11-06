package com.enterprise.procurement.controller.v2;

import com.enterprise.procurement.aspect.OperationLog;
import com.enterprise.procurement.business.PurchaseBusinessService;
import com.enterprise.procurement.dto.ApiResponse;
import com.enterprise.procurement.dto.PurchaseRequestDtos;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping("/api/purchase-orders")
public class PurchaseOrderController {

    private final PurchaseBusinessService service;

    public PurchaseOrderController(PurchaseBusinessService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','PURCHASER')")
    @OperationLog(value = "创建采购订单", module = "采购订单")
    public ApiResponse<?> create(@Valid @RequestBody PurchaseRequestDtos.CreateOrder request,
                                 @RequestParam(required = false) Long requestId) {
        Long id = request.requestId() != null ? request.requestId() : requestId;
        if (id == null) throw new IllegalArgumentException("requestId 必填");
        return ApiResponse.ok(service.createOrder(id, request.supplierId()));
    }

    /**
     * 可下单的申请：已审批通过、且还没有订单。
     *
     * <p>没有这个接口，"审批通过 → 采购下单"这一步在页面上是断的：采购员拿不到
     * 可选的申请单 id，只能靠手输。放在这里而不是让前端遍历申请列表再比对订单，
     * 是因为那需要客户端拉全量数据，分页一开就会漏。</p>
     */
    @GetMapping("/orderable")
    @PreAuthorize("hasAnyRole('ADMIN','PURCHASER')")
    public ApiResponse<?> orderable() {
        return ApiResponse.ok(service.orderableRequests());
    }

    /**
     * 订单列表（分页）。
     *
     * <p>返回值从"数组"改成 {@code {records,total,page,size}}。前端原有代码
     * {@code Array.isArray(data) ? data : data?.records} 两种都能吃，所以这是兼容变更；
     * 新增的 {@code total} 才让分页器算得出页数。</p>
     */
    @GetMapping
    public ApiResponse<?> list(@RequestParam(defaultValue = "1") int page,
                              @RequestParam(defaultValue = "20") int size,
                              @RequestParam(required = false) String status,
                              @RequestParam(required = false) String keyword,
                              @RequestParam(required = false) Long supplierId,
                              Principal principal) {
        return ApiResponse.ok(service.orderPage(principal.getName(), status, keyword, supplierId, page, size));
    }

    @GetMapping("/{id}")
    public ApiResponse<?> get(@PathVariable Long id, Principal principal) {
        return ApiResponse.ok(service.orderDetail(id, principal.getName()));
    }

    @PostMapping("/{id}/delivered")
    @PreAuthorize("hasAnyRole('ADMIN','WAREHOUSE')")
    @OperationLog(value = "确认订单入库", module = "库存")
    public ApiResponse<?> delivered(@PathVariable Long id, Principal principal) {
        return ApiResponse.ok("已入库", service.delivered(id, service.userId(principal.getName())));
    }
}
