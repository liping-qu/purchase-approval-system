package com.enterprise.procurement.model;

import com.enterprise.procurement.model.Enums.ApplicationStatus;
import com.enterprise.procurement.model.Enums.OrderStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 状态机的单元测试。
 *
 * <p>这类"纯逻辑"最该写测试：没有数据库、没有网络，跑一次几毫秒，
 * 却能锁住整条业务流程的合法边界。以后谁想加一条 DRAFT → COMPLETED 的捷径，
 * 这里会先红。</p>
 */
class EnumsStateMachineTest {

    @Test
    @DisplayName("申请单：草稿只能提交，不能直接通过或完成")
    void draftCanOnlySubmit() {
        assertThat(ApplicationStatus.DRAFT.canGoTo(ApplicationStatus.PENDING)).isTrue();
        assertThat(ApplicationStatus.DRAFT.canGoTo(ApplicationStatus.APPROVED)).isFalse();
        assertThat(ApplicationStatus.DRAFT.canGoTo(ApplicationStatus.COMPLETED)).isFalse();
    }

    @Test
    @DisplayName("申请单：待审批可撤回、可通过、可驳回")
    void pendingBranches() {
        assertThat(ApplicationStatus.PENDING.canGoTo(ApplicationStatus.DRAFT)).isTrue();   // 撤回
        assertThat(ApplicationStatus.PENDING.canGoTo(ApplicationStatus.APPROVED)).isTrue();
        assertThat(ApplicationStatus.PENDING.canGoTo(ApplicationStatus.REJECTED)).isTrue();
        assertThat(ApplicationStatus.PENDING.canGoTo(ApplicationStatus.COMPLETED)).isFalse();
    }

    @Test
    @DisplayName("申请单：通过后只能进入采购中，采购中只能完成")
    void approvedThenPurchasing() {
        assertThat(ApplicationStatus.APPROVED.canGoTo(ApplicationStatus.PURCHASING)).isTrue();
        assertThat(ApplicationStatus.APPROVED.canGoTo(ApplicationStatus.PENDING)).isFalse();
        assertThat(ApplicationStatus.PURCHASING.canGoTo(ApplicationStatus.COMPLETED)).isTrue();
    }

    @Test
    @DisplayName("申请单：驳回/完成/作废都是终态")
    void terminalStates() {
        for (ApplicationStatus s : new ApplicationStatus[]{
                ApplicationStatus.REJECTED, ApplicationStatus.COMPLETED, ApplicationStatus.CANCELLED}) {
            for (ApplicationStatus target : ApplicationStatus.values()) {
                assertThat(s.canGoTo(target)).as("%s -> %s", s, target).isFalse();
            }
        }
    }

    @Test
    @DisplayName("订单：只能 已创建 -> 已入库")
    void orderTransitions() {
        assertThat(OrderStatus.CREATED.canGoTo(OrderStatus.DELIVERED)).isTrue();
        assertThat(OrderStatus.DELIVERED.canGoTo(OrderStatus.CREATED)).isFalse();
    }

    @Test
    @DisplayName("状态解析大小写不敏感，非法值直接失败")
    void parseStatus() {
        assertThat(ApplicationStatus.of("pending")).isEqualTo(ApplicationStatus.PENDING);
        assertThatThrownBy(() -> ApplicationStatus.of("APROVED"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
