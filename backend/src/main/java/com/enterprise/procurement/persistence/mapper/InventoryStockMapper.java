package com.enterprise.procurement.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.enterprise.procurement.persistence.entity.InventoryStockEntity;
import com.enterprise.procurement.persistence.entity.InventoryStockRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface InventoryStockMapper extends BaseMapper<InventoryStockEntity> {

    /**
     * "不存在则建、已存在则累加"，一条语句完成。
     *
     * <p>改造前是 {@code selectById -> if (null) insert -> CAS update} 三步：
     * 两个并发请求同时发现库存行不存在，就会同时 insert，第二个直接撞主键报 500。</p>
     *
     * <p>为什么这里用 upsert 而不是继续用版本号 CAS：入库是<b>纯增量</b>操作，
     * 新值只由旧值和增量决定，不需要在应用层基于旧值做判断，交给数据库的
     * 行锁 + 自增语义即可，天然原子，还省掉一次 SELECT 往返。
     * 版本号依然递增，用于让"基于旧值做判断"的读方（例如前端乐观刷新）能察觉变化。
     * CAS 仍然保留给真正需要"读-判断-写"的地方，比如单据的状态流转。</p>
     *
     * <p>{@code reference_no + product_id + type} 上有唯一约束作为最后一道幂等兜底，
     * 见 V6 迁移。</p>
     */
    @Update("INSERT INTO inventory_stock (product_id, quantity, version) VALUES (#{productId}, #{quantity}, 0) "
            + "ON DUPLICATE KEY UPDATE quantity = quantity + VALUES(quantity), version = version + 1")
    int upsertAndAdd(@Param("productId") Long productId, @Param("quantity") Integer quantity);

    /**
     * 出库：把"够不够扣"交给数据库判断，一条语句完成 检查 + 扣减。
     *
     * <p>为什么不是先 {@code selectById} 判断 {@code quantity >= n} 再 update：
     * 两个并发出库会同时读到"够"，然后各自扣减，最终库存变成负数。
     * 现在 {@code WHERE quantity >= n} 与扣减在同一个行锁里完成，
     * 返回 0 就说明没扣到 —— 这一行是"抢输了"，业务层据此抛"库存不足"。</p>
     */
    @Update("UPDATE inventory_stock SET quantity = quantity - #{quantity}, version = version + 1 "
            + "WHERE product_id = #{productId} AND quantity >= #{quantity}")
    int deduct(@Param("productId") Long productId, @Param("quantity") Integer quantity);

    /**
     * 库存列表：库存表左连商品档案。
     *
     * <p>用 LEFT JOIN 而不是 INNER JOIN：商品万一被下架（active=0）甚至被物理删掉，
     * 库存行仍然要看得见——看不见的库存比显示成"商品#12"更危险。</p>
     */
    @Select("SELECT s.product_id            AS product_id, "
            + "       COALESCE(p.name, CONCAT('商品#', s.product_id)) AS product_name, "
            + "       p.category              AS category, "
            + "       p.unit                  AS unit, "
            + "       s.quantity              AS quantity, "
            + "       s.min_quantity          AS minimum_stock, "
            + "       s.version               AS version, "
            + "       s.updated_at            AS updated_at "
            + "FROM inventory_stock s LEFT JOIN sys_product p ON p.id = s.product_id "
            + "ORDER BY s.quantity ASC, s.product_id ASC")
    List<InventoryStockRow> stockRows();
}
