package com.enterprise.procurement.persistence.entity;
import com.baomidou.mybatisplus.annotation.*;
@TableName("inventory_stock") public class InventoryStockEntity { @TableId private Long productId; private Integer quantity; private Integer version; public Long getProductId(){return productId;} public void setProductId(Long v){productId=v;} public Integer getQuantity(){return quantity;} public void setQuantity(Integer v){quantity=v;} public Integer getVersion(){return version;} public void setVersion(Integer v){version=v;} }
