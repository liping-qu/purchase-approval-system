package com.enterprise.procurement.model;

import java.math.BigDecimal;

public class Product {
    private Long id; private String sku; private String name; private String category; private String unit; private BigDecimal referencePrice; private Integer stock; private boolean active = true;
    public Product() {}
    public Product(Long id,String sku,String name,String category,String unit,BigDecimal price,Integer stock){this.id=id;this.sku=sku;this.name=name;this.category=category;this.unit=unit;this.referencePrice=price;this.stock=stock;}
    public Long getId(){return id;} public void setId(Long v){id=v;} public String getSku(){return sku;} public void setSku(String v){sku=v;} public String getName(){return name;} public void setName(String v){name=v;} public String getCategory(){return category;} public void setCategory(String v){category=v;} public String getUnit(){return unit;} public void setUnit(String v){unit=v;} public BigDecimal getReferencePrice(){return referencePrice;} public void setReferencePrice(BigDecimal v){referencePrice=v;} public Integer getStock(){return stock;} public void setStock(Integer v){stock=v;} public boolean isActive(){return active;} public void setActive(boolean v){active=v;}
}
