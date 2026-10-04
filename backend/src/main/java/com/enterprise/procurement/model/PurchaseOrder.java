package com.enterprise.procurement.model;

import java.math.BigDecimal; import java.time.LocalDateTime; import java.util.List;

public class PurchaseOrder {
    private Long id; private String orderNo; private Long applicationId; private Long supplierId; private String supplierName; private BigDecimal totalAmount; private Enums.OrderStatus status=Enums.OrderStatus.CREATED; private LocalDateTime createdAt=LocalDateTime.now(); private List<PurchaseApplication.Item> items;
    public Long getId(){return id;} public void setId(Long v){id=v;} public String getOrderNo(){return orderNo;} public void setOrderNo(String v){orderNo=v;} public Long getApplicationId(){return applicationId;} public void setApplicationId(Long v){applicationId=v;} public Long getSupplierId(){return supplierId;} public void setSupplierId(Long v){supplierId=v;} public String getSupplierName(){return supplierName;} public void setSupplierName(String v){supplierName=v;} public BigDecimal getTotalAmount(){return totalAmount;} public void setTotalAmount(BigDecimal v){totalAmount=v;} public Enums.OrderStatus getStatus(){return status;} public void setStatus(Enums.OrderStatus v){status=v;} public LocalDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(LocalDateTime v){createdAt=v;} public List<PurchaseApplication.Item> getItems(){return items;} public void setItems(List<PurchaseApplication.Item> v){items=v;}
}
