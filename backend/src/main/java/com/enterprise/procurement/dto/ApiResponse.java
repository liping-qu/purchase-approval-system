package com.enterprise.procurement.dto;

import com.enterprise.procurement.exception.ErrorCode;
import com.enterprise.procurement.support.TraceContext;

/**
 * 统一响应信封。
 *
 * <p>字段顺序是契约的一部分：</p>
 * <ul>
 *   <li>{@code success} + {@code code}：机器可读。前端按 code 决定交互，
 *       不再拿 message 做字符串匹配——文案改一个字，字符串匹配就静默失效；</li>
 *   <li>{@code message}：给人看，可直接展示；</li>
 *   <li>{@code data}：成功时的载荷，失败时的字段级错误；</li>
 *   <li>{@code traceId}：<b>保持在末位</b>。JSON 追加字段对现有前端向后兼容，
 *       而 {@code frontend/src/api/http.js} 只认 {@code success}/{@code data}，
 *       新增 code 不会影响解包。</li>
 * </ul>
 *
 * <p>{@code traceId} 同时出现在响应头 {@code X-Trace-Id} 和日志里，
 * 用户报障时三处可对齐同一次请求。</p>
 */
public record ApiResponse<T>(boolean success, String code, String message, T data, String traceId) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, ErrorCode.OK.code(), "操作成功", data, TraceContext.current());
    }

    public static <T> ApiResponse<T> ok(String message, T data) {
        return new ApiResponse<>(true, ErrorCode.OK.code(), message, data, TraceContext.current());
    }

    public static <T> ApiResponse<T> fail(ErrorCode errorCode, String message) {
        return new ApiResponse<>(false, errorCode.code(), message, null, TraceContext.current());
    }

    /**
     * 兼容旧调用点：不带具体错误码时给通用的 {@code BAD_REQUEST}。
     * 新代码请显式传 {@link ErrorCode}，让失败原因可被程序识别。
     */
    public static <T> ApiResponse<T> fail(String message) {
        return fail(ErrorCode.BAD_REQUEST, message);
    }

    /** 失败且带字段级明细（Bean Validation 用）。 */
    public static <T> ApiResponse<T> fail(ErrorCode errorCode, String message, T data) {
        return new ApiResponse<>(false, errorCode.code(), message, data, TraceContext.current());
    }
}
