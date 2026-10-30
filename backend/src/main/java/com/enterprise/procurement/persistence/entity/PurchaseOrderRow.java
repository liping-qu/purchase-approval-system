package com.enterprise.procurement.persistence.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 采购订单的"展示态"投影。
 *
 * <p>和 {@link PurchaseRequestRow} 同一个问题：订单表只有 {@code request_id} 和
 * {@code supplier_id}，页面上要显示的是"关联申请编号"和"供应商名称"。
 * 一次 LEFT JOIN 取回，而不是让前端拿着 id 再去点查两次。</p>
 *
 * <p>用 LEFT JOIN 而非 INNER JOIN：供应商被停用（active=0）甚至记录的 id 已不存在时，
 * 订单本身仍然必须可见——历史单据不能因为主数据变动就从列表里消失，
 * 拿不到名称时退化成"供应商#12"，比整行消失安全得多。</p>
 */
public class PurchaseOrderRow {
    private Long id;
    private String orderNo;
    private Long requestId;
    private String requestNo;
    private String reason;
    private Long supplierId;
    private String supplier;
    private BigDecimal amount;
    private String status;
    private LocalDateTime createTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public Long getRequestId() { return requestId; }
    public void setRequestId(Long requestId) { this.requestId = requestId; }
    public String getRequestNo() { return requestNo; }
    public void setRequestNo(String requestNo) { this.requestNo = requestNo; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public Long getSupplierId() { return supplierId; }
    public void setSupplierId(Long supplierId) { this.supplierId = supplierId; }
    public String getSupplier() { return supplier; }
    public void setSupplier(String supplier) { this.supplier = supplier; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
