package com.enterprise.procurement.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 请求体校验边界测试。
 *
 * <p>这些边界对着数据库列来定：{@code reason}/{@code comment} 是 {@code VARCHAR(500)}，
 * {@code unit_price} 是 {@code DECIMAL(14,2)}。改造前没有上限，超长事由会一路走到
 * 数据库抛 {@code DataIntegrityViolation}，最后变成 500"服务器内部错误"——
 * 用户只是多打了几个字，却看到系统故障。</p>
 *
 * <p>用 Validator 直接跑，不起 Spring 上下文：这类规则是纯声明式的，
 * 起 MVC 只会把测试变慢而不增加可信度。</p>
 */
class PurchaseRequestDtosValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private static PurchaseRequestDtos.Item item(int quantity, String price) {
        return new PurchaseRequestDtos.Item(1L, quantity, new BigDecimal(price));
    }

    @Test
    @DisplayName("合法请求：零违规")
    void validRequestPasses() {
        var dto = new PurchaseRequestDtos.Create("采购办公耗材 A4 纸 20 包",
                List.of(item(20, "3.50"), item(1, "0.00")), true);

        assertThat(validator.validate(dto)).isEmpty();
    }

    @Test
    @DisplayName("事由超过 500 字：拦住，而不是让数据库报错变 500")
    void reasonTooLongRejected() {
        var dto = new PurchaseRequestDtos.Create("超".repeat(501), List.of(item(1, "1.00")), false);

        assertThat(messages(dto)).anyMatch(m -> m.contains("500 字"));
    }

    @Test
    @DisplayName("明细超过 50 行：拦住")
    void tooManyItemsRejected() {
        var items = IntStream.range(0, 51).mapToObj(i -> item(1, "1.00")).toList();
        var dto = new PurchaseRequestDtos.Create("明细太多", items, false);

        assertThat(messages(dto)).anyMatch(m -> m.contains("50 行"));
    }

    @Test
    @DisplayName("数量为 0 / 超上限 / 单价三位小数：都拦住")
    void itemBoundariesRejected() {
        assertThat(messages(new PurchaseRequestDtos.Create("q0", List.of(item(0, "1.00")), false)))
                .anyMatch(m -> m.contains("至少为 1"));
        assertThat(messages(new PurchaseRequestDtos.Create("q大", List.of(item(10000, "1.00")), false)))
                .anyMatch(m -> m.contains("9999"));
        assertThat(messages(new PurchaseRequestDtos.Create("价格", List.of(item(1, "1.005")), false)))
                .anyMatch(m -> m.contains("2 位小数"));
    }

    @Test
    @DisplayName("审批动作必须二选一：脏字符串在参数绑定阶段就被拒")
    void approvalActionMustBeEnum() {
        assertThat(messages(new PurchaseRequestDtos.Approval("agree", null)))
                .anyMatch(m -> m.contains("APPROVE"));
        assertThat(messages(new PurchaseRequestDtos.Approval("APPROVE", "批".repeat(501))))
                .anyMatch(m -> m.contains("500 字"));
    }

    private Set<String> messages(Object dto) {
        return validator.validate(dto).stream()
                .map(ConstraintViolation::getMessage)
                .collect(java.util.stream.Collectors.toSet());
    }
}
