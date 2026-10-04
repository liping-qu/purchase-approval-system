package com.enterprise.procurement.controller;

import com.enterprise.procurement.dto.ApiResponse; import com.enterprise.procurement.service.ProcurementService; import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/products") public class ProductController {private final ProcurementService service; public ProductController(ProcurementService s){service=s;} @GetMapping public ApiResponse<?> list(){return ApiResponse.ok(service.products());}}
