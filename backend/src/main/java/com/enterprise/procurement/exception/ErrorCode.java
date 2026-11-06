package com.enterprise.procurement.exception;

import org.springframework.http.HttpStatus;

/**
 * 业务错误码：给"失败"一个机器可读的稳定标识。
 *
 * <p>改造前的响应只有 {@code success=false} 和一句中文 message。前端要判断
 * "是被人抢先了还是我填错了"，只能拿 message 做字符串匹配——文案一改前端就崩，
 * 而且中英文/标点差异会让匹配静默失效。错误码与文案分离后：</p>
 * <ul>
 *   <li>前端按 {@code code} 决定交互（刷新 / 标红字段 / 提示重试）；</li>
 *   <li>监控按 {@code code} 聚合告警，不必解析中文；</li>
 *   <li>文案可以随时改，契约不变。</li>
 * </ul>
 *
 * <p>每个码固定对应一个 HTTP 状态：4xx 是"请求或状态的问题"，5xx 只留给真故障。</p>
 */
public enum ErrorCode {

    OK("OK", HttpStatus.OK),

    /** 参数不合法（手写校验，不是 Bean Validation 拦下来的那些）。 */
    BAD_REQUEST("BAD_REQUEST", HttpStatus.BAD_REQUEST),
    /** Bean Validation 失败，data 里带字段级提示。 */
    VALIDATION_FAILED("VALIDATION_FAILED", HttpStatus.BAD_REQUEST),
    /** 未登录或 token 失效。 */
    AUTH_REQUIRED("AUTH_REQUIRED", HttpStatus.UNAUTHORIZED),
    /** 已登录但无权做这件事（角色不足 / 不是本人 / 跨部门）。 */
    FORBIDDEN("FORBIDDEN", HttpStatus.FORBIDDEN),
    /** 不存在，或存在但无权可见（不暴露存在性）。 */
    NOT_FOUND("NOT_FOUND", HttpStatus.NOT_FOUND),

    /** 状态已被别人改掉，本次操作基于旧状态。 */
    STATE_CONFLICT("STATE_CONFLICT", HttpStatus.CONFLICT),
    /** 唯一键冲突兜底。 */
    DUPLICATE_KEY("DUPLICATE_KEY", HttpStatus.CONFLICT),
    /** 订单已入库，重复提交。 */
    ORDER_ALREADY_DELIVERED("ORDER_ALREADY_DELIVERED", HttpStatus.CONFLICT),
    /** 该申请已生成订单，重复下单。 */
    REQUEST_ALREADY_ORDERED("REQUEST_ALREADY_ORDERED", HttpStatus.CONFLICT),
    /** 库存不够扣（含并发下抢输）。 */
    STOCK_INSUFFICIENT("STOCK_INSUFFICIENT", HttpStatus.CONFLICT),
    /** 同一个幂等键被用于不同请求内容（客户端 bug）。 */
    IDEMPOTENCY_KEY_REUSED("IDEMPOTENCY_KEY_REUSED", HttpStatus.CONFLICT),
    /** 同幂等键的请求正在处理，稍后重试。 */
    IDEMPOTENCY_IN_PROGRESS("IDEMPOTENCY_IN_PROGRESS", HttpStatus.CONFLICT),

    /** 未预期异常，message 里带 traceId。 */
    INTERNAL_ERROR("INTERNAL_ERROR", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String code;
    private final HttpStatus status;

    ErrorCode(String code, HttpStatus status) {
        this.code = code;
        this.status = status;
    }

    public String code() { return code; }

    public HttpStatus status() { return status; }
}
