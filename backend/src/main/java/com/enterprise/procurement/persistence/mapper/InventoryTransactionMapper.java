package com.enterprise.procurement.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.enterprise.procurement.persistence.entity.InventoryTransactionEntity;
import com.enterprise.procurement.persistence.entity.InventoryTransactionRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;

@Mapper
public interface InventoryTransactionMapper extends BaseMapper<InventoryTransactionEntity> {

    /**
     * 流水分页查询。
     *
     * <p>改造前是 {@code SELECT * FROM inventory_transaction ORDER BY created_at DESC}
     * —— 没有 LIMIT，也没有任何过滤条件。流水是只增不删的表，跑上一年就是几十万行，
     * 一次请求会把整表读进内存。这里把过滤与分页都下推到 SQL。</p>
     *
     * <p>商品名与操作人用 LEFT JOIN 带出：商品可能已下架、用户可能已删除，
     * 用 COALESCE 兜底成"商品#12"总比空白列可读。</p>
     */
    @Select("""
            <script>
            SELECT t.id             AS id,
                   t.product_id     AS product_id,
                   COALESCE(p.name, CONCAT('商品#', t.product_id))              AS product_name,
                   t.quantity       AS quantity,
                   t.type           AS type,
                   t.reference_no   AS reference_no,
                   COALESCE(u.nickname, u.username, CONCAT('用户#', t.operator_id)) AS operator,
                   t.created_at     AS create_time
            FROM inventory_transaction t
            LEFT JOIN sys_product p ON p.id = t.product_id
            LEFT JOIN sys_user    u ON u.id = t.operator_id
            WHERE 1 = 1
            <if test="productId != null"> AND t.product_id = #{productId}</if>
            <if test="type != null and type != ''"> AND t.type = #{type}</if>
            <if test="from != null"> AND t.created_at &gt;= #{from}</if>
            <if test="to != null"> AND t.created_at &lt; DATE_ADD(#{to}, INTERVAL 1 DAY)</if>
            ORDER BY t.created_at DESC, t.id DESC
            </script>
            """)
    IPage<InventoryTransactionRow> pageRows(Page<?> page,
                                            @Param("productId") Long productId,
                                            @Param("type") String type,
                                            @Param("from") LocalDate from,
                                            @Param("to") LocalDate to);
}
