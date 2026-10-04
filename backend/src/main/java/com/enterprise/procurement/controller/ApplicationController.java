package com.enterprise.procurement.controller;

import com.enterprise.procurement.dto.*; import com.enterprise.procurement.service.ProcurementService; import jakarta.validation.Valid; import org.springframework.security.access.prepost.PreAuthorize; import org.springframework.web.bind.annotation.*; import java.util.*;

@RestController @RequestMapping("/api/applications")
public class ApplicationController { private final ProcurementService service; public ApplicationController(ProcurementService s){service=s;}
    @GetMapping public ApiResponse<Collection<?>> list(){return ApiResponse.ok(service.applications());}
    @GetMapping("/{id}") public ApiResponse<?> get(@PathVariable Long id){return ApiResponse.ok(service.getApplication(id));}
    @PostMapping public ApiResponse<?> create(@Valid @RequestBody ApplicationDtos.CreateRequest req,java.security.Principal p){return ApiResponse.ok("申请已提交",service.createApplication(p.getName(),req));}
    @PostMapping("/{id}/approval") @PreAuthorize("hasAnyRole('ADMIN','APPROVER')") public ApiResponse<?> approve(@PathVariable Long id,@Valid @RequestBody ApplicationDtos.ApprovalRequest req,java.security.Principal p){return ApiResponse.ok(service.approve(id,p.getName(),req));}
    @PostMapping("/{id}/order") @PreAuthorize("hasAnyRole('ADMIN','PURCHASER')") public ApiResponse<?> order(@PathVariable Long id,@Valid @RequestBody ApplicationDtos.OrderRequest req){return ApiResponse.ok("订单已创建",service.createOrder(id,req.supplierId()));}
}
