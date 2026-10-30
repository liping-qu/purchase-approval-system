package com.enterprise.procurement.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
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

    /** 采购/仓库/管理员：全部订单。 */
    @Select(ROW_SELECT + " ORDER BY o.created_at DESC, o.id DESC")
    List<PurchaseOrderRow> rows();

    /**
     * 其他人：只看与自己相关的订单。
     *
     * <p>改造前是先查全量、再逐行 {@code selectById(requestId)} 判断——订单有 N 条就多 N 次查询，
     * 数据量一大就退化。把归属条件下推到 SQL，一次扫描解决；部门为 null 时只按本人过滤，
     * 不会出现"两个 null 相等"的放行。</p>
     */
    @Select("<script>" + ROW_SELECT
            + "WHERE pr.applicant_id = #{applicantId}"
            + "<if test=\"departmentId != null\"> OR pr.department_id = #{departmentId}</if>"
            + " ORDER BY o.created_at DESC, o.id DESC"
            + "</script>")
    List<PurchaseOrderRow> rowsFor(@Param("applicantId") Long applicantId,
                                   @Param("departmentId") Long departmentId);

    @Select(ROW_SELECT + " WHERE o.id = #{id}")
    PurchaseOrderRow rowById(@Param("id") Long id);
}
