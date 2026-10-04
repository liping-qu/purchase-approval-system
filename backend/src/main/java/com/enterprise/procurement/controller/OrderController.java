package com.enterprise.procurement.controller;

import com.enterprise.procurement.dto.ApiResponse; import com.enterprise.procurement.service.ProcurementService; import org.springframework.security.access.prepost.PreAuthorize; import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/orders") public class OrderController {private final ProcurementService service; public OrderController(ProcurementService s){service=s;} @GetMapping public ApiResponse<?> list(){return ApiResponse.ok(service.orders());} @GetMapping("/{id}") public ApiResponse<?> get(@PathVariable Long id){return ApiResponse.ok(service.orders().stream().filter(o->o.getId().equals(id)).findFirst().orElseThrow());} @PostMapping("/{id}/receive") @PreAuthorize("hasAnyRole('ADMIN','WAREHOUSE')") public ApiResponse<?> receive(@PathVariable Long id){return ApiResponse.ok("入库完成",service.receiveOrder(id));}}
