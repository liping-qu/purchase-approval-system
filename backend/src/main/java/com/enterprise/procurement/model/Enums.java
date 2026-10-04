package com.enterprise.procurement.model;

public final class Enums {
    private Enums() {}
    public enum ApplicationStatus { DRAFT, PENDING, APPROVED, REJECTED, PURCHASING, COMPLETED, CANCELLED }
    public enum ApprovalAction { APPROVE, REJECT }
    public enum OrderStatus { CREATED, RECEIVING, COMPLETED, CANCELLED }
    public enum UserRole { ADMIN, PURCHASER, APPROVER, MANAGER, WAREHOUSE, EMPLOYEE }
}
