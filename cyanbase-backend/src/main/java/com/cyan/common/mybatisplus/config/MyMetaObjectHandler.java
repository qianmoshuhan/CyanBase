package com.cyan.common.mybatisplus.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.cyan.security.domain.LoginUser;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus 自动填充处理器
 */
@Slf4j
@Component
public class MyMetaObjectHandler implements MetaObjectHandler {

    /**
     * 插入时自动填充
     */
    @Override
    public void insertFill(MetaObject metaObject) {
        log.debug("开始插入填充...");
        
        // 自动填充 createTime
        this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, LocalDateTime.now());
        
        // 自动填充 updateTime
        this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
        
        // 自动填充 createBy（如果SecurityContext中有用户信息）
        try {
            String username = getCurrentUsername();
            if (username != null) {
                this.strictInsertFill(metaObject, "createBy", String.class, username);
                this.strictInsertFill(metaObject, "updateBy", String.class, username);
            }
        } catch (Exception e) {
            log.debug("获取当前用户失败，跳过createBy/updateBy填充");
        }
    }

    /**
     * 更新时自动填充
     */
    @Override
    public void updateFill(MetaObject metaObject) {
        log.debug("开始更新填充...");
        
        // 自动填充 updateTime
        this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
        
        // 自动填充 updateBy（如果SecurityContext中有用户信息）
        try {
            String username = getCurrentUsername();
            if (username != null) {
                this.strictUpdateFill(metaObject, "updateBy", String.class, username);
            }
        } catch (Exception e) {
            log.debug("获取当前用户失败，跳过updateBy填充");
        }
    }

    /**
     * 获取当前登录用户名
     * <p>
     * 兼容三种 principal 类型：
     * 1. LoginUser  —— 本系统 JWT 过滤器塞入的自定义域对象（最常见）
     * 2. UserDetails —— Spring Security 标准接口（预留）
     * 3. String      —— anonymousUser 或直接塞字符串的场景
     * <p>
     * ⚠️ 绝对不能对 LoginUser 调 toString()！Lombok @Data 生成的 toString()
     *    会把 sysUser 等嵌套字段全部展开，产生 300+ 字符，超过数据库 create_by(varchar 50) 限制。
     */
    private String getCurrentUsername() {
        try {
            org.springframework.security.core.Authentication authentication =
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();

            if (authentication != null && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getPrincipal())) {

                Object principal = authentication.getPrincipal();
                if (principal instanceof LoginUser) {
                    return ((LoginUser) principal).getUserName();
                }
                if (principal instanceof org.springframework.security.core.userdetails.UserDetails) {
                    return ((org.springframework.security.core.userdetails.UserDetails) principal).getUsername();
                }
                if (principal instanceof String) {
                    return (String) principal;
                }
                // 兜底：只取类名，避免长字符串
                log.warn("未知 principal 类型：{}，跳过 createBy 填充",
                        principal != null ? principal.getClass().getName() : "null");
                return null;
            }
        } catch (Exception e) {
            log.debug("获取SecurityContext失败");
        }
        return null;
    }
}