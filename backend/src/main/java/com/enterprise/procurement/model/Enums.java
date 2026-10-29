package com.enterprise.procurement.model;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 业务枚举与状态机的唯一定义处。
 *
 * <p>改造前状态是一串裸字符串散落在 service 里（{@code "DRAFT"}、{@code "PENDING"}…），
 * 合法流转只体现在"哪几行代码调用了 transition"，读代码的人无法一眼看出状态图，
 * 而且拼错一个字母（{@code "APROVED"}）不会被任何东西拦住，只会写进库里变成脏数据。</p>
 *
 * <p>现在把状态图和合法迁移集中到 {@link #TRANSITIONS}，service 每次流转前先问一句
 * {@code from.canGoTo(to)}，非法迁移在代码层面就抛错，同时这张表本身就是最好的文档。</p>
 */
public final class Enums {

    private Enums() {}

    /** 采购申请状态。 */
    public enum ApplicationStatus {
        DRAFT,        // 草稿，仅本人可编辑/删除
        PENDING,      // 已提交，等待部门负责人审批
        APPROVED,     // 审批通过，等待采购下单
        REJECTED,     // 审批驳回（终态）
        PURCHASING,   // 已下单，已完成订购
        COMPLETED,    // 已入库完成（终态）
        CANCELLED;    // 作废（终态，当前流程未使用，预留）

        private static final Map<ApplicationStatus, Set<ApplicationStatus>> TRANSITIONS = Map.of(
                DRAFT, Set.of(PENDING),
                PENDING, Set.of(DRAFT, APPROVED, REJECTED),   // DRAFT = 申请人撤回
                APPROVED, Set.of(PURCHASING),
                REJECTED, Set.of(),
                PURCHASING, Set.of(COMPLETED),
                COMPLETED, Set.of(),
                CANCELLED, Set.of()
        );

        public boolean canGoTo(ApplicationStatus next) {
            return TRANSITIONS.getOrDefault(this, Set.of()).contains(next);
        }

        /** 严格解析：库里出现枚举外的值时直接失败，避免把脏数据当正常状态处理。 */
        public static ApplicationStatus of(String code) {
            return valueOf(code.toUpperCase(Locale.ROOT));
        }
    }

    /** 采购订单状态。注意这里必须与 {@code purchase_order.status} 实际取值一致。 */
    public enum OrderStatus {
        CREATED,    // 已创建，等待收货
        DELIVERED;  // 已入库

        private static final Map<OrderStatus, Set<OrderStatus>> TRANSITIONS = Map.of(
                CREATED, Set.of(DELIVERED),
                DELIVERED, Set.of()
        );

        public boolean canGoTo(OrderStatus next) {
            return TRANSITIONS.getOrDefault(this, Set.of()).contains(next);
        }
    }

    public enum ApprovalAction { APPROVE, REJECT }

    public enum UserRole { ADMIN, PURCHASER, APPROVER, MANAGER, WAREHOUSE, EMPLOYEE }
}
