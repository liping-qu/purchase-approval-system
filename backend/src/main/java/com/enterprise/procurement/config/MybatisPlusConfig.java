package com.enterprise.procurement.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 插件配置。
 *
 * <p>改造前项目<b>没有注册分页插件</b>，但 {@code AdminController.users} 和
 * {@code OperationLogController.list} 都在用 {@code selectPage}：
 * 没有 {@link PaginationInnerInterceptor} 时，MyBatis-Plus 不会拼 {@code LIMIT}，
 * 也不会执行 count —— 接口把全表返回，而 {@code total} 恒为 0。
 * 前端分页器因此永远显示"共 0 条"，点下一页还是同一批数据。</p>
 *
 * <p>{@code maxLimit=200} 是防呆：没有上限的话，一个 {@code ?size=999999}
 * 就能把整张表读进内存，既是性能问题也是稳定性问题。</p>
 */
@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);
        pagination.setMaxLimit(200L);
        interceptor.addInnerInterceptor(pagination);
        return interceptor;
    }
}
