package com.enterprise.procurement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Set;
import com.enterprise.procurement.model.Enums;

public final class AuthDtos { private AuthDtos(){}
    public record LoginRequest(@NotBlank String username,@NotBlank String password){}
    public record LoginResponse(String token,Long userId,String username,String displayName,Set<Enums.UserRole> roles){}

    /**
     * "我是谁"的对外视图。
     *
     * <p>刻意不含 {@code password}。之前 {@code /api/auth/me} 直接把数据库投影
     * {@code SystemUserRow} 序列化出去，任何登录用户都能拿到自己的 BCrypt 哈希；
     * 哈希虽然不能直接反解，但可离线爆破、也可能被复用到其它系统，属于典型的
     * "过度暴露实体"漏洞。对外一律用 VO，是比"给字段加 @JsonIgnore"更稳的做法——
     * 后者依赖开发者记得加，前者默认什么都不给。</p>
     */
    public record CurrentUser(Long id,String username,String displayName,Long departmentId,
                              String departmentName,Set<String> roles,Set<String> permissions){}

    public record ChangePasswordRequest(@NotBlank String oldPassword,
                                        @NotBlank @Size(min = 8, max = 64) String newPassword){}
}
