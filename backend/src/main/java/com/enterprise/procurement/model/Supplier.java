package com.enterprise.procurement.model;

public class Supplier {
    private Long id; private String code; private String name; private String contact; private String phone; private String address; private boolean active = true;
    public Supplier() {} public Supplier(Long id,String code,String name,String contact,String phone,String address){this.id=id;this.code=code;this.name=name;this.contact=contact;this.phone=phone;this.address=address;}
    public Long getId(){return id;} public void setId(Long v){id=v;} public String getCode(){return code;} public void setCode(String v){code=v;} public String getName(){return name;} public void setName(String v){name=v;} public String getContact(){return contact;} public void setContact(String v){contact=v;} public String getPhone(){return phone;} public void setPhone(String v){phone=v;} public String getAddress(){return address;} public void setAddress(String v){address=v;} public boolean isActive(){return active;} public void setActive(boolean v){active=v;}
}
