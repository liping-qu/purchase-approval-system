package com.enterprise.procurement.persistence.mapper;

import com.enterprise.procurement.persistence.entity.ProductEntity;
import com.enterprise.procurement.persistence.entity.SupplierEntity;
import com.enterprise.procurement.persistence.entity.SystemUserRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 只读的目录型查询（商品、供应商、登录用户、角色）。
 *
 * <p>全部为轻量点查，交由 MyBatis 注解 SQL 完成；不继承 BaseMapper 是刻意的——
 * 这些表在业务代码里只允许"按 id / 按用户名"读取，避免有人顺手写出全表 update。</p>
 */
@Mapper
public interface CatalogMapper {

    @Select("SELECT id, name, reference_price, stock FROM sys_product WHERE id = #{id} AND active = 1")
    ProductEntity product(@Param("id") Long id);

    /** 可采购商品列表，供"新建采购申请"的商品选择器使用。 */
    @Select("SELECT id, name, reference_price, stock FROM sys_product WHERE active = 1 ORDER BY id")
    List<ProductEntity> products();

    @Select("SELECT id, name FROM sys_supplier WHERE id = #{id} AND active = 1")
    SupplierEntity supplier(@Param("id") Long id);

    @Select("SELECT u.id, u.username, u.password, u.nickname AS display_name, u.department_id, "
            + "d.name AS department_name, u.token_version "
            + "FROM sys_user u LEFT JOIN sys_department d ON d.id = u.department_id "
            + "WHERE u.username = #{username} AND u.status = 1")
    SystemUserRow user(@Param("username") String username);

    @Select("SELECT r.code FROM sys_role r JOIN sys_user_role ur ON ur.role_id = r.id "
            + "WHERE ur.user_id = #{userId}")
    List<String> roleCodes(@Param("userId") Long userId);
}
