package com.enterprise.procurement.exception;

import com.enterprise.procurement.dto.ApiResponse;
import com.enterprise.procurement.support.TraceContext;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;

import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 异常契约测试。
 *
 * <p>改造前这套契约是破的：参数校验失败返回 500、越权返回 500、未知路由返回 500，
 * 前端无法区分"我填错了""我没权限""服务挂了"。这里把每种情形都钉成断言，
 * 保证 4xx/5xx 的边界不会在后续改动中悄悄退化。</p>
 */
class ExceptionContractTest {

    private static final String TRACE_ID = "test-trace-0001";

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        TraceContext.set(TRACE_ID);
        mvc = MockMvcBuilders.standaloneSetup(new ProbeController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        TraceContext.clear();
    }

    @Test
    @DisplayName("参数校验失败返回 400，并给出字段级提示（原来是 500）")
    void validationFailureIs400WithFieldErrors() throws Exception {
        mvc.perform(post("/probe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"\",\"quantity\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("参数校验失败"))
                .andExpect(jsonPath("$.data.reason").exists())
                .andExpect(jsonPath("$.data.quantity").exists())
                .andExpect(jsonPath("$.traceId").value(TRACE_ID));
    }

    @Test
    @DisplayName("参数/调用顺序错误返回 400，文案可直接展示")
    void badArgumentIs400() throws Exception {
        mvc.perform(get("/bad-argument"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("action 必须为 APPROVE 或 REJECT"));
    }

    @Test
    @DisplayName("并发/状态冲突返回 409，与 400 区分（原来也是 400，前端无法提示刷新重试）")
    void stateConflictIs409() throws Exception {
        mvc.perform(get("/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("STATE_CONFLICT"))
                .andExpect(jsonPath("$.message").value("申请状态已变更，请刷新后重试"))
                .andExpect(jsonPath("$.traceId").value(TRACE_ID));
    }

    @Test
    @DisplayName("无权限返回 403 —— 原来和参数错误一样是 400，前端无法区分改输入还是找管理员")
    void forbiddenIs403() throws Exception {
        mvc.perform(get("/forbidden"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("仅草稿本人可编辑"));
    }

    @Test
    @DisplayName("资源不存在返回 404")
    void missingResourceIs404() throws Exception {
        mvc.perform(get("/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("采购申请不存在"));
    }

    @Test
    @DisplayName("请求体不是合法 JSON 返回 400 而不是 500")
    void unreadableBodyIs400() throws Exception {
        mvc.perform(post("/probe").contentType(MediaType.APPLICATION_JSON).content("{not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("请求体格式错误，请检查 JSON 结构与字段类型"));
    }

    @Test
    @DisplayName("未知路由返回 404 而不是 500")
    void unknownRouteIs404() throws Exception {
        mvc.perform(get("/no-such-endpoint"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("真正的服务端异常返回 500，并在文案里带 traceId")
    void unexpectedErrorCarriesTraceId() throws Exception {
        mvc.perform(get("/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("服务器内部错误（traceId=" + TRACE_ID + "）"))
                .andExpect(jsonPath("$.traceId").value(TRACE_ID));
    }

    @Test
    @DisplayName("成功响应也带 traceId，便于前后端对齐同一次请求")
    void successAlsoCarriesTraceId() throws Exception {
        mvc.perform(get("/ok"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.traceId").value(TRACE_ID));
    }

    @Test
    @DisplayName("信封字段顺序固定：traceId 必须在最后（前端按位置解析的兼容性依赖它）")
    void traceIdStaysLastInJson() throws Exception {
        String body = mvc.perform(get("/ok"))
                .andReturn().getResponse().getContentAsString();

        assertTrue(body.indexOf("\"traceId\"") > body.indexOf("\"data\""), "traceId 不应出现在 data 之前: " + body);
        assertTrue(body.indexOf("\"code\"") > 0 && body.indexOf("\"code\"") < body.indexOf("\"message\""),
                "code 应在 message 之前: " + body);
    }

    @Test
    @DisplayName("500 必须落 ERROR 日志且带 MDC traceId，否则 traceId 无从关联")
    void unexpectedErrorIsLoggedWithTraceId() throws Exception {
        Logger handlerLogger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        handlerLogger.addAppender(appender);
        try {
            mvc.perform(get("/boom")).andExpect(status().isInternalServerError());
        } finally {
            handlerLogger.detachAppender(appender);
        }
        boolean logged = appender.list.stream().anyMatch(ev ->
                ev.getLevel() == Level.ERROR
                        && ev.getThrowableProxy() != null
                        && TRACE_ID.equals(ev.getMDCPropertyMap().get("traceId")));
        assertTrue(logged, "500 应打出一条 ERROR 日志，堆栈非空，且 MDC 中带 traceId");
    }

    // ---------------------------------------------------------------- 测试用探针

    @RestController
    static class ProbeController {

        record Body(@NotBlank(message = "事由不能为空") String reason,
                    @NotNull @Min(value = 1, message = "数量至少为 1") Integer quantity) {}

        @PostMapping("/probe")
        ApiResponse<String> probe(@Valid @RequestBody Body body) {
            return ApiResponse.ok("ok");
        }

        @GetMapping("/conflict")
        ApiResponse<String> conflict() {
            throw new StateConflictException("申请状态已变更，请刷新后重试");
        }

        @GetMapping("/bad-argument")
        ApiResponse<String> badArgument() {
            throw new IllegalArgumentException("action 必须为 APPROVE 或 REJECT");
        }

        @GetMapping("/forbidden")
        ApiResponse<String> forbidden() {
            throw new ForbiddenException("仅草稿本人可编辑");
        }

        @GetMapping("/missing")
        ApiResponse<String> missing() {
            throw new NoSuchElementException("采购申请不存在");
        }

        @GetMapping("/boom")
        ApiResponse<String> boom() {
            throw new RuntimeException("数据库连接池耗尽");
        }

        @GetMapping("/ok")
        ApiResponse<String> ok() {
            return ApiResponse.ok("ok");
        }
    }
}
