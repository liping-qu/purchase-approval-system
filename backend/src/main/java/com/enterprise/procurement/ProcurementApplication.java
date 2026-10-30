package com.enterprise.procurement;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.mybatis.spring.annotation.MapperScan;

/**
 * 启动类。
 *
 * <p>这里原本挂着 {@code @EnableScheduling}，但全项目没有任何 {@code @Scheduled} 方法：
 * 等于白建一个调度线程池，还会让读代码的人误以为存在定时任务（单号回收、库存对账之类）。
 * 真需要定时任务时再加注解，并在那个类上写清楚触发周期与失败重试策略。</p>
 */
@SpringBootApplication
@MapperScan({"com.enterprise.procurement.persistence.mapper", "com.enterprise.procurement.admin.mapper"})
public class ProcurementApplication {
    public static void main(String[] args) { SpringApplication.run(ProcurementApplication.class, args); }
}
