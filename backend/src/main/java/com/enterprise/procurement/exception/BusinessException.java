package com.enterprise.procurement.exception;

/**
 * 带错误码的业务异常基类。
 *
 * <p>继承它的异常不需要自己决定 HTTP 状态：状态与码都绑在 {@link ErrorCode} 上，
 * 全局处理器只按 {@code errorCode()} 翻译。这样"新增一个错误场景"= 加一个枚举值，
 * 而不是在处理器里再加一个 {@code @ExceptionHandler}。</p>
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}
