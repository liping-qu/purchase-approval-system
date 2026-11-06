package com.enterprise.procurement.persistence.mapper;

import com.enterprise.procurement.persistence.entity.IdempotencyRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 幂等键读写。
 *
 * <p>复合主键 {@code (idem_key, user_id)} 不走 MyBatis-Plus 的单主键约定，所以手写 SQL。
 * {@link #claim} 的 INSERT 是整套机制的原子点：并发重复请求会阻塞在主键索引上，
 * 等前一个事务提交后拿到 {@code DuplicateKeyException}，再回查即可读到完整记录。</p>
 */
@Mapper
public interface IdempotencyMapper {

    @Insert("INSERT INTO sys_idempotency (idem_key, user_id, endpoint, request_hash, expires_at) "
            + "VALUES (#{idemKey}, #{userId}, #{endpoint}, #{requestHash}, DATE_ADD(NOW(), INTERVAL #{ttlHours} HOUR))")
    int claim(@Param("idemKey") String idemKey,
              @Param("userId") Long userId,
              @Param("endpoint") String endpoint,
              @Param("requestHash") String requestHash,
              @Param("ttlHours") int ttlHours);

    @Select("SELECT idem_key, user_id, endpoint, request_hash, result_id, created_at, expires_at "
            + "FROM sys_idempotency WHERE idem_key = #{idemKey} AND user_id = #{userId}")
    IdempotencyRecord find(@Param("idemKey") String idemKey, @Param("userId") Long userId);

    @Update("UPDATE sys_idempotency SET result_id = #{resultId} "
            + "WHERE idem_key = #{idemKey} AND user_id = #{userId}")
    int complete(@Param("idemKey") String idemKey, @Param("userId") Long userId,
                 @Param("resultId") Long resultId);
}
