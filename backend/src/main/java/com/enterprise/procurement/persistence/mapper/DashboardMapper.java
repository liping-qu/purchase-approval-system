package com.enterprise.procurement.persistence.mapper;

import com.enterprise.procurement.persistence.entity.RecentRequestRow;
import com.enterprise.procurement.persistence.entity.StatusCountRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 工作台统计查询。
 *
 * <p>设计意图：工作台是纯读的聚合视图，用 4 条轻量聚合 SQL + 1 条 top-N 查询完成，
 * 不做"查全表再在内存里 count"——那正是被删掉的内存实现的做法。
 * 后续 P6 阶段会对这些 SQL 执行 EXPLAIN，并据此补索引。</p>
 */
@Mapper
public interface DashboardMapper {

    @Select("SELECT status, COUNT(*) AS total FROM purchase_request GROUP BY status")
    List<StatusCountRow> statusCounts();

    @Select("SELECT COUNT(*) FROM purchase_order")
    long orderCount();

    @Select("SELECT COUNT(*) FROM sys_supplier WHERE active = 1")
    long supplierCount();

    @Select("SELECT COUNT(*) FROM sys_product WHERE active = 1")
    long productCount();

    /**
     * 零库存商品数。注意：当前表结构没有"最低库存阈值"字段，
     * 所以这里只统计真正没有库存的商品，不伪造一个"库存预警"指标。
     * 等 P2 引入 min_quantity 后再升级为"低于阈值"的语义。
     */
    @Select("SELECT COUNT(*) FROM inventory_stock WHERE quantity <= 0")
    long zeroStockCount();

    @Select("""
            SELECT pr.id            AS id,
                   pr.request_no    AS request_no,
                   pr.reason        AS reason,
                   COALESCE(u.nickname, u.username) AS applicant,
                   pr.total_amount  AS amount,
                   pr.status        AS status,
                   pr.created_at    AS create_time
            FROM purchase_request pr
            LEFT JOIN sys_user u ON u.id = pr.applicant_id
            ORDER BY pr.created_at DESC, pr.id DESC
            LIMIT 5
            """)
    List<RecentRequestRow> recentRequests();
}
