package com.enterprise.procurement.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.enterprise.procurement.persistence.entity.InventoryStockEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

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
}
