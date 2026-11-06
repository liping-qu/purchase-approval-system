package com.enterprise.procurement.dto;

import java.util.List;

/**
 * 统一分页响应。
 *
 * <p>字段名与管理端已有接口保持一致（{@code records/total/page/size}），
 * 前端一个分页组件可以复用到所有列表页，不需要按接口写两套取值逻辑。</p>
 *
 * <p>{@code total} 是<b>满足过滤条件的总行数</b>，不是当前页行数——
 * 分页器要靠它算页数。MyBatis-Plus 在没有分页插件时 {@code total} 恒为 0，
 * 这正是改造前管理端分页"永远只有一页"的根因。</p>
 */
public record PageResult<T>(List<T> records, long total, long page, long size) {

    public static <T> PageResult<T> of(List<T> records, long total, long page, long size) {
        return new PageResult<>(records, total, page, size);
    }

    public static <T> PageResult<T> empty(long page, long size) {
        return new PageResult<>(List.of(), 0L, page, size);
    }
}
