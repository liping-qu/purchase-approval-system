package com.enterprise.procurement.controller;

import com.enterprise.procurement.dto.ApiResponse; import com.enterprise.procurement.service.ProcurementService; import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/suppliers") public class SupplierController {private final ProcurementService service; public SupplierController(ProcurementService s){service=s;} @GetMapping public ApiResponse<?> list(){return ApiResponse.ok(service.suppliers());}}
