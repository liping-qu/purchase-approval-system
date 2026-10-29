package com.enterprise.procurement.persistence.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 业务单号号段。
 *
 * <p>用 MySQL 的 {@code LAST_INSERT_ID(expr)} 实现"原子自增并回读"：
 * 该函数会把参数写入当前<b>连接</b>的 last_insert_id，随后 {@code SELECT LAST_INSERT_ID()}
 * 就能拿到本次分配值。因此这两条语句必须在同一个事务（同一个连接）里执行，
 * 所以调用方 {@link com.enterprise.procurement.business.DocumentNoGenerator}
 * 用 {@code Propagation.MANDATORY} 强制要求已存在事务。</p>
 */
@Mapper
public interface SequenceMapper {

    @Update("INSERT INTO sys_sequence (name, seq_date, next_val) VALUES (#{name}, CURDATE(), LAST_INSERT_ID(1)) "
            + "ON DUPLICATE KEY UPDATE next_val = LAST_INSERT_ID(next_val + 1)")
    void bump(@Param("name") String name);

    @Select("SELECT LAST_INSERT_ID()")
    long currentValue();
}
