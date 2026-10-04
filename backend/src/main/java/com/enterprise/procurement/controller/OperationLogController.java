package com.enterprise.procurement.controller;

import com.enterprise.procurement.dto.ApiResponse; import org.springframework.web.bind.annotation.*; import java.time.LocalDateTime; import java.util.*;

@RestController @RequestMapping("/api/operation-logs") public class OperationLogController { @GetMapping public ApiResponse<?> list(){return ApiResponse.ok(List.of(Map.of("id",1,"operator","system","action","SYSTEM_START","resource","procurement","createdAt",LocalDateTime.now()))); } }
