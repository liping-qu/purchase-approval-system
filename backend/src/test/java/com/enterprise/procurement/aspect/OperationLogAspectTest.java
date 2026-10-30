package com.enterprise.procurement.aspect;

import com.enterprise.procurement.admin.entity.OperationLogEntity;
import com.enterprise.procurement.admin.mapper.OperationLogMapper;
import com.enterprise.procurement.dto.ApiResponse;
import com.enterprise.procurement.persistence.mapper.CatalogMapper;
import com.enterprise.procurement.security.AuthCacheService;
import com.enterprise.procurement.security.AuthSnapshot;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 操作日志切面：既要"记得全"，也不能"记日志把业务记挂"。
 */
class OperationLogAspectTest {

    private final OperationLogMapper mapper = mock(OperationLogMapper.class);
    private final AuthCacheService cache = mock(AuthCacheService.class);
    private final CatalogMapper catalog = mock(CatalogMapper.class);
    private final OperationLogAspect aspect = new OperationLogAspect(mapper, cache, catalog);

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    static class Probe {
        @OperationLog(value = "提交采购申请", module = "采购申请")
        public String submit(Long id, String operator) {
            return "ok";
        }

        @OperationLog("会失败的动作")
        public String boom(Long id) {
            throw new IllegalStateException("申请状态已变更");
        }

        /** 创建类接口没有路径变量，业务 id 只能从返回值里拿。 */
        @OperationLog(value = "创建采购申请", module = "采购申请")
        public ApiResponse<?> create(String reason) {
            return ApiResponse.ok("草稿已创建", new Created(88L));
        }
    }

    static class Created {
        private final Long id;

        Created(Long id) {
            this.id = id;
        }

        public Long getId() {
            return id;
        }
    }

    private void login(String username) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, null, List.of()));
    }

    @Test
    @DisplayName("成功调用：操作人、模块、业务 id、耗时、结果全部落库")
    void recordsOperatorModuleBizIdAndCost() throws Throwable {
        login("alice");
        when(cache.get("alice")).thenReturn(new AuthSnapshot(7L, "alice", 0, List.of("ADMIN")));

        var m = Probe.class.getMethod("submit", Long.class, String.class);
        MethodSignature sig = mock(MethodSignature.class);
        when(sig.getMethod()).thenReturn(m);
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        when(pjp.getSignature()).thenReturn(sig);
        when(pjp.getArgs()).thenReturn(new Object[]{42L, "alice"});
        when(pjp.proceed()).thenReturn("ok");

        assertEquals("ok", aspect.record(pjp));

        ArgumentCaptor<OperationLogEntity> captor = ArgumentCaptor.forClass(OperationLogEntity.class);
        verify(mapper).insert(captor.capture());
        OperationLogEntity row = captor.getValue();
        // 改造前这里是 null（三元两支都写了 null）
        assertEquals(7L, row.getOperatorId());
        assertEquals("alice", row.getOperatorName());
        assertEquals("采购申请", row.getModule());
        assertEquals("提交采购申请", row.getAction());
        assertEquals(42L, row.getBizId());
        assertEquals("SUCCESS", row.getResult());
        assertNotNull(row.getCostMs());
        assertTrue(row.getCostMs() >= 0);
    }

    @Test
    @DisplayName("业务抛异常：记 FAIL，异常继续向上抛（不能被切面吞掉）")
    void failedCallIsRecordedAsFail() throws Throwable {
        login("alice");
        when(cache.get("alice")).thenReturn(new AuthSnapshot(7L, "alice", 0, List.of()));

        var m = Probe.class.getMethod("boom", Long.class);
        MethodSignature sig = mock(MethodSignature.class);
        when(sig.getMethod()).thenReturn(m);
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        when(pjp.getSignature()).thenReturn(sig);
        when(pjp.getArgs()).thenReturn(new Object[]{5L});
        when(pjp.proceed()).thenThrow(new IllegalStateException("申请状态已变更"));

        assertThrows(IllegalStateException.class, () -> aspect.record(pjp));

        ArgumentCaptor<OperationLogEntity> captor = ArgumentCaptor.forClass(OperationLogEntity.class);
        verify(mapper).insert(captor.capture());
        assertEquals("FAIL", captor.getValue().getResult());
        assertEquals(5L, captor.getValue().getBizId());
    }

    @Test
    @DisplayName("写日志失败不能影响业务：mapper 抛错时业务返回值照常返回")
    void loggingFailureDoesNotBreakBusiness() throws Throwable {
        login("alice");
        when(cache.get("alice")).thenReturn(new AuthSnapshot(7L, "alice", 0, List.of()));
        // 显式给出实体类型：BaseMapper 还有 insert(Collection) 重载，裸 any() 会二义
        doThrow(new RuntimeException("日志表不可写")).when(mapper).insert(any(OperationLogEntity.class));

        var m = Probe.class.getMethod("submit", Long.class, String.class);
        MethodSignature sig = mock(MethodSignature.class);
        when(sig.getMethod()).thenReturn(m);
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        when(pjp.getSignature()).thenReturn(sig);
        when(pjp.getArgs()).thenReturn(new Object[]{1L, "alice"});
        when(pjp.proceed()).thenReturn("ok");

        assertEquals("ok", assertDoesNotThrow(() -> aspect.record(pjp)));
    }

    @Test
    @DisplayName("创建类接口（无路径变量）：从返回值里取出新建对象 id 作为业务 id")
    void createEndpointsTakeBizIdFromResult() throws Throwable {
        login("alice");
        when(cache.get("alice")).thenReturn(new AuthSnapshot(7L, "alice", 0, List.of()));

        var m = Probe.class.getMethod("create", String.class);
        MethodSignature sig = mock(MethodSignature.class);
        when(sig.getMethod()).thenReturn(m);
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        when(pjp.getSignature()).thenReturn(sig);
        when(pjp.getArgs()).thenReturn(new Object[]{"买十台显示器"});
        when(pjp.proceed()).thenReturn(ApiResponse.ok("草稿已创建", new Created(88L)));

        aspect.record(pjp);

        ArgumentCaptor<OperationLogEntity> captor = ArgumentCaptor.forClass(OperationLogEntity.class);
        verify(mapper).insert(captor.capture());
        assertEquals(88L, captor.getValue().getBizId());
        assertEquals("创建采购申请", captor.getValue().getAction());
    }

    @Test
    @DisplayName("匿名调用（无登录态）：操作人留空但日志照记")
    void anonymousHasNoOperator() throws Throwable {
        var m = Probe.class.getMethod("submit", Long.class, String.class);
        MethodSignature sig = mock(MethodSignature.class);
        when(sig.getMethod()).thenReturn(m);
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        when(pjp.getSignature()).thenReturn(sig);
        when(pjp.getArgs()).thenReturn(new Object[]{9L, null});
        when(pjp.proceed()).thenReturn("ok");

        aspect.record(pjp);

        ArgumentCaptor<OperationLogEntity> captor = ArgumentCaptor.forClass(OperationLogEntity.class);
        verify(mapper).insert(captor.capture());
        assertNull(captor.getValue().getOperatorId());
        assertNull(captor.getValue().getOperatorName());
        assertEquals(9L, captor.getValue().getBizId());
    }
}
