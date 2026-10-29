package com.enterprise.procurement.controller.v2;

import com.enterprise.procurement.business.PurchaseBusinessService;
import com.enterprise.procurement.dto.ApiResponse;
import com.enterprise.procurement.dto.PurchaseRequestDtos;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping("/api/purchase-requests")
public class PurchaseRequestController {

    private final PurchaseBusinessService service;

    public PurchaseRequestController(PurchaseBusinessService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<?> list(Principal principal) {
        return ApiResponse.ok(service.list(principal.getName()));
    }

    /** 详情。可见性由 service 判定，越权读取返回 404（不暴露记录是否存在）。 */
    @GetMapping("/{id}")
    public ApiResponse<?> get(@PathVariable Long id, Principal principal) {
        return ApiResponse.ok(service.get(id, principal.getName()));
    }

    @GetMapping("/{id}/items")
    public ApiResponse<?> items(@PathVariable Long id, Principal principal) {
        return ApiResponse.ok(service.requestItems(id, principal.getName()));
    }

    @PostMapping
    public ApiResponse<?> create(@Valid @RequestBody PurchaseRequestDtos.Create dto, Principal principal) {
        return ApiResponse.ok("草稿已创建", service.createDraft(principal.getName(), dto));
    }

    @PutMapping("/{id}")
    public ApiResponse<?> update(@PathVariable Long id, @Valid @RequestBody PurchaseRequestDtos.Update dto,
                                 Principal principal) {
        return ApiResponse.ok(service.updateDraft(id, principal.getName(), dto));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<?> delete(@PathVariable Long id, Principal principal) {
        service.deleteDraft(id, principal.getName());
        return ApiResponse.ok("已删除", null);
    }

    @PostMapping("/{id}/submit")
    public ApiResponse<?> submit(@PathVariable Long id, Principal principal) {
        return ApiResponse.ok(service.submit(id, principal.getName()));
    }

    @PostMapping("/{id}/withdraw")
    public ApiResponse<?> withdraw(@PathVariable Long id, Principal principal) {
        return ApiResponse.ok(service.withdraw(id, principal.getName()));
    }
}
