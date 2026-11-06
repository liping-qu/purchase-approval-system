package com.enterprise.procurement.persistence.entity;

import java.time.LocalDateTime;

/**
 * 幂等键记录。对应 {@code sys_idempotency}（复合主键，故不继承 MyBatis-Plus 的 BaseMapper 约定，
 * 由 {@link com.enterprise.procurement.persistence.mapper.IdempotencyMapper} 手写 SQL）。
 */
public class IdempotencyRecord {

    private String idemKey;
    private Long userId;
    private String endpoint;
    private String requestHash;
    private Long resultId;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;

    public String getIdemKey() { return idemKey; }
    public void setIdemKey(String v) { idemKey = v; }

    public Long getUserId() { return userId; }
    public void setUserId(Long v) { userId = v; }

    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String v) { endpoint = v; }

    public String getRequestHash() { return requestHash; }
    public void setRequestHash(String v) { requestHash = v; }

    public Long getResultId() { return resultId; }
    public void setResultId(Long v) { resultId = v; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime v) { createdAt = v; }

    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime v) { expiresAt = v; }
}
