package com.enterprise.procurement.controller;
import com.enterprise.procurement.dto.ApiResponse; import com.enterprise.procurement.service.AdminService; import org.springframework.web.bind.annotation.*; import java.util.*;
@RestController @RequestMapping("/api/admin")
public class AdminController { private final AdminService s; public AdminController(AdminService s){this.s=s;}
 @GetMapping("/departments") public ApiResponse<?> departments(){return ApiResponse.ok(s.departments());} @PostMapping("/departments") public ApiResponse<?> addDept(@RequestBody Map<String,Object> b){return ApiResponse.ok(s.add("department",b));} @DeleteMapping("/departments/{id}") public ApiResponse<?> delDept(@PathVariable Long id){s.delete("department",id);return ApiResponse.ok(null);}
 @GetMapping("/roles") public ApiResponse<?> roles(){return ApiResponse.ok(s.roles());} @PostMapping("/roles") public ApiResponse<?> addRole(@RequestBody Map<String,Object> b){return ApiResponse.ok(s.add("role",b));} @DeleteMapping("/roles/{id}") public ApiResponse<?> delRole(@PathVariable Long id){s.delete("role",id);return ApiResponse.ok(null);}
 @GetMapping("/menus") public ApiResponse<?> menus(){return ApiResponse.ok(s.menus());} @PostMapping("/menus") public ApiResponse<?> addMenu(@RequestBody Map<String,Object> b){return ApiResponse.ok(s.add("menu",b));} @DeleteMapping("/menus/{id}") public ApiResponse<?> delMenu(@PathVariable Long id){s.delete("menu",id);return ApiResponse.ok(null);}
 @GetMapping("/configs") public ApiResponse<?> configs(){return ApiResponse.ok(s.configs());} @PostMapping("/configs") public ApiResponse<?> addConfig(@RequestBody Map<String,Object> b){return ApiResponse.ok(s.add("config",b));}
 @GetMapping("/logs") public ApiResponse<?> logs(){return ApiResponse.ok(s.logs());}
}
