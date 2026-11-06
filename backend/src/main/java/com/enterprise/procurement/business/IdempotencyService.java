package com.enterprise.procurement.business;

import com.enterprise.procurement.exception.StateConflictException;
import com.enterprise.procurement.persistence.entity.IdempotencyRecord;
import com.enterprise.procurement.persistence.mapper.IdempotencyMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Pattern;

/**
 * 接口幂等：把"同一次用户意图"从"多个 HTTP 请求"里识别出来。
 *
 * <p>解决的问题很具体：网络超时后用户重试、或者手快连点两次，请求会真的发两遍。
 * 状态机与唯一键能挡住"已审批再审批""同申请再下单"，但挡不住
 * <b>创建申请</b>（同一个用户合法地可以有两张相同事由的单）和
 * <b>手工调库存</b>（每次生成新单号，唯一键兜不住）。</p>
 *
 * <p>三个关键取舍：</p>
 * <ul>
 *   <li><b>幂等键由客户端生成并由客户端负责重试时复用</b>——服务端无法判断
 *       "这次请求是不是上一次的重试"，只有发起方知道。key 只在"同一次操作"内保持不变。</li>
 *   <li><b>占位与业务同事务</b>：{@link #claim} 的 INSERT 加入调用方事务，
 *       业务回滚时占位一起回滚，用户重试仍是干净的；不会出现"键被占了但单没建成"。</li>
 *   <li><b>并发重复请求不是"先查后插"</b>：直接 INSERT 撞主键，让 InnoDB 的行锁
 *       把并发串起来，避免"两个请求都查不到然后都插入"的经典竞态。</li>
 * </ul>
 */
@Service
public class IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);

    /** 24 小时：覆盖"用户第二天回来重试"的极端情况，又不至于让表无限增长。 */
    static final int TTL_HOURS = 24;

    /** 只允许 URL 安全字符，长度 8–64：太短容易撞、太长会超列宽。 */
    private static final Pattern KEY_PATTERN = Pattern.compile("[A-Za-z0-9_-]{8,64}");

    private final IdempotencyMapper mapper;

    public IdempotencyService(IdempotencyMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 占位结果。
     *
     * @param replay    true 表示这个 key 之前已经成功处理过，调用方应直接回放而不是再执行一次
     * @param resultId  上次处理产生的业务主键（创建类接口用它回放；无返回值的操作为 null）
     */
    public record Claim(boolean replay, Long resultId) {
        static Claim fresh() { return new Claim(false, null); }
    }

    /**
     * 占位或识别重放。
     *
     * <p>调用方必须在<b>已有事务</b>里调用（服务方法上有 {@code @Transactional}），
     * 否则占位会独立提交，业务失败后 key 仍被占用。</p>
     *
     * @param idemKey     客户端传入的幂等键；为空表示不做幂等（兼容老客户端）
     * @param userId      用户作用域
     * @param endpoint    业务标识，如 {@code CREATE_REQUEST}
     * @param fingerprint 请求内容指纹，用于识别"同 key 换了请求体"的客户端 bug
     */
    public Claim claim(String idemKey, Long userId, String endpoint, String fingerprint) {
        if (idemKey == null || idemKey.isBlank()) {
            return Claim.fresh();
        }
        if (!KEY_PATTERN.matcher(idemKey).matches()) {
            throw new IllegalArgumentException("Idempotency-Key 必须是 8–64 位字母、数字、下划线或短横线");
        }

        try {
            mapper.claim(idemKey, userId, endpoint, fingerprint, TTL_HOURS);
            return Claim.fresh();
        } catch (DuplicateKeyException e) {
            // 并发或重试：并发时这里会先阻塞在主键锁上，等前一个事务结束后才走到此处，
            // 因此下面的回查一定能读到"已提交"的完整记录。
            IdempotencyRecord existing = mapper.find(idemKey, userId);
            if (existing == null) {
                // 前一个事务刚刚回滚：占位已消失，本次按新请求继续是安全的
                return Claim.fresh();
            }
            if (!existing.getRequestHash().equals(fingerprint)) {
                throw new StateConflictException("同一幂等键被用于不同的请求内容，已拒绝");
            }
            if (existing.getResultId() == null) {
                // 记录在、结果还没写：只可能是同事务尚未提交（理论上已被锁阻塞，走不到这里），
                // 保守起见按"处理中"告诉客户端稍后重试，绝不重复执行一次业务。
                throw new StateConflictException("相同请求正在处理中，请稍后重试");
            }
            log.debug("幂等命中，回放结果: endpoint={}, key={}, resultId={}", endpoint, idemKey, existing.getResultId());
            return new Claim(true, existing.getResultId());
        }
    }

    /**
     * 记录业务结果主键。
     *
     * <p>创建类接口必须调用，否则重试时只能得到"处理中"；无返回值的操作传 null 即可，
     * 表示"这个 key 已经成功执行过"。</p>
     */
    public void complete(String idemKey, Long userId, Long resultId) {
        if (idemKey == null || idemKey.isBlank()) {
            return;
        }
        mapper.complete(idemKey, userId, resultId);
    }

    /** 请求内容指纹：把参与"是否同一请求"判断的字段拼起来做 SHA-256。 */
    public static String fingerprint(Object... parts) {
        StringBuilder sb = new StringBuilder();
        for (Object p : parts) {
            sb.append(p == null ? "" : p.toString()).append('\u0001');
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(sb.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JVM 缺少 SHA-256", e);
        }
    }
}
