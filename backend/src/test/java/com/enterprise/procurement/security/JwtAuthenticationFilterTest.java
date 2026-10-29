package com.enterprise.procurement.security;

import com.enterprise.procurement.persistence.entity.SystemUserRow;
import com.enterprise.procurement.persistence.mapper.CatalogMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 鉴权缓存对过滤器行为的影响。
 *
 * <p>要钉住的核心不是"缓存能省查询"，而是<b>省查询不能改变鉴权结果</b>：
 * 命中时角色必须仍来自快照、token 版本不一致仍必须拒绝、查不到用户时不许写阴性缓存。</p>
 */
class JwtAuthenticationFilterTest {

    private final JwtService jwt = mock(JwtService.class);
    private final CatalogMapper catalog = mock(CatalogMapper.class);

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private Claims claims(String username, int tv) {
        return Jwts.claims().subject(username).add("tv", tv).build();
    }

    private void wire(String username, int tv) {
        when(jwt.parse(any())).thenReturn(claims(username, tv));
        when(jwt.tokenVersion(any())).thenCallRealMethod();
    }

    private MockHttpServletResponse run(JwtAuthenticationFilter filter) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/purchase-requests");
        req.addHeader("Authorization", "Bearer any-token");
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, new MockFilterChain());
        return res;
    }

    @Test
    @DisplayName("缓存命中：不查库，角色来自快照")
    void cacheHitSkipsDatabase() throws Exception {
        AuthCacheService cache = mock(AuthCacheService.class);
        when(cache.get("alice")).thenReturn(new AuthSnapshot(7L, "alice", 3, List.of("MANAGER")));
        wire("alice", 3);

        run(new JwtAuthenticationFilter(jwt, catalog, cache));

        verifyNoInteractions(catalog);
        verify(cache, never()).put(any());
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth);
        assertEquals("alice", auth.getName());
        assertTrue(auth.getAuthorities().stream().map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_MANAGER"::equals));
    }

    @Test
    @DisplayName("缓存未命中：回源两次查询并写回快照")
    void cacheMissLoadsOnceAndPuts() throws Exception {
        AuthCacheService cache = mock(AuthCacheService.class);
        when(cache.get("bob")).thenReturn(null);
        SystemUserRow row = new SystemUserRow();
        row.setId(9L);
        row.setUsername("bob");
        row.setTokenVersion(1);
        when(catalog.user("bob")).thenReturn(row);
        when(catalog.roleCodes(9L)).thenReturn(List.of("PURCHASER"));
        wire("bob", 1);

        run(new JwtAuthenticationFilter(jwt, catalog, cache));

        verify(catalog, times(1)).user("bob");
        verify(catalog, times(1)).roleCodes(9L);
        verify(cache, times(1)).put(new AuthSnapshot(9L, "bob", 1, List.of("PURCHASER")));
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    @DisplayName("token 版本与快照不一致：不建立认证（改密踢下线不能被缓存绕过）")
    void staleTokenIsRejectedEvenOnCacheHit() throws Exception {
        AuthCacheService cache = mock(AuthCacheService.class);
        when(cache.get("alice")).thenReturn(new AuthSnapshot(7L, "alice", 4, List.of("ADMIN")));
        wire("alice", 3); // token 里还是旧版本

        run(new JwtAuthenticationFilter(jwt, catalog, cache));

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    @DisplayName("用户不存在：保持匿名，且不写阴性缓存")
    void missingUserIsNotCached() throws Exception {
        AuthCacheService cache = mock(AuthCacheService.class);
        when(cache.get("ghost")).thenReturn(null);
        when(catalog.user("ghost")).thenReturn(null);
        wire("ghost", 0);

        run(new JwtAuthenticationFilter(jwt, catalog, cache));

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(cache, never()).put(any());
    }

    @Test
    @DisplayName("关闭缓存后仍能靠查库完成认证（Redis 故障时的降级路径）")
    void degradedModeStillAuthenticates() throws Exception {
        AuthCacheService cache = new AuthCacheService(Optional.empty(),
                new com.fasterxml.jackson.databind.ObjectMapper(), false, 300);
        SystemUserRow row = new SystemUserRow();
        row.setId(1L);
        row.setUsername("admin");
        row.setTokenVersion(0);
        when(catalog.user("admin")).thenReturn(row);
        when(catalog.roleCodes(1L)).thenReturn(List.of("ADMIN"));
        wire("admin", 0);

        run(new JwtAuthenticationFilter(jwt, catalog, cache));

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        verify(catalog, times(1)).user("admin");
    }
}
