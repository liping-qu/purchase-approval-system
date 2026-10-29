package com.enterprise.procurement.config;

import com.enterprise.procurement.support.TraceContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 给每个请求打一个 traceId，贯穿日志与响应。
 *
 * <p>解决的是最实际的一个运维问题：用户说"我点了没反应/报错了"，你拿什么去日志里找？
 * 没有 traceId 时只能靠时间戳去撞，一次并发几十个请求就完全对不上。</p>
 *
 * <p>做法：</p>
 * <ul>
 *   <li>优先复用调用方传来的 {@code X-Trace-Id}（网关已生成时可以串起来），没有就本地生成；</li>
 *   <li>写进 MDC，日志 pattern 里用 {@code %X{traceId}} 输出，同一请求的所有日志行自动带上；</li>
 *   <li>写回响应头，同时出现在响应体里，用户截图就能定位；</li>
 *   <li>{@code finally} 清理 —— 线程池会复用线程，不清就会把上一个请求的 traceId 带下去。</li>
 * </ul>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String traceId = request.getHeader(TraceContext.HEADER);
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        }
        TraceContext.set(traceId);
        response.setHeader(TraceContext.HEADER, traceId);
        try {
            chain.doFilter(request, response);
        } finally {
            TraceContext.clear();
        }
    }
}
