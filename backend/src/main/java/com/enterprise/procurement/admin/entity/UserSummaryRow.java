package com.enterprise.procurement.admin.entity;

/**
 * 用户列表投影行：一次查询同时拿到部门名称与角色编码（GROUP_CONCAT），
 * 避免"列表 5 个用户、每个用户再查 2 次角色/权限"的 N+1 问题。
 */
public class UserSummaryRow {
    private Long id;
    private String username;
    private String nickname;
    private String departmentName;
    private Integer status;
    private String roleCodes;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public String getDepartmentName() { return departmentName; }
    public void setDepartmentName(String departmentName) { this.departmentName = departmentName; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public String getRoleCodes() { return roleCodes; }
    public void setRoleCodes(String roleCodes) { this.roleCodes = roleCodes; }
}
