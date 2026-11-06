package com.enterprise.procurement.persistence.entity;

import java.time.LocalDateTime;

/**
 * 审批留痕行。
 *
 * <p>改造前 {@code approval_record} 只写不读：{@code ApprovalRecordMapper.byRequest()}
 * 写完之后没有任何接口调用它（死代码），于是页面上永远看不到"谁在什么时候批的、
 * 批注写了什么"。审计断链不是没记，是记了没展示。</p>
 */
public class ApprovalRecordRow {

    private Long id;
    private String approver;
    private String action;
    private String comment;
    private LocalDateTime createTime;

    public Long getId() { return id; }
    public void setId(Long v) { id = v; }

    public String getApprover() { return approver; }
    public void setApprover(String v) { approver = v; }

    public String getAction() { return action; }
    public void setAction(String v) { action = v; }

    public String getComment() { return comment; }
    public void setComment(String v) { comment = v; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime v) { createTime = v; }
}
