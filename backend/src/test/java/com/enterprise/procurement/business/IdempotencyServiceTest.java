package com.enterprise.procurement.business;

import com.enterprise.procurement.exception.StateConflictException;
import com.enterprise.procurement.persistence.entity.IdempotencyRecord;
import com.enterprise.procurement.persistence.mapper.IdempotencyMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 幂等组件单测。
 *
 * <p>重点不是"插了一行"，而是三条分支不能混：</p>
 * <ul>
 *   <li>首次请求 → 占位成功，允许执行业务；</li>
 *   <li>重试/并发重复 → 回放上次结果，<b>绝不再执行业务一次</b>；</li>
 *   <li>同 key 不同内容 → 拒绝，这是客户端 bug，不能当重试放过。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {

    @Mock IdempotencyMapper mapper;

    @InjectMocks IdempotencyService service;

    private IdempotencyRecord record(String hash, Long resultId) {
        IdempotencyRecord r = new IdempotencyRecord();
        r.setIdemKey("key-abc-123");
        r.setUserId(1L);
        r.setEndpoint("CREATE_REQUEST");
        r.setRequestHash(hash);
        r.setResultId(resultId);
        return r;
    }

    @Test
    @DisplayName("没有幂等键：完全跳过，不查库不插库（老客户端兼容）")
    void blankKeyIsNoop() {
        var claim = service.claim(null, 1L, "CREATE_REQUEST", "hash");

        assertThat(claim.replay()).isFalse();
        verifyNoInteractions(mapper);

        service.complete(null, 1L, 10L);
        verifyNoInteractions(mapper);
    }

    @Test
    @DisplayName("键格式不合法：直接 400，不写库")
    void invalidKeyRejected() {
        assertThatThrownBy(() -> service.claim("bad key!", 1L, "CREATE_REQUEST", "hash"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Idempotency-Key");
        verifyNoInteractions(mapper);
    }

    @Test
    @DisplayName("首次请求：占位成功，返回 replay=false 让调用方执行业务")
    void freshClaimProceeds() {
        var claim = service.claim("key-abc-123", 1L, "CREATE_REQUEST", "hash");

        assertThat(claim.replay()).isFalse();
        verify(mapper).claim("key-abc-123", 1L, "CREATE_REQUEST", "hash", IdempotencyService.TTL_HOURS);
    }

    @Test
    @DisplayName("重试命中：返回上次的结果主键，调用方据此回放而不是再执行一次")
    void duplicateWithSameFingerprintReplays() {
        when(mapper.claim(anyString(), anyLong(), anyString(), anyString(), anyInt()))
                .thenThrow(new DuplicateKeyException("uk_idem"));
        when(mapper.find("key-abc-123", 1L)).thenReturn(record("hash", 500L));

        var claim = service.claim("key-abc-123", 1L, "CREATE_REQUEST", "hash");

        assertThat(claim.replay()).isTrue();
        assertThat(claim.resultId()).isEqualTo(500L);
    }

    @Test
    @DisplayName("同一个 key 换了请求内容：拒绝（客户端 bug，不能当重试放过）")
    void duplicateWithDifferentFingerprintConflicts() {
        when(mapper.claim(anyString(), anyLong(), anyString(), anyString(), anyInt()))
                .thenThrow(new DuplicateKeyException("uk_idem"));
        when(mapper.find("key-abc-123", 1L)).thenReturn(record("another-hash", 500L));

        assertThatThrownBy(() -> service.claim("key-abc-123", 1L, "CREATE_REQUEST", "hash"))
                .isInstanceOf(StateConflictException.class)
                .hasMessageContaining("不同的请求内容");
    }

    @Test
    @DisplayName("记录在但结果没写：按处理中拒绝，绝不重复执行业务")
    void duplicateWithoutResultIsInProgress() {
        when(mapper.claim(anyString(), anyLong(), anyString(), anyString(), anyInt()))
                .thenThrow(new DuplicateKeyException("uk_idem"));
        when(mapper.find("key-abc-123", 1L)).thenReturn(record("hash", null));

        assertThatThrownBy(() -> service.claim("key-abc-123", 1L, "CREATE_REQUEST", "hash"))
                .isInstanceOf(StateConflictException.class)
                .hasMessageContaining("正在处理中");
    }

    @Test
    @DisplayName("并发回滚：回查不到记录说明前一个事务已回滚，本次按新请求继续")
    void vanishedRecordMeansPreviousRollback() {
        when(mapper.claim(anyString(), anyLong(), anyString(), anyString(), anyInt()))
                .thenThrow(new DuplicateKeyException("uk_idem"));
        when(mapper.find("key-abc-123", 1L)).thenReturn(null);

        assertThat(service.claim("key-abc-123", 1L, "CREATE_REQUEST", "hash").replay()).isFalse();
    }

    @Test
    @DisplayName("指纹对字段顺序敏感、对空值稳定")
    void fingerprintIsStable() {
        assertThat(IdempotencyService.fingerprint("a", 1))
                .isEqualTo(IdempotencyService.fingerprint("a", 1))
                .hasSize(64);
        assertThat(IdempotencyService.fingerprint("a", 1))
                .isNotEqualTo(IdempotencyService.fingerprint(1, "a"));
        assertThat(IdempotencyService.fingerprint(null, 1)).isNotNull();
    }
}
