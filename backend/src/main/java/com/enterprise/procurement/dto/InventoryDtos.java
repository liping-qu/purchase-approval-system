package com.enterprise.procurement.dto;

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

    /** 手工调整：正数入库、负数出库；0 没有语义，服务端直接拒绝。 */
    public record Adjust(Integer quantity) {
    }
}
