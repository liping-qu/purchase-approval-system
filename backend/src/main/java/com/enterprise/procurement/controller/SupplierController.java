package com.enterprise.procurement.controller;

import com.enterprise.procurement.dto.ApiResponse;
import com.enterprise.procurement.persistence.entity.SupplierEntity;
import com.enterprise.procurement.persistence.mapper.SupplierMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/suppliers")
public class SupplierController {
    private final SupplierMapper mapper;
    public SupplierController(SupplierMapper mapper) { this.mapper = mapper; }
    @GetMapping public ApiResponse<?> list() { return ApiResponse.ok(mapper.selectList(null)); }
    @PostMapping @PreAuthorize("hasAnyRole('ADMIN','PURCHASER')") public ApiResponse<?> create(@Valid @RequestBody SupplierForm form) { SupplierEntity s=form.toEntity(null); mapper.insert(s); return ApiResponse.ok(s); }
    @PutMapping("/{id}") @PreAuthorize("hasAnyRole('ADMIN','PURCHASER')") public ApiResponse<?> update(@PathVariable Long id,@Valid @RequestBody SupplierForm form) { SupplierEntity s=form.toEntity(id); mapper.updateById(s); return ApiResponse.ok(s); }
    @DeleteMapping("/{id}") @PreAuthorize("hasRole('ADMIN')") public ApiResponse<?> delete(@PathVariable Long id) { SupplierEntity s=new SupplierEntity(); s.setId(id); s.setActive(0); mapper.updateById(s); return ApiResponse.ok(true); }
    public record SupplierForm(@NotBlank String name,String code,String contact,String phone,String address) { SupplierEntity toEntity(Long id){SupplierEntity s=new SupplierEntity();s.setId(id);s.setName(name);s.setCode(code);s.setContact(contact);s.setPhone(phone);s.setAddress(address);s.setActive(1);return s;} }
}
