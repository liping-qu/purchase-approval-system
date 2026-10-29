package com.enterprise.procurement.controller;

import com.enterprise.procurement.admin.mapper.SysUserMapper;
import com.enterprise.procurement.dto.ApiResponse;
import com.enterprise.procurement.dto.AuthDtos;
import com.enterprise.procurement.persistence.mapper.CatalogMapper;
import com.enterprise.procurement.security.JwtService;
import jakarta.validation.Valid;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final CatalogMapper catalog;
    private final SysUserMapper users;
    private final JwtService jwt;
    private final PasswordEncoder encoder;

    public AuthController(CatalogMapper catalog, SysUserMapper users, JwtService jwt, PasswordEncoder encoder) {
        this.catalog = catalog;
        this.users = users;
        this.jwt = jwt;
        this.encoder = encoder;
    }

    @PostMapping("/login")
    public ApiResponse<AuthDtos.LoginResponse> login(@Valid @RequestBody AuthDtos.LoginRequest req) {
        var user = catalog.user(req.username());
        // 用户不存在与密码错误返回同一句话，避免"用户名是否存在"被枚举
        if (user == null || user.getPassword() == null || !encoder.matches(req.password(), user.getPassword())) {
            return ApiResponse.fail("用户名或密码错误");
        }
        Set<com.enterprise.procurement.model.Enums.UserRole> roles = new HashSet<>();
        for (String code : catalog.roleCodes(user.getId())) {
            try {
                roles.add(com.enterprise.procurement.model.Enums.UserRole.valueOf(code.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
                // 库里出现枚举之外的编码时不让登录整体失败，只忽略该角色
            }
        }
        int tokenVersion = user.getTokenVersion() == null ? 0 : user.getTokenVersion();
        return ApiResponse.ok(new AuthDtos.LoginResponse(
                jwt.generate(user.getUsername(), tokenVersion),
                user.getId(), user.getUsername(), user.getDisplayName(), roles));
    }

    @GetMapping("/me")
    public ApiResponse<AuthDtos.CurrentUser> me(Principal principal) {
        var user = catalog.user(principal.getName());
        if (user == null) {
            throw new IllegalStateException("当前账号不可用");
        }
        return ApiResponse.ok(new AuthDtos.CurrentUser(
                user.getId(), user.getUsername(), user.getDisplayName(),
                user.getDepartmentId(), user.getDepartmentName(),
                new HashSet<>(catalog.roleCodes(user.getId())),
                new HashSet<>(users.permissionCodes(user.getId()))));
    }

    /**
     * 修改自己的口令。
     *
     * <p>成功后 {@code token_version + 1}，此前签发过的所有 token（包括当前这个）立即失效，
     * 客户端需要重新登录。这是 JWT 方案里"改密即踢下线"的标准做法：
     * 不维护黑名单，只用一个单调递增的版本号做比对。</p>
     */
    @PostMapping("/change-password")
    public ApiResponse<Void> changePassword(@Valid @RequestBody AuthDtos.ChangePasswordRequest req,
                                            Principal principal) {
        var user = users.findByUsername(principal.getName());
        if (user == null) {
            throw new IllegalStateException("当前账号不可用");
        }
        if (!encoder.matches(req.oldPassword(), user.getPassword())) {
            throw new IllegalArgumentException("原密码不正确");
        }
        if (req.oldPassword().equals(req.newPassword())) {
            throw new IllegalArgumentException("新密码不能与原密码相同");
        }
        var patch = new com.enterprise.procurement.admin.entity.SysUser();
        patch.setId(user.getId());
        patch.setPassword(encoder.encode(req.newPassword()));
        patch.setTokenVersion((user.getTokenVersion() == null ? 0 : user.getTokenVersion()) + 1);
        users.updateById(patch);
        return ApiResponse.ok("密码已修改，请重新登录", null);
    }
}
