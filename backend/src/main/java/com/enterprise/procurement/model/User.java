package com.enterprise.procurement.model;

import java.util.Set;

public class User {
    private Long id; private String username; private String password; private String displayName; private String department; private Set<Enums.UserRole> roles; private boolean enabled = true;
    public User() {}
    public User(Long id, String username, String password, String displayName, String department, Set<Enums.UserRole> roles) { this.id=id; this.username=username; this.password=password; this.displayName=displayName; this.department=department; this.roles=roles; }
    public Long getId(){return id;} public void setId(Long v){id=v;} public String getUsername(){return username;} public void setUsername(String v){username=v;} public String getPassword(){return password;} public void setPassword(String v){password=v;} public String getDisplayName(){return displayName;} public void setDisplayName(String v){displayName=v;} public String getDepartment(){return department;} public void setDepartment(String v){department=v;} public Set<Enums.UserRole> getRoles(){return roles;} public void setRoles(Set<Enums.UserRole> v){roles=v;} public boolean isEnabled(){return enabled;} public void setEnabled(boolean v){enabled=v;}
}
