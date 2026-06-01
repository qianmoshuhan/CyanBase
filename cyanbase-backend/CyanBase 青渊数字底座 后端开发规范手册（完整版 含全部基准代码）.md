CyanBase 青渊数字底座 后端开发规范手册（完整版 含全部基准代码）
可直接丢给 VSCode AI 智能体，AI 会自动建目录、建包、生成所有基础代码，严格按这份规范和模板生成，不许改动版本、数据库、目录结构。
一、基础环境规范

1. 版本约束（强制不可改）
   项目名称：cyanbase-backend
   Java 版本：Java 17
   Maven 版本：3.8.7
   SpringBoot：3.1.6
   MyBatis-Plus：3.5.3.1
   数据库：MariaDB 10.11
   项目编码：UTF-8

2. 服务器部署根路径
   plaintext
   /home/xjgh2025XZD/Cyan/CyanBase/cyanbase-backend
   二、数据库连接规范（固定死，不许修改）

3. 数据库远程信息
   数据库 IP：120.48.47.253
   端口：3306
   库名：cyan_base
   账号：petclinic
   密码：xJgh2021@xzd

4. 固定 JDBC URL
   plaintext
   jdbc:mariadb://120.48.47.253:3306/cyan_base?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
   三、标准代码目录结构（AI 严格按此生成）
   plaintext
   cyanbase-backend
   ├── pom.xml
   └── src
    └── main
   
        ├── java/com/cyan
        │   ├── CyanBaseApplication.java       # 启动类
        │   ├── common                         # 公共全局类
        │   │   ├── R.java                     # 统一返回
        │   │   ├── ResultCode.java            # 状态码
        │   │   └── GlobalExceptionHandler.java # 全局异常
        │   ├── config                         # 配置类
        │   │   ├── MybatisPlusConfig.java     # MP分页插件
        │   │   └── WebConfig.java              # 跨域Web配置
        │   ├── entity                         # 数据库实体
        │   ├── mapper                         # Mapper接口
        │   ├── service
        │   │   ├── 接口
        │   │   └── impl 实现类
        │   ├── controller                     # 控制器
        │   ├── dto                            # 入参出参VO/DTO
        │   └── util                           # 工具类
        └── resources
            ├── application.properties
            └── mapper                         # Mapper XML文件
   
   四、开发强制编码规范
   所有数据库表前缀 sys_
   实体类统一注解：@TableName、@TableId(type = IdType.AUTO)、@Data
   数据库下划线 → 实体驼峰命名
   所有表必带公共字段：id、tenant_id、dept_id、create_by、update_by、create_time、update_time、del_flag、remark
   启用逻辑删除 del_flag 0正常 1删除，禁止物理删除
   禁止数据库物理外键，只做逻辑关联
   所有接口统一返回 R<T>
   三层架构：Controller → Service → Mapper
   五、附：固定完整模板代码（AI 直接照搬）

5. pom.xml 完整文本
   xml
   
   <?xml version="1.0" encoding="UTF-8"?>
   
   <project xmlns="http://maven.apache.org/POM/4.0.0"
   
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
   
    <modelVersion>4.0.0</modelVersion>
   
    <groupId>com.cyan</groupId>
    <artifactId>cyanbase-backend</artifactId>
    <version>1.0.0</version>
    <name>CyanBase 青渊数字底座</name>
   
   <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.1.6</version>
        <relativePath/>
    </parent>
   
   <properties>
        <java.version>17</java.version>
        <maven.compiler.source>17</maven.compiler.source>
        <maven.compiler.target>17</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <mybatis.plus.version>3.5.3.1</mybatis.plus.version>
    </properties>
   
   <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
   
        <dependency>
            <groupId>org.mariadb.jdbc</groupId>
            <artifactId>mariadb-java-client</artifactId>
            <scope>runtime</scope>
        </dependency>
       
        <dependency>
            <groupId>com.baomidou.mybatisplus</groupId>
            <artifactId>mybatis-plus-boot-starter</artifactId>
            <version>${mybatis.plus.version}</version>
        </dependency>
       
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>
       
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
   
   </dependencies>
   
   <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <excludes>
                        <exclude>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                        </exclude>
                    </excludes>
                </configuration>
            </plugin>
        </plugins>
    </build>
   </project>

6. application.properties 完整配置
   properties
   server.port=8090
   spring.application.name=cyanbase-backend

spring.datasource.url=jdbc:mariadb://120.48.47.253:3306/cyan_base?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
spring.datasource.username=petclinic
spring.datasource.password=xJgh2021@xzd
spring.datasource.driver-class-name=org.mariadb.jdbc.Driver

mybatis-plus.mapper-locations=classpath:mapper/**/*.xml
mybatis-plus.configuration.map-underscore-to-camel-case=true
mybatis-plus.configuration.log-impl=org.apache.ibatis.logging.stdout.StdOutImpl

# 逻辑删除全局配置

mybatis-plus.global-config.db-config.logic-delete-field=del_flag
mybatis-plus.global-config.db-config.logic-delete-value=1
mybatis-plus.global-config.db-config.logic-not-delete-value=0
3. 启动类 CyanBaseApplication.java
java
运行
package com.cyan;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.cyan.mapper")
public class CyanBaseApplication {
    public static void main(String[] args) {
        SpringApplication.run(CyanBaseApplication.class, args);
        System.out.println("===== CyanBase青渊数字底座 启动成功 =====");
    }
}
4. 统一返回公共类 R.java
java
运行
package com.cyan.common;

import lombok.Data;

@Data
public class R<T> {
    private int code;
    private String msg;
    private T data;

    public static <T> R<T> ok() {
        return restResult(null, ResultCode.SUCCESS, "操作成功");
    }
    
    public static <T> R<T> ok(T data) {
        return restResult(data, ResultCode.SUCCESS, "操作成功");
    }
    
    public static <T> R<T> ok(T data, String msg) {
        return restResult(data, ResultCode.SUCCESS, msg);
    }
    
    public static <T> R<T> fail(String msg) {
        return restResult(null, ResultCode.FAIL, msg);
    }
    
    private static <T> R<T> restResult(T data, int code, String msg) {
        R<T> r = new R<>();
        r.setCode(code);
        r.setMsg(msg);
        r.setData(data);
        return r;
    }

}
5. 状态码 ResultCode.java
java
运行
package com.cyan.common;

public class ResultCode {
    public static final int SUCCESS = 200;
    public static final int FAIL = 500;
    public static final int UNAUTHORIZED = 401;
    public static final int FORBIDDEN = 403;
}
6. 全局异常处理器 GlobalExceptionHandler.java
java
运行
package com.cyan.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(Exception.class)
    public R<?> handleException(Exception e) {
        log.error("系统异常：", e);
        return R.fail("系统繁忙，请稍后重试");
    }

}
7. MyBatis-Plus 分页配置 MybatisPlusConfig.java
java
运行
package com.cyan.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor());
        return interceptor;
    }

}
8. 跨域 WebConfig.java
java
运行
package com.cyan.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins("*")
                .allowedMethods("GET","POST","PUT","DELETE","OPTIONS")
                .allowCredentials(false)
                .maxAge(3600);
    }

}
