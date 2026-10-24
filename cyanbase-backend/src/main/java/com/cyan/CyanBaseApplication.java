package com.cyan;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan({"com.cyan.mapper", "com.cyan.modules.job.mapper", "com.cyan.modules.message.mapper", "com.cyan.modules.log.mapper", "com.cyan.modules.workflow.mapper", "com.cyan.modules.form.mapper", "com.cyan.modules.sydyd.mapper"})
public class CyanBaseApplication {
    public static void main(String[] args) {
        SpringApplication.run(CyanBaseApplication.class, args);
        System.out.println("===== CyanBase青渊数字底座 启动成功 =====");
    }
}