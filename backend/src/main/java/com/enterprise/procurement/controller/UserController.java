package com.enterprise.procurement.controller;

import com.enterprise.procurement.admin.service.AdminService;
import com.enterprise.procurement.dto.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户列表查询。
 *
 * <p>两处修正：</p>
 * <ol>
 *   <li>原实现读内存 Map 的 3 个硬编码用户，与数据库完全脱节；现改为查询 {@code sys_user}。</li>
 *   <li>原实现没有加任何权限注解，任何登录用户都能列出全公司账号。用户目录属于管理能力，
 *       这里收敛为 ADMIN 或具备 {@code user:manage} 权限的角色。</li>
 * </ol>
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final AdminService adminService;

    public UserController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('user:manage') or hasRole('ADMIN')")
    public ApiResponse<?> list() {
        return ApiResponse.ok(adminService.userSummaries());
    }
}
