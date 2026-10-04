package com.enterprise.procurement.controller;

import com.enterprise.procurement.dto.ApiResponse; import com.enterprise.procurement.service.ProcurementService; import org.springframework.web.bind.annotation.*; import java.util.*;

@RestController @RequestMapping("/api/dashboard") public class DashboardController {private final ProcurementService service; public DashboardController(ProcurementService s){service=s;} @GetMapping("/summary") public ApiResponse<?> summary(){Map<String,Object> m=new LinkedHashMap<>();m.put("applicationCount",service.applications().size());m.put("pendingCount",service.applications().stream().filter(a->a.getStatus().name().equals("PENDING")).count());m.put("approvedCount",service.applications().stream().filter(a->a.getStatus().name().equals("APPROVED")).count());m.put("orderCount",service.orders().size());m.put("supplierCount",service.suppliers().size());m.put("productCount",service.products().size());return ApiResponse.ok(m);} }
