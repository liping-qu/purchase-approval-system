package com.enterprise.procurement.aspect;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记需要写操作日志的方法。
 *
 * <p>{@code module} 独立出来是必要的：原来模块名在切面里写死成「采购管理」，
 * 后台操作（建用户、改角色）也会被记成采购管理，按模块筛选等于没用。</p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OperationLog {

    /** 动作描述，例如「创建采购订单」。 */
    String value();

    /** 业务模块，用于后台按模块筛选。 */
    String module() default "业务";
}
