package com.enterprise.procurement.persistence.entity;
import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

/**
 * 库存（按商品聚合一行）。
 *
 * <p>{@code minQuantity} / {@code updatedAt} 由 V8 迁移补上：前者给前端"低库存"标红用，
 * 后者记录最后一次变动时间（DB 侧 ON UPDATE CURRENT_TIMESTAMP 自动维护，业务代码不用记）。</p>
 */
@TableName("inventory_stock") public class InventoryStockEntity { @TableId private Long productId; private Integer quantity; private Integer version; private Integer minQuantity; private LocalDateTime updatedAt; public Long getProductId(){return productId;} public void setProductId(Long v){productId=v;} public Integer getQuantity(){return quantity;} public void setQuantity(Integer v){quantity=v;} public Integer getVersion(){return version;} public void setVersion(Integer v){version=v;} public Integer getMinQuantity(){return minQuantity;} public void setMinQuantity(Integer v){minQuantity=v;} public LocalDateTime getUpdatedAt(){return updatedAt;} public void setUpdatedAt(LocalDateTime v){updatedAt=v;} }
