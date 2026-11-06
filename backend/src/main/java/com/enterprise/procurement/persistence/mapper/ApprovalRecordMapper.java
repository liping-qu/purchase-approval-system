package com.enterprise.procurement.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.enterprise.procurement.persistence.entity.ApprovalRecordEntity;
import com.enterprise.procurement.persistence.entity.ApprovalRecordRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ApprovalRecordMapper extends BaseMapper<ApprovalRecordEntity> {

    /**
     * 审批留痕（展示态）。
     *
     * <p>按时间<b>正序</b>：页面上的时间线要从"提交"往下读到"最近一次审批"，
     * 倒序会让读者从结果往回找原因。原来的 {@code byRequest} 是倒序且无人调用，
     * 现在改成给时间线用的正序查询。</p>
     *
     * <p>审批人用 JOIN 带出姓名，避免前端拿 id 去用户字典里碰运气——
     * 审批人可能已停用甚至已删除，字典里查不到就会显示成空白。</p>
     */
    @Select("""
            SELECT a.id          AS id,
                   COALESCE(u.nickname, u.username, CONCAT('用户#', a.approver_id)) AS approver,
                   a.action      AS action,
                   a.comment     AS comment,
                   a.created_at  AS create_time
            FROM approval_record a
            LEFT JOIN sys_user u ON u.id = a.approver_id
            WHERE a.request_id = #{requestId}
            ORDER BY a.created_at ASC, a.id ASC
            """)
    List<ApprovalRecordRow> rowsByRequest(@Param("requestId") Long requestId);
}
