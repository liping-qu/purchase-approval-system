package com.enterprise.procurement.exception;

import com.enterprise.procurement.dto.ApiResponse;
import com.enterprise.procurement.support.TraceContext;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * 统一异常出口：让"失败"也有稳定的契约。
 *
 * <p>改造前的问题很具体：</p>
 * <ul>
 *   <li>参数校验失败（{@code @Valid}）没有专属处理，落进 {@code Exception} 兜底，返回 <b>500</b>；
 *       前端既拿不到 400 也拿不到字段级提示，只能笼统提示"系统错误"。</li>
 *   <li>越权抛的 {@code AuthorizationDeniedException} 同样被兜成 500，
 *       于是"没权限"和"服务挂了"用同一个状态码，监控里分不清。</li>
 *   <li>未知路由不落到 404，也是 500。</li>
 *   <li>500 的响应体里没有任何可用于排查的标识，用户报障时无法定位是哪一次请求。</li>
 * </ul>
 *
 * <p>现在的约定：业务上可预期的失败一律 4xx，文案可直接展示给用户；
 * 只有真正的服务端故障给 500，并在 message 里带上 traceId。</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ---------------------------------------------------------------- 4xx 业务

    @ExceptionHandler(NoSuchElementException.class)
    ResponseEntity<ApiResponse<Void>> notFound(Exception e) {
        return build(ErrorCode.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiResponse<Void>> forbidden(AccessDeniedException e) {
        return build(ErrorCode.FORBIDDEN, "无权访问该资源");
    }

    /**
     * 所有带错误码的业务异常：状态与码都由 {@link ErrorCode} 决定。
     *
     * <p>加这个处理器之后，"新增一种失败场景"= 加一个枚举值，
     * 而不是在 advice 里再加一个方法——错误码与 HTTP 状态不会再各自漂移。</p>
     */
    @ExceptionHandler(BusinessException.class)
    ResponseEntity<ApiResponse<Void>> business(BusinessException e) {
        return build(e.errorCode(), e.getMessage());
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    ResponseEntity<ApiResponse<Void>> badRequest(Exception e) {
        return build(ErrorCode.BAD_REQUEST, e.getMessage());
    }

    /**
     * 唯一键冲突兜底。
     *
     * <p>业务的幂等冲突大多已在 service 里翻译成具体错误码（例如 {@code REQUEST_ALREADY_ORDERED}）。
     * 这里是最后一层：万一还有没预料到的唯一键冲突，返回 409 而不是 500——
     * 500 会让前端和监控把"用户重复提交"误判成"服务端故障"。</p>
     */
    @ExceptionHandler(DuplicateKeyException.class)
    ResponseEntity<ApiResponse<Void>> duplicateKey(DuplicateKeyException e) {
        return build(ErrorCode.DUPLICATE_KEY, "数据已存在，请勿重复提交");
    }

    // ---------------------------------------------------------------- 4xx 参数

    /**
     * {@code @Valid} 校验失败。字段名和提示一起返回，前端可以直接标红对应输入框，
     * 而不是只能弹一句"操作失败"。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiResponse<Map<String, String>>> invalidBody(MethodArgumentNotValidException e) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fe : e.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(fe.getField(), fe.getDefaultMessage());
        }
        return ResponseEntity.badRequest()
                .body(ApiResponse.fail(ErrorCode.VALIDATION_FAILED, "参数校验失败", errors));
    }

    /** {@code @RequestParam} / {@code @PathVariable} 上的约束失败。 */
    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiResponse<Map<String, String>>> invalidParam(ConstraintViolationException e) {
        Map<String, String> errors = new LinkedHashMap<>();
        e.getConstraintViolations().forEach(v ->
                errors.putIfAbsent(v.getPropertyPath().toString(), v.getMessage()));
        return ResponseEntity.badRequest()
                .body(ApiResponse.fail(ErrorCode.VALIDATION_FAILED, "参数校验失败", errors));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<ApiResponse<Void>> missingParam(MissingServletRequestParameterException e) {
        return build(ErrorCode.BAD_REQUEST, "缺少必要参数: " + e.getParameterName());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiResponse<Void>> typeMismatch(MethodArgumentTypeMismatchException e) {
        return build(ErrorCode.BAD_REQUEST, "参数类型不正确: " + e.getName());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiResponse<Void>> unreadableBody(HttpMessageNotReadableException e) {
        return build(ErrorCode.BAD_REQUEST, "请求体格式错误，请检查 JSON 结构与字段类型");
    }

    // ---------------------------------------------------------------- 4xx 路由

    /** 请求了不存在的接口。改造前这类请求同样返回 500。 */
    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    ResponseEntity<ApiResponse<Void>> noHandler(Exception e) {
        return build(ErrorCode.NOT_FOUND, "接口不存在");
    }

    // ---------------------------------------------------------------- 5xx

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiResponse<Void>> generic(Exception e) {
        String id = TraceContext.current();
        // 关键：必须把堆栈打进日志。只把 traceId 回给前端而不记录异常，
        // 用户拿着 id 来查也只会得到"查无此 id"——traceId 就成了摆设。
        // 日志 pattern 里已含 %X{traceId}，这里由 MDC 自动补上，无需手工拼接。
        log.error("未处理异常，已返回 500", e);
        // message 里带 traceId：用户截图后，运维 grep 这一个 id 就能拿到完整调用日志
        String message = id == null ? "服务器内部错误" : "服务器内部错误（traceId=" + id + "）";
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiResponse<>(false, ErrorCode.INTERNAL_ERROR.code(), message, null, id));
    }

    // ---------------------------------------------------------------- 内部

    private ResponseEntity<ApiResponse<Void>> build(ErrorCode errorCode, String message) {
        return ResponseEntity.status(errorCode.status()).body(ApiResponse.fail(errorCode, message));
    }
}
