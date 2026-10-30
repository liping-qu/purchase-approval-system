package com.enterprise.procurement.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 主键策略守门员。
 *
 * <p>为什么值得专门写个测试：MyBatis-Plus 的 {@code @TableId} 不写 type 时默认走雪花算法，
 * 生成的 19 位 id 超出前端 {@code Number.MAX_SAFE_INTEGER}（9007199254740991），
 * 浏览器拿到的是四舍五入后的近似值，再拿它回查/更新/删除就是 404 ——
 * 供应商模块已经踩过一次（升级 8），表现是"建完就再也改不动"。
 * 而且一次显式雪花 id 会把表的 AUTO_INCREMENT 顶到 19 位，MySQL 8 再也调不回来。</p>
 *
 * <p>这里不逐个列类，而是扫出所有带 {@code @TableName} 的实体，
 * 以后新增实体自动纳入约束，不用记得来改这个测试。</p>
 */
class EntityIdStrategyTest {

    @Test
    @DisplayName("所有实体里名叫 id 的主键都必须是数据库自增，不能是雪花算法")
    void idFieldsMustBeAutoIncrement() throws Exception {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(TableName.class));

        List<String> violations = new ArrayList<>();
        int checked = 0;
        for (BeanDefinition bd : scanner.findCandidateComponents("com.enterprise.procurement")) {
            Class<?> entity = Class.forName(bd.getBeanClassName());
            for (Field field : entity.getDeclaredFields()) {
                TableId tableId = field.getAnnotation(TableId.class);
                if (tableId == null || !"id".equals(field.getName())) {
                    continue;   // inventory_stock 用 product_id 做业务主键，不在约束范围
                }
                checked++;
                if (tableId.type() != IdType.AUTO) {
                    violations.add(entity.getSimpleName() + "#" + field.getName() + " -> " + tableId.type());
                }
            }
        }

        assertTrue(checked >= 10, "扫描到的实体主键太少（" + checked + "），扫描路径可能写错了");
        assertTrue(violations.isEmpty(), "以下实体主键不是自增，会产生 JS 精度丢失的 19 位 id：" + violations);
    }

    @Test
    @DisplayName("回归：供应商与商品这两个运行时可写的实体，必须是 IDENTITY")
    void writableCatalogsAreAutoIncrement() throws NoSuchFieldException {
        assertEquals(IdType.AUTO, SupplierEntity.class.getDeclaredField("id").getAnnotation(TableId.class).type());
        assertEquals(IdType.AUTO, ProductEntity.class.getDeclaredField("id").getAnnotation(TableId.class).type());
    }
}
