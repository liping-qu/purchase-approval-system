package com.enterprise.procurement.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

/**
 * 鉴权快照的 Redis 缓存。
 *
 * <p>解决的问题很具体：{@link JwtAuthenticationFilter} 每个请求都要查两次库
 * （`sys_user` 关联部门、`sys_role` 关联用户角色），这两条查询的结果在一次会话期间
 * 几乎不变，却占了每请求的固定 DB 开销。压测里它是 QPS 的天花板之一。</p>
 *
 * <p>三条设计取舍：</p>
 * <ul>
 *   <li><b>不缓存「用户不存在」</b>——阴性结果一旦缓存，管理员刚建好的账号要等 TTL
 *       才能登录。只缓存查到的活跃用户。</li>
 *   <li><b>写操作必须显式失效</b>，不能靠 TTL 兜底：改密靠 {@code token_version + 1}
 *       踢掉旧 token，如果快照里的版本号是旧的，这个安全机制就失效了。所以在
 *       改密、管理员改用户、删用户三处都主动删 key。</li>
 *   <li><b>Redis 挂了不能挡住登录</b>——所有读写都捕获异常并降级为查库。
 *       缓存是性能优化，不是正确性依赖；它不该成为一个新的故障点。</li>
 * </ul>
 */
@Service
public class AuthCacheService {

    private static final Logger log = LoggerFactory.getLogger(AuthCacheService.class);
    private static final String PREFIX = "auth:user:";

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final boolean enabled;
    private final Duration ttl;

    public AuthCacheService(Optional<StringRedisTemplate> redis,
                            ObjectMapper mapper,
                            @Value("${app.auth-cache.enabled:true}") boolean enabled,
                            @Value("${app.auth-cache.ttl-seconds:300}") long ttlSeconds) {
        this.redis = redis.orElse(null);
        this.mapper = mapper;
        this.enabled = enabled;
        this.ttl = Duration.ofSeconds(Math.max(1, ttlSeconds));
        if (enabled && this.redis == null) {
            log.warn("鉴权缓存已开启，但容器里没有 StringRedisTemplate（未引入 redis starter？），将全程降级查库");
        }
    }

    /** 命中返回快照，未命中/未启用/Redis 异常一律返回 null，由调用方回源查库。 */
    public AuthSnapshot get(String username) {
        if (!enabled || redis == null || username == null) {
            return null;
        }
        try {
            String json = redis.opsForValue().get(PREFIX + username);
            return json == null ? null : mapper.readValue(json, AuthSnapshot.class);
        } catch (Exception e) {
            // 序列化不兼容或连接失败都走这里：降级查库，不抛给上层
            log.warn("鉴权缓存读取失败，降级查库: {}", e.toString());
            return null;
        }
    }

    public void put(AuthSnapshot snapshot) {
        if (!enabled || redis == null || snapshot == null || snapshot.username() == null) {
            return;
        }
        try {
            redis.opsForValue().set(PREFIX + snapshot.username(), mapper.writeValueAsString(snapshot), ttl);
        } catch (Exception e) {
            log.warn("鉴权缓存写入失败（不影响本次请求）: {}", e.toString());
        }
    }

    /**
     * 失效。改密 / 修改用户 / 删除用户后必须调用，否则旧快照里的角色和 token 版本会继续生效。
     *
     * <p>这里同样吞异常：失效失败最多让旧数据多活一个 TTL，而抛出异常会让
     * "改密成功但接口报错"——那才是更糟的结果。失败时打 error 便于告警。</p>
     */
    public void evict(String username) {
        if (!enabled || redis == null || username == null) {
            return;
        }
        try {
            redis.delete(PREFIX + username);
        } catch (Exception e) {
            log.error("鉴权缓存失效失败，旧快照最长残留 {} 秒: {}", ttl.getSeconds(), e.toString());
        }
    }
}
