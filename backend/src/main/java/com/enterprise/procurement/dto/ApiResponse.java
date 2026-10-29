package com.enterprise.procurement.dto;

import com.enterprise.procurement.support.TraceContext;

/**
 * 统一响应信封。
 *
 * <p>{@code traceId} 放在最后一列，所有响应（成功和失败）都带。原因很实际：
 * 用户报障时说的往往是"我点了保存，提示系统错误"，此时唯一能把你引导到具体那次请求的
 * 就是 traceId。它同时出现在响应头 {@code X-Trace-Id} 和日志里，三处可对齐。</p>
 *
 * <p>放在末尾是刻意的：JSON 追加字段对现有前端向后兼容，不需要同步改解析代码。</p>
 */
public record ApiResponse<T>(boolean success, String message, T data, String traceId) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, "操作成功", data, TraceContext.current());
    }

    public static <T> ApiResponse<T> ok(String message, T data) {
        return new ApiResponse<>(true, message, data, TraceContext.current());
    }

    public static <T> ApiResponse<T> fail(String message) {
        return new ApiResponse<>(false, message, null, TraceContext.current());
    }
}
