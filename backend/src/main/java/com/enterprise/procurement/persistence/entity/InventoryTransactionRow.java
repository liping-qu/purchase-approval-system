package com.enterprise.procurement.persistence.entity;

import java.time.LocalDateTime;

/**
 * 库存流水列表行（含商品名与操作人姓名）。
 *
 * <p>流水表本身只存 {@code product_id} / {@code operator_id}，页面要的是商品名和人名。
 * 让前端拿着 id 去字典里查是行不通的：商品可能已下架、操作人可能已停用，
 * 字典里查不到就变成空白列。这里由 SQL 一次 JOIN 带出，并提供兜底文案。</p>
 */
public class InventoryTransactionRow {

    private Long id;
    private Long productId;
    private String productName;
    private Integer quantity;
    private String type;
    private String referenceNo;
    private String operator;
    private LocalDateTime createTime;

    public Long getId() { return id; }
    public void setId(Long v) { id = v; }

    public Long getProductId() { return productId; }
    public void setProductId(Long v) { productId = v; }

    public String getProductName() { return productName; }
    public void setProductName(String v) { productName = v; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer v) { quantity = v; }

    public String getType() { return type; }
    public void setType(String v) { type = v; }

    public String getReferenceNo() { return referenceNo; }
    public void setReferenceNo(String v) { referenceNo = v; }

    public String getOperator() { return operator; }
    public void setOperator(String v) { operator = v; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime v) { createTime = v; }
}
