package com.enterprise.procurement.aspect;

import com.enterprise.procurement.admin.entity.OperationLogEntity;
import com.enterprise.procurement.admin.mapper.OperationLogMapper;
import com.enterprise.procurement.persistence.mapper.CatalogMapper;
import com.enterprise.procurement.security.AuthCacheService;
import com.enterprise.procurement.security.AuthSnapshot;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;

/**
 * 操作日志切面。
 *
 * <p>改造前的实现有几处硬伤，导致这批日志基本没法用来追责：</p>
 * <ul>
 *   <li><b>操作人恒为 null</b>：{@code auth != null && auth.isAuthenticated() ? null : null}——
 *       三元表达式的两个分支都写成了 null，等于整个登录态判断被短路掉了；</li>
 *   <li><b>模块名写死</b>「采购管理」，后台管理类操作也被记成采购管理；</li>
 *   <li><b>没有业务对象 id</b>，只知道"有人创建了采购订单"，不知道是哪一单；</li>
 *   <li><b>没有耗时</b>，日志无法用于排查慢操作；</li>
 *   <li><b>记录失败会影响业务</b>：写日志写在 {@code finally} 里且没有捕获异常，
 *       一旦日志表不可写，业务请求本身会跟着失败/返回值被覆盖。</li>
 * </ul>
 *
 * <p>这里的取舍很明确：<b>审计日志是旁路，不能成为业务的失败点</b>。
 * 因此整个记录过程包在 catch(Throwable) 里，失败只打 WARN。</p>
 */
@Aspect
@Component
public class OperationLogAspect {

    private static final Logger log = LoggerFactory.getLogger(OperationLogAspect.class);

    private final OperationLogMapper mapper;
    private final AuthCacheService authCache;
    private final CatalogMapper catalog;

    public OperationLogAspect(OperationLogMapper mapper, AuthCacheService authCache, CatalogMapper catalog) {
        this.mapper = mapper;
        this.authCache = authCache;
        this.catalog = catalog;
    }

    @Around("@annotation(com.enterprise.procurement.aspect.OperationLog)")
    public Object record(ProceedingJoinPoint point) throws Throwable {
        OperationLog op = ((MethodSignature) point.getSignature()).getMethod().getAnnotation(OperationLog.class);
        long started = System.nanoTime();
        String result = "SUCCESS";
        Object returned = null;
        try {
            returned = point.proceed();
            return returned;
        } catch (Throwable ex) {
            result = "FAIL";
            throw ex;
        } finally {
            // 先算出耗时再落库；整段都有异常保护，绝不影响业务返回
            int costMs = (int) ((System.nanoTime() - started) / 1_000_000L);
            try {
                persist(op, point, returned, result, costMs);
            } catch (Throwable ex) {
                log.warn("写操作日志失败（已忽略，不影响业务）：action={}, 原因={}", op.value(), ex.toString());
            }
        }
    }

    private void persist(OperationLog op, ProceedingJoinPoint point, Object returned, String result, int costMs) {
        OperationLogEntity row = new OperationLogEntity();
        row.setModule(op.module());
        row.setAction(op.value());
        row.setResult(result);
        row.setCostMs(costMs);
        // 业务 id 优先取返回值里的真实对象 id：创建类接口没有路径变量；
        // 「创建订单」虽然带了个 Long requestId 参数，但那只是来源申请，不是订单 id。
        // 返回值里取不到（例如删除接口返回 Boolean）才退回参数里的第一个 Long。
        Long bizId = bizIdOf(returned);
        row.setBizId(bizId != null ? bizId : firstLongArg(point.getArgs()));
        row.setCreateTime(LocalDateTime.now());

        String username = currentUsername();
        if (username != null) {
            row.setOperatorName(username);
            row.setOperatorId(resolveUserId(username));
        }

        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            HttpServletRequest req = attrs.getRequest();
            row.setRequestUri(req.getRequestURI());
            row.setIp(clientIp(req));
        }
        mapper.insert(row);
    }

    /** 已登录返回用户名，匿名（未带 token / 验签失败）返回 null。 */
    private String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return null;
        }
        return auth.getName();
    }

    /** 优先用鉴权快照里的 id，避免为了写日志再多查一次库。 */
    private Long resolveUserId(String username) {
        try {
            AuthSnapshot snapshot = authCache.get(username);
            if (snapshot != null) {
                return snapshot.userId();
            }
            var user = catalog.user(username);
            return user == null ? null : user.getId();
        } catch (Exception e) {
            return null;
        }
    }

    /** 取第一个 Long 参数作为业务对象 id（controller 的 {@code @PathVariable Long id} 正是这个形状）。 */
    private Long firstLongArg(Object[] args) {
        if (args == null) {
            return null;
        }
        for (Object arg : args) {
            if (arg instanceof Long value) {
                return value;
            }
        }
        return null;
    }

    /**
     * 从返回值里找业务 id：controller 统一返回 {@code ApiResponse<T>}，先拆出 data，
     * 再按 {@code getId()}/{@code id()} 取值。取不到就是 null，不影响日志落库。
     */
    private Long bizIdOf(Object returned) {
        if (returned == null) {
            return null;
        }
        Object payload = invokeGetter(returned, "data");
        Long id = invokeLong(payload, "getId", "id");
        return id != null ? id : invokeLong(returned, "getId", "id");
    }

    private Object invokeGetter(Object target, String name) {
        if (target == null) {
            return null;
        }
        try {
            return target.getClass().getMethod(name).invoke(target);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private Long invokeLong(Object target, String... names) {
        for (String name : names) {
            Object value = invokeGetter(target, name);
            if (value instanceof Number number) {
                return number.longValue();
            }
        }
        return null;
    }

    /** 取真实客户端 IP：反向代理后 {@code getRemoteAddr()} 只会拿到网关地址。 */
    private String clientIp(HttpServletRequest req) {
        String forwarded = req.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
        }
        return req.getRemoteAddr();
    }
}
