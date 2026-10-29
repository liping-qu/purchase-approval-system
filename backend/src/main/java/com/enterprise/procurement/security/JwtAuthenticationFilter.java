package com.enterprise.procurement.security;

import com.enterprise.procurement.persistence.mapper.CatalogMapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 每请求解析 Bearer token 并建立 SecurityContext。
 *
 * <p>三道门，任一不过就<b>不建立认证</b>（请求继续走，由 SecurityConfig 的
 * authenticationEntryPoint 统一回 401，而不是在这里直接写响应）：</p>
 * <ol>
 *   <li>验签/有效期 —— {@link JwtService#parse}；</li>
 *   <li>账号仍存在且启用 —— {@code sys_user.status = 1}，删号或禁用即刻失效；</li>
 *   <li>token 版本一致 —— {@code tv} 声明与 {@code sys_user.token_version} 相等，
 *       改密/强制下线后旧的 token 在这里被挡住。</li>
 * </ol>
 *
 * <p>第 2、3 步原本每请求两次查库，现在走 {@link AuthCacheService}：命中则零次查库，
 * 未命中回源一次并把结果写回缓存。注意"用户不存在"不写缓存，避免新建账号被阴性结果挡住。</p>
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwt;
    private final CatalogMapper catalog;
    private final AuthCacheService cache;

    public JwtAuthenticationFilter(JwtService jwt, CatalogMapper catalog, AuthCacheService cache) {
        this.jwt = jwt;
        this.catalog = catalog;
        this.cache = cache;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String header = req.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            authenticate(header.substring(BEARER_PREFIX.length()).trim());
        }
        chain.doFilter(req, res);
    }

    private void authenticate(String token) {
        try {
            Claims claims = jwt.parse(token);
            String username = claims.getSubject();
            if (username == null || username.isBlank()) {
                return;
            }
            AuthSnapshot snapshot = cache.get(username);
            if (snapshot == null) {
                snapshot = loadFromDb(username);
                if (snapshot == null) {
                    return; // 账号不存在或已禁用：不缓存阴性结果
                }
                cache.put(snapshot);
            }
            if (jwt.tokenVersion(claims) != snapshot.tokenVersion()) {
                return;
            }
            Set<SimpleGrantedAuthority> authorities = snapshot.roles().stream()
                    .map(code -> code.toUpperCase())
                    .map(code -> code.startsWith("ROLE_") ? code : "ROLE_" + code)
                    .map(SimpleGrantedAuthority::new)
                    .collect(Collectors.toSet());
            SecurityContextHolder.getContext()
                    .setAuthentication(new UsernamePasswordAuthenticationToken(username, null, authorities));
        } catch (Exception ignored) {
            // 验签失败/过期/格式错误：保持匿名，交由后续鉴权环节返回 401
        }
    }

    /** 回源：一次用户查询 + 一次角色查询，结果封装成快照。 */
    private AuthSnapshot loadFromDb(String username) {
        var user = catalog.user(username);
        if (user == null) {
            return null;
        }
        int version = user.getTokenVersion() == null ? 0 : user.getTokenVersion();
        return new AuthSnapshot(user.getId(), user.getUsername(), version, catalog.roleCodes(user.getId()));
    }
}

