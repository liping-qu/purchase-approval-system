package com.enterprise.procurement.controller;

import com.enterprise.procurement.dto.ApiResponse; import com.enterprise.procurement.service.ProcurementService; import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/inventory") public class InventoryController {private final ProcurementService service; public InventoryController(ProcurementService s){service=s;} @GetMapping public ApiResponse<?> list(){return ApiResponse.ok(service.inventory());} @GetMapping("/products") public ApiResponse<?> products(){return ApiResponse.ok(service.products());}}
