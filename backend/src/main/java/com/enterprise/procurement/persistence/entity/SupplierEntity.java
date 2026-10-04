package com.enterprise.procurement.persistence.entity;
import com.baomidou.mybatisplus.annotation.*;
@TableName("sys_supplier") public class SupplierEntity { @TableId private Long id; private String name; public Long getId(){return id;} public void setId(Long v){id=v;} public String getName(){return name;} public void setName(String v){name=v;} }
