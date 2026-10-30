package com.enterprise.procurement.persistence.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 采购申请列表/详情的"展示态"投影。
 *
 * <p>存在的理由：{@code purchase_request} 表里只有 {@code applicant_id} 和
 * {@code department_id}，没有任何名称字段。以前列表接口把实体直出，前端按
 * {@code applicant} / {@code department} / {@code amount} / {@code createTime} 取值，
 * 于是每一列都是 undefined —— 页面看起来有表格、有表头，但一格数据都没有。</p>
 *
 * <p>两种修法：让前端逐行再查用户和部门（N+1），或者在这里一次 JOIN 拿全。
 * 选后者：列表固定是一屏 10~20 行，一条 SQL 拿完比 40 次点查便宜两个数量级，
 * 而且"名称"本来就是读模型的一部分，不该让每个客户端各自拼装。</p>
 *
 * <p>{@code applicantId} / {@code departmentId} 不返回给前端，只在服务端做归属判断用，
 * 这样鉴权不必为了拿两个 id 再查一次实体。</p>
 */
public class PurchaseRequestRow {
    private Long id;
    private String requestNo;
    private String reason;
    private String applicant;
    private String department;
    private BigDecimal amount;
    private String status;
    private LocalDateTime createTime;
    private Long applicantId;
    private Long departmentId;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getRequestNo() { return requestNo; }
    public void setRequestNo(String requestNo) { this.requestNo = requestNo; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getApplicant() { return applicant; }
    public void setApplicant(String applicant) { this.applicant = applicant; }
    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public Long getApplicantId() { return applicantId; }
    public void setApplicantId(Long applicantId) { this.applicantId = applicantId; }
    public Long getDepartmentId() { return departmentId; }
    public void setDepartmentId(Long departmentId) { this.departmentId = departmentId; }
}
