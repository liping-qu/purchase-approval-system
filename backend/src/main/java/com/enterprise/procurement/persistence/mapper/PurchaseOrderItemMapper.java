package com.enterprise.procurement.persistence.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper; import com.enterprise.procurement.persistence.entity.PurchaseOrderItemEntity; import org.apache.ibatis.annotations.*; import java.util.*;
@Mapper public interface PurchaseOrderItemMapper extends BaseMapper<PurchaseOrderItemEntity> { @Select("SELECT * FROM purchase_order_item WHERE order_id=#{orderId}") List<PurchaseOrderItemEntity> byOrder(@Param("orderId") Long id); }
