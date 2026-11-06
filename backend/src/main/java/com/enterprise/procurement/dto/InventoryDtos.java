package com.enterprise.procurement.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 库存相关的请求体。
 *
 * <p>列表行不用 DTO 而是 {@code persistence/entity/InventoryStockRow}，
 * 与工作台的 {@code StatusCountRow}/{@code RecentRequestRow} 保持一致：
 * 跨表聚合的投影行统一放实体包，MyBatis 用 setter 映射，不依赖构造器参数名。</p>
 */
public final class InventoryDtos {

    private InventoryDtos() {
    }

    /**
     * 手工调整：正数入库、负数出库；0 没有语义（服务端拒绝）。
     *
     * <p>{@code @NotNull} 让"忘记传数量"在参数绑定阶段就是 400 而不是走到
     * service 里空指针；上下限挡住 {@code Integer.MIN_VALUE} 这种一看就不是
     * 人手输入的值——它会让 {@code -quantity} 溢出成负数，最终扣减逻辑反向。</p>
     */
    public record Adjust(
            @NotNull(message = "请填写调整数量")
            @Min(value = -99999, message = "单次调整不能超过 99999")
            @Max(value = 99999, message = "单次调整不能超过 99999")
            Integer quantity) {
    }
}
