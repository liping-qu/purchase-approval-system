package com.enterprise.procurement.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.enterprise.procurement.admin.entity.SysUser;
import com.enterprise.procurement.admin.entity.UserSummaryRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Set;

@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    @Select("select r.code from sys_role r join sys_user_role ur on ur.role_id=r.id where ur.user_id=#{id}")
    Set<String> roleCodes(Long id);

    @Select("select p.code from sys_permission p join sys_role_permission rp on rp.permission_id=p.id "
            + "join sys_user_role ur on ur.role_id=rp.role_id where ur.user_id=#{id}")
    Set<String> permissionCodes(Long id);

    @Select("select * from sys_user where username=#{username} limit 1")
    SysUser findByUsername(String username);

    /**
     * 用户列表：左连部门取名称，子查询用 GROUP_CONCAT 一次取全部角色编码。
     * 整页只发 1 条 SQL，替代"列表 + 每行再查角色"的 N+1 写法。
     */
    @Select("""
            SELECT u.id,
                   u.username,
                   u.nickname,
                   d.name AS department_name,
                   u.status,
                   (SELECT GROUP_CONCAT(r.code ORDER BY r.code)
                      FROM sys_role r
                      JOIN sys_user_role ur ON ur.role_id = r.id
                     WHERE ur.user_id = u.id) AS role_codes
            FROM sys_user u
            LEFT JOIN sys_department d ON d.id = u.department_id
            ORDER BY u.id
            """)
    List<UserSummaryRow> listWithRoles();
}
