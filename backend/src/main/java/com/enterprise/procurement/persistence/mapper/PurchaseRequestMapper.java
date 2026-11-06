package com.enterprise.procurement.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.enterprise.procurement.persistence.entity.PurchaseRequestEntity;
import com.enterprise.procurement.persistence.entity.PurchaseRequestRow;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.List;

/**
 * 采购申请读写。
 *
 * <p>实体操作（写、CAS）继续用 MyBatis-Plus；列表/详情这类"要连表拿名称"的读，
 * 用下面的注解 SQL 一次 JOIN 完成，避免逐行点查用户和部门。</p>
 */
@Mapper
public interface PurchaseRequestMapper extends BaseMapper<PurchaseRequestEntity> {

    /** 展示态列清单。抽成常量，四处查询共用，避免某处漏了 JOIN 导致名称变 null。 */
    String ROW_SELECT = """
            SELECT pr.id                     AS id,
                   pr.request_no             AS request_no,
                   pr.reason                 AS reason,
                   pr.applicant_id           AS applicant_id,
                   COALESCE(u.nickname, u.username, CONCAT('用户#', pr.applicant_id)) AS applicant,
                   pr.department_id          AS department_id,
                   COALESCE(d.name, '未分配') AS department,
                   pr.total_amount           AS amount,
                   pr.status                 AS status,
                   pr.created_at             AS create_time
            FROM purchase_request pr
            LEFT JOIN sys_user u ON u.id = pr.applicant_id
            LEFT JOIN sys_department d ON d.id = pr.department_id
            """;

    String KEYWORD_FILTER = """
            <if test="keyword != null and keyword != ''">
              AND (pr.request_no LIKE CONCAT('%', #{keyword}, '%')
                OR pr.reason     LIKE CONCAT('%', #{keyword}, '%')
                OR u.nickname    LIKE CONCAT('%', #{keyword}, '%')
                OR u.username    LIKE CONCAT('%', #{keyword}, '%'))
            </if>
            """;

    @Update("UPDATE purchase_request SET status=#{toStatus}, version=version+1, updated_at=NOW() "
            + "WHERE id=#{id} AND status=#{fromStatus} AND version=#{version}")
    int transition(@Param("id") Long id, @Param("fromStatus") String from, @Param("toStatus") String to,
                   @Param("version") Integer version);

    /**
     * 草稿内容更新：只改业务字段，<b>绝不触碰 status 和 version</b>。
     *
     * <p>改造前 {@code updateDraft} 走 MyBatis-Plus 的 {@code updateById} 全字段覆盖——
     * 实体里的 {@code version} 只是普通列（项目没有 {@code @Version} 乐观锁插件），
     * 一旦编辑期间单据被并发提交成 PENDING，保存会把状态写回 DRAFT、version 写回旧值，
     * 后续所有 CAS 都建立在错误版本上。条件里的 {@code applicant_id + status='DRAFT'}
     * 同时是并发兜底：不再是草稿就一行都改不动，由调用方翻译成 409。</p>
     */
    @Update("UPDATE purchase_request SET reason=#{reason}, total_amount=#{totalAmount}, updated_at=NOW() "
            + "WHERE id=#{id} AND applicant_id=#{applicantId} AND status='DRAFT'")
    int updateDraftFields(@Param("id") Long id, @Param("applicantId") Long applicantId,
                          @Param("reason") String reason, @Param("totalAmount") BigDecimal totalAmount);

    /**
     * 条件删除：只有<b>仍然是本人的草稿</b>才删得掉。
     *
     * <p>改造前"先读后判再无条件 DELETE"——读与删之间单据若被提交，仍然会被连明细
     * 带审批记录一起级联删掉。把状态条件压进 DELETE 本身，竞态窗口就闭合了。</p>
     */
    @Delete("DELETE FROM purchase_request WHERE id=#{id} AND applicant_id=#{applicantId} AND status='DRAFT'")
    int deleteDraftRow(@Param("id") Long id, @Param("applicantId") Long applicantId);

    /** 我的申请列表，支持关键词与状态过滤（此前这两个参数被前端发了、后端完全忽略）。 */
    @Select("<script>" + ROW_SELECT
            + "WHERE pr.applicant_id = #{applicantId}"
            + KEYWORD_FILTER
            + "<if test=\"status != null and status != ''\"> AND pr.status = #{status}</if>"
            + " ORDER BY pr.created_at DESC, pr.id DESC"
            + "</script>")
    List<PurchaseRequestRow> rows(@Param("applicantId") Long applicantId,
                                  @Param("keyword") String keyword,
                                  @Param("status") String status);

    /**
     * 审批待办。
     *
     * <p>{@code departmentId} 为 null 时表示"不限部门"（管理员）；普通负责人由 service
     * 在部门为 null 时直接返回空列表，不会走到这里 —— 把"没部门"翻译成"看全部"
     * 正是原来的越权点。</p>
     */
    @Select("<script>" + ROW_SELECT
            + "WHERE pr.status = 'PENDING'"
            + "<if test=\"departmentId != null\"> AND pr.department_id = #{departmentId}</if>"
            + KEYWORD_FILTER
            + " ORDER BY pr.created_at ASC, pr.id ASC"
            + "</script>")
    List<PurchaseRequestRow> pendingRows(@Param("departmentId") Long departmentId,
                                         @Param("keyword") String keyword);

    /** 单条详情。可见性仍由 service 判定，这里只负责取数。 */
    @Select(ROW_SELECT + " WHERE pr.id = #{id}")
    PurchaseRequestRow rowById(@Param("id") Long id);

    /**
     * 可以下单的申请：已审批通过、且还没有生成订单。
     *
     * <p>用 {@code po.id IS NULL} 而不是让前端自己去两张表里比对——
     * 那需要客户端拉全量订单再过滤，既浪费流量又会因为分页漏掉。
     * 这也顺带让"重复下单"在 UI 层就没有入口，唯一键只作为最后兜底。</p>
     */
    @Select(ROW_SELECT
            + "LEFT JOIN purchase_order po ON po.request_id = pr.id "
            + "WHERE pr.status = 'APPROVED' AND po.id IS NULL "
            + "ORDER BY pr.created_at ASC, pr.id ASC")
    List<PurchaseRequestRow> orderableRows();
}
