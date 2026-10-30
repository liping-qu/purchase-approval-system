package com.enterprise.procurement.persistence.entity;

import java.time.LocalDateTime;

/**
 * 库存列表投影行：库存表 + 商品档案。
 *
 * <p>为什么不直接返回 {@link InventoryStockEntity}：前端表格要显示商品名称/分类/单位，
 * 库存表里只有 product_id。改造前接口直出实体，页面除"当前库存"外全是空白，
 * 库存预警也因为 {@code row.minimumStock} 恒为 undefined 而恒为 0 —— 典型的
 * "接口有数据、页面是空的"。</p>
 */
public class InventoryStockRow {

    private Long productId;
    private String productName;
    private String category;
    private String unit;
    private Integer quantity;
    private Integer minimumStock;
    private Integer version;
    private LocalDateTime updatedAt;

    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public Integer getMinimumStock() { return minimumStock; }
    public void setMinimumStock(Integer minimumStock) { this.minimumStock = minimumStock; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
