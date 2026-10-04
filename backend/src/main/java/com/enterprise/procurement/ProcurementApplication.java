package com.enterprise.procurement;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.mybatis.spring.annotation.MapperScan;

@SpringBootApplication
@EnableScheduling
@MapperScan({"com.enterprise.procurement.persistence.mapper", "com.enterprise.procurement.admin.mapper"})
public class ProcurementApplication {
    public static void main(String[] args) { SpringApplication.run(ProcurementApplication.class, args); }
}
