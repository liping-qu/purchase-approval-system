package com.enterprise.procurement.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * 采购申请相关的请求体。
 *
 * <p>校验边界都对着数据库列来定，不是拍脑袋：{@code reason}/{@code comment} 是
 * {@code VARCHAR(500)}，{@code quantity} 是 {@code INT}，{@code unit_price} 是
 * {@code DECIMAL(14,2)}。改造前没有 {@code @Size}/{@code @Max}，
 * 超长事由会一路走到数据库抛 {@code DataIntegrityViolation} → 兜底 500，
 * 用户看到"服务器内部错误"，实际只是他多打了几个字。</p>
 */
public final class PurchaseRequestDtos {

    private PurchaseRequestDtos() {
    }

    public record Item(
            @NotNull(message = "请选择商品")
            @Positive(message = "商品 id 不合法")
            Long productId,

            @NotNull(message = "请填写数量")
            @Min(value = 1, message = "采购数量至少为 1")
            @Max(value = 9999, message = "单个商品数量不能超过 9999")
            Integer quantity,

            @NotNull(message = "请填写单价")
            @DecimalMin(value = "0.00", message = "单价不能为负数")
            @DecimalMax(value = "99999999.99", message = "单价超出范围")
            @Digits(integer = 8, fraction = 2, message = "单价最多 8 位整数、2 位小数")
            BigDecimal unitPrice) {
    }

    /**
     * 建单请求。
     *
     * @param submit 为 true 时在<b>同一个事务</b>里直接提交审批，避免前端"建草稿 + 提交"
     *               两跳之间失败留下孤儿草稿；为 null/false 时行为与改造前一致（只建草稿）
     */
    public record Create(
            @NotBlank(message = "请填写采购事由")
            @Size(max = 500, message = "采购事由不能超过 500 字")
            String reason,

            @NotEmpty(message = "请至少填写一行采购明细")
            @Size(max = 50, message = "单张申请最多 50 行明细")
            List<@Valid Item> items,

            Boolean submit) {
    }

    public record Update(
            @NotBlank(message = "请填写采购事由")
            @Size(max = 500, message = "采购事由不能超过 500 字")
            String reason,

            @NotEmpty(message = "请至少填写一行采购明细")
            @Size(max = 50, message = "单张申请最多 50 行明细")
            List<@Valid Item> items) {
    }

    public record Approval(
            @NotBlank(message = "请指定审批动作")
            @Pattern(regexp = "APPROVE|REJECT", message = "action 必须为 APPROVE 或 REJECT")
            String action,

            @Size(max = 500, message = "审批意见不能超过 500 字")
            String comment) {
    }

    /**
     * 下单请求。
     *
     * <p>{@code requestId} 仍然允许为空：历史上存在 {@code ?requestId=} 的调用方式，
     * Controller 会在 body 缺失时回退到查询参数。两个入口只能留一个的话，
     * 应该等前端全部迁移完再删，现在直接删会让旧调用方 400。</p>
     */
    public record CreateOrder(
            @Positive(message = "申请单 id 不合法")
            Long requestId,

            @NotNull(message = "请选择供应商")
            @Positive(message = "供应商 id 不合法")
            Long supplierId) {
    }
}
