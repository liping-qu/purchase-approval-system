package com.enterprise.procurement.service;

import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class AdminService {
  private final AtomicLong ids=new AtomicLong(100); private final Map<Long,Map<String,Object>> departments=new LinkedHashMap<>(), roles=new LinkedHashMap<>(), menus=new LinkedHashMap<>(), configs=new LinkedHashMap<>(), logs=new LinkedHashMap<>();
  public AdminService(){ department("管理部",null); department("采购部",null); department("财务部",null); role("管理员","ADMIN"); role("审批人","APPROVER"); role("采购员","PURCHASER"); menu(null,"系统管理","/admin","Setting","admin"); menu(1L,"用户管理","/admin/users","User","user:manage"); menu(1L,"角色管理","/admin/roles","UserFilled","role:manage"); config("system.name","企业采购与审批管理系统","系统名称"); }
  private Map<String,Object> base(Long id){var m=new LinkedHashMap<String,Object>();m.put("id",id);m.put("createdAt",LocalDateTime.now());m.put("updatedAt",LocalDateTime.now());return m;}
  private void department(String n,Long p){var m=base(ids.incrementAndGet());m.put("name",n);m.put("parentId",p);m.put("status",1);departments.put((Long)m.get("id"),m);} private void role(String n,String c){var m=base(ids.incrementAndGet());m.put("name",n);m.put("code",c);m.put("status",1);roles.put((Long)m.get("id"),m);} private void menu(Long p,String n,String path,String icon,String perm){var m=base(ids.incrementAndGet());m.put("parentId",p);m.put("name",n);m.put("path",path);m.put("icon",icon);m.put("permissionCode",perm);m.put("sortOrder",0);m.put("status",1);menus.put((Long)m.get("id"),m);} private void config(String k,String v,String d){var m=base(ids.incrementAndGet());m.put("configKey",k);m.put("configValue",v);m.put("description",d);configs.put((Long)m.get("id"),m);}
  public Collection<Map<String,Object>> departments(){return departments.values();} public Collection<Map<String,Object>> roles(){return roles.values();} public Collection<Map<String,Object>> menus(){return menus.values();} public Collection<Map<String,Object>> configs(){return configs.values();} public Collection<Map<String,Object>> logs(){return logs.values();}
  public Map<String,Object> add(String type,Map<String,Object> in){var m=base(ids.incrementAndGet());m.putAll(in);m.putIfAbsent("status",1);if(type.equals("department"))departments.put((Long)m.get("id"),m);else if(type.equals("role"))roles.put((Long)m.get("id"),m);else if(type.equals("menu"))menus.put((Long)m.get("id"),m);else if(type.equals("config"))configs.put((Long)m.get("id"),m);return m;}
  public void delete(String type,Long id){Map<Long,Map<String,Object>> x=type.equals("department")?departments:type.equals("role")?roles:type.equals("menu")?menus:configs;x.remove(id);}
  public void log(String operator,String action,String resource){var m=base(ids.incrementAndGet());m.put("operator",operator);m.put("action",action);m.put("resource",resource);logs.put((Long)m.get("id"),m);}
}
