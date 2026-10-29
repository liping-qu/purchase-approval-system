package com.enterprise.procurement.security;

import java.util.List;

/**
 * 鉴权所需的用户快照。
 *
 * <p>刻意只放"每个请求都要用"的字段：id、用户名、token 版本、角色编码。
 * 不含 password、部门名、权限明细——那些只在 {@code /auth/me} 这类一次性接口里要，
 * 塞进这里只会让缓存变大、还要处理更多失效场景。</p>
 *
 * <p>用 record 而不是实体：缓存里的东西是<b>某一刻的只读副本</b>，
 * 让它不可变，可以杜绝"有人改了缓存对象却没写回库"的隐患。</p>
 */
public record AuthSnapshot(Long userId, String username, int tokenVersion, List<String> roles) {

    public AuthSnapshot {
        roles = roles == null ? List.of() : List.copyOf(roles);
    }
}
