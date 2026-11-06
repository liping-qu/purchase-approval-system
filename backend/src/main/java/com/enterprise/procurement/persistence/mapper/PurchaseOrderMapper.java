package com.enterprise.procurement.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.enterprise.procurement.persistence.entity.PurchaseOrderEntity;
import com.enterprise.procurement.persistence.entity.PurchaseOrderRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface PurchaseOrderMapper extends BaseMapper<PurchaseOrderEntity> {

    String ROW_SELECT = """
            SELECT o.id                                 AS id,
                   o.order_no                           AS order_no,
                   o.request_id                         AS request_id,
                   pr.request_no                        AS request_no,
                   pr.reason                            AS reason,
                   o.supplier_id                        AS supplier_id,
                   COALESCE(s.name, CONCAT('供应商#', o.supplier_id)) AS supplier,
                   o.total_amount                       AS amount,
                   o.status                             AS status,
                   o.created_at                         AS create_time
            FROM purchase_order o
            LEFT JOIN purchase_request pr ON pr.id = o.request_id
            LEFT JOIN sys_supplier s ON s.id = o.supplier_id
            """;

    @Update("UPDATE purchase_order SET status=#{toStatus}, version=version+1, updated_at=NOW() "
            + "WHERE id=#{id} AND status=#{fromStatus} AND version=#{version}")
    int transition(@Param("id") Long id, @Param("fromStatus") String from, @Param("toStatus") String to,
                   @Param("version") Integer version);

    @Select(ROW_SELECT + " WHERE o.id = #{id}")
    PurchaseOrderRow rowById(@Param("id") Long id);

    /**
     * 列表过滤条件。抽成常量给两条分页查询共用：特权角色看全部、其他人只看与自己相关的，
     * 两者的差别只在 WHERE 的归属部分，过滤与排序必须完全一致，否则同一个搜索在
     * 不同角色下结果会莫名其妙地不同。
     */
    String LIST_FILTER = """
            <if test="status != null and status != ''"> AND o.status = #{status}</if>
            <if test="supplierId != null"> AND o.supplier_id = #{supplierId}</if>
            <if test="keyword != null and keyword != ''">
              AND (o.order_no  LIKE CONCAT('%', #{keyword}, '%')
                OR pr.request_no LIKE CONCAT('%', #{keyword}, '%')
                OR pr.reason    LIKE CONCAT('%', #{keyword}, '%')
                OR s.name       LIKE CONCAT('%', #{keyword}, '%'))
            </if>
            """;

    /**
     * 分页：采购/仓库/管理员看全部。
     *
     * <p>改造前这个接口<b>没有分页也没有过滤</b>，返回全表 —— 订单攒到几千条时，
     * 页面首屏要把所有行都传下来。现在把 LIMIT 交给分页插件，条件下推到 SQL。</p>
     */
    @Select("<script>" + ROW_SELECT + " WHERE 1 = 1" + LIST_FILTER
            + " ORDER BY o.created_at DESC, o.id DESC</script>")
    IPage<PurchaseOrderRow> pageAll(Page<?> page,
                                    @Param("status") String status,
                                    @Param("keyword") String keyword,
                                    @Param("supplierId") Long supplierId);

    /** 分页：其他人只看本人或本部门的订单。归属条件同样下推，不做"查全量再过滤"。 */
    @Select("<script>" + ROW_SELECT
            + " WHERE pr.applicant_id = #{applicantId}"
            + "<if test=\"departmentId != null\"> OR pr.department_id = #{departmentId}</if>"
            + LIST_FILTER
            + " ORDER BY o.created_at DESC, o.id DESC</script>")
    IPage<PurchaseOrderRow> pageFor(Page<?> page,
                                    @Param("applicantId") Long applicantId,
                                    @Param("departmentId") Long departmentId,
                                    @Param("status") String status,
                                    @Param("keyword") String keyword,
                                    @Param("supplierId") Long supplierId);
}
