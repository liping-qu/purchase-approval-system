package com.enterprise.procurement.persistence.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper; import com.enterprise.procurement.persistence.entity.InventoryTransactionEntity; import org.apache.ibatis.annotations.*; import java.util.*;
@Mapper public interface InventoryTransactionMapper extends BaseMapper<InventoryTransactionEntity> { @Select("SELECT * FROM inventory_transaction ORDER BY created_at DESC") List<InventoryTransactionEntity> recent(); }
