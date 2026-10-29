package com.enterprise.procurement.support;

import org.slf4j.MDC;

/**
 * 请求级 traceId 的读写入口。
 *
 * <p>把 MDC 的 key 收敛在这里，而不是让 {@code TraceIdFilter}、响应体、异常处理器
 * 各自写死字符串 {@code "traceId"}——三处硬编码是典型的"改一处忘两处"事故来源。</p>
 */
public final class TraceContext {

    public static final String HEADER = "X-Trace-Id";
    private static final String MDC_KEY = "traceId";

    private TraceContext() {}

    public static void set(String traceId) {
        MDC.put(MDC_KEY, traceId);
    }

    public static void clear() {
        MDC.remove(MDC_KEY);
    }

    /** 当前线程绑定的 traceId；不在请求线程内（如启动阶段、定时任务）返回 null。 */
    public static String current() {
        return MDC.get(MDC_KEY);
    }
}
