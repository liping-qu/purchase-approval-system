package com.enterprise.procurement.controller;

import com.enterprise.procurement.dto.ApiResponse; import com.enterprise.procurement.model.User; import com.enterprise.procurement.service.ProcurementService; import org.springframework.web.bind.annotation.*; import java.util.*;

@RestController @RequestMapping("/api/users") public class UserController { private final ProcurementService service; public UserController(ProcurementService s){service=s;} @GetMapping public ApiResponse<?> list(){return ApiResponse.ok(service.users().stream().map(this::safe).toList());} private Map<String,Object> safe(User u){Map<String,Object> m=new LinkedHashMap<>(); m.put("id",u.getId());m.put("username",u.getUsername());m.put("displayName",u.getDisplayName());m.put("department",u.getDepartment());m.put("roles",u.getRoles());m.put("enabled",u.isEnabled());return m;} }
