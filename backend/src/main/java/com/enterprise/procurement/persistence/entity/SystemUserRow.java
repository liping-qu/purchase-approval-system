package com.enterprise.procurement.persistence.entity;

/**
 * 鉴权用的用户投影。
 *
 * <p>只包含认证与授权所需的字段。注意 {@code password} 仅供 {@code AuthController} 做
 * BCrypt 校验，**任何接口都不得直接返回本对象**——{@code /api/auth/me} 曾经就是因为
 * 直接序列化它，把 BCrypt 哈希泄露给了前端。</p>
 */
public class SystemUserRow {
    private Long id; private String username; private String password; private String displayName;
    private Long departmentId; private String departmentName; private Integer tokenVersion;
    public Long getId(){return id;} public void setId(Long v){id=v;}
    public String getUsername(){return username;} public void setUsername(String v){username=v;}
    public String getPassword(){return password;} public void setPassword(String v){password=v;}
    public String getDisplayName(){return displayName;} public void setDisplayName(String v){displayName=v;}
    public Long getDepartmentId(){return departmentId;} public void setDepartmentId(Long v){departmentId=v;}
    public String getDepartmentName(){return departmentName;} public void setDepartmentName(String v){departmentName=v;}
    public Integer getTokenVersion(){return tokenVersion;} public void setTokenVersion(Integer v){tokenVersion=v;}
}
