package com.enterprise.procurement.persistence.entity;

/** purchase_request 按状态聚合的投影行，仅用于工作台统计。 */
public class StatusCountRow {
    private String status;
    private Long total;

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getTotal() { return total; }
    public void setTotal(Long total) { this.total = total; }
}
