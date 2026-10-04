package com.enterprise.procurement.controller;

import com.enterprise.procurement.dto.*; import com.enterprise.procurement.persistence.mapper.CatalogMapper; import com.enterprise.procurement.security.JwtService; import jakarta.validation.Valid; import org.springframework.security.crypto.password.PasswordEncoder; import org.springframework.web.bind.annotation.*; import java.util.*;

@RestController @RequestMapping("/api/auth")
public class AuthController { private final CatalogMapper catalog; private final JwtService jwt; private final PasswordEncoder encoder; public AuthController(CatalogMapper c,JwtService j,PasswordEncoder e){catalog=c;jwt=j;encoder=e;}
    @PostMapping("/login") public ApiResponse<AuthDtos.LoginResponse> login(@Valid @RequestBody AuthDtos.LoginRequest r){var u=catalog.user(r.username()); if(u==null||u.getPassword()==null||!encoder.matches(r.password(),u.getPassword())) return new ApiResponse<>(false,"用户名或密码错误",null);var roleCodes=catalog.roleCodes(u.getId());Set<com.enterprise.procurement.model.Enums.UserRole> roles=new HashSet<>();for(String code:roleCodes){try{roles.add(com.enterprise.procurement.model.Enums.UserRole.valueOf(code.toUpperCase(Locale.ROOT)));}catch(Exception ignored){}}return ApiResponse.ok(new AuthDtos.LoginResponse(jwt.generate(u.getUsername()),u.getId(),u.getUsername(),u.getDisplayName(),roles));}
    @GetMapping("/me") public ApiResponse<Object> me(java.security.Principal p){return ApiResponse.ok(catalog.user(p.getName()));}
}
