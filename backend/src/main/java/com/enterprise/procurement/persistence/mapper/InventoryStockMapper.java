package com.enterprise.procurement.persistence.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper; import com.enterprise.procurement.persistence.entity.InventoryStockEntity; import org.apache.ibatis.annotations.*;
@Mapper public interface InventoryStockMapper extends BaseMapper<InventoryStockEntity> { @Update("UPDATE inventory_stock SET quantity=quantity+#{quantity}, version=version+1 WHERE product_id=#{productId} AND version=#{version}") int increment(@Param("productId") Long productId,@Param("quantity") Integer quantity,@Param("version") Integer version); }
