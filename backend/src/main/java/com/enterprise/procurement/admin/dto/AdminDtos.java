package com.enterprise.procurement.admin.dto;
import jakarta.validation.constraints.*; import java.util.*;
public final class AdminDtos {
 private AdminDtos(){}
 public record UserQuery(String keyword,Integer status,Long departmentId,Integer page,Integer size){}
 public record UserCreate(@NotBlank String username,@NotBlank @Size(min=6,max=72) String password,@NotBlank String nickname,String phone,@Email String email,Long departmentId,Integer status,Set<Long> roleIds){}
 public record UserUpdate(String nickname,String phone,@Email String email,Long departmentId,Integer status,Set<Long> roleIds,String password){}
 public record DepartmentRequest(@NotBlank String name,Long parentId,Integer status){}
 public record RoleRequest(@NotBlank String code,@NotBlank String name,Set<Long> permissionIds){}
 public record PermissionRequest(@NotBlank String code,@NotBlank String name){}
 public record MenuRequest(@NotBlank String name,Long parentId,String path,String icon,String permissionCode,Integer sortOrder,Integer status){}
 public record ConfigRequest(@NotBlank String configKey,@NotBlank String configValue,String description){}
 public record UserVO(Long id,String username,String nickname,String phone,String email,Long departmentId,Integer status,Set<String> roles,Set<String> permissions){}
 public record SimpleVO(Long id,String code,String name){}
 public record MenuVO(Long id,Long parentId,String name,String path,String icon,String permissionCode,Integer sortOrder,Integer status){}
 public record ConfigVO(Long id,String configKey,String configValue,String description){}
}
