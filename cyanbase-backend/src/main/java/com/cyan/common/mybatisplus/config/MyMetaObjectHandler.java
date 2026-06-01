package com.cyan.common.mybatisplus.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
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
     */
    private String getCurrentUsername() {
        try {
            org.springframework.security.core.Authentication authentication = 
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            
            if (authentication != null && authentication.isAuthenticated() 
                && !"anonymousUser".equals(authentication.getPrincipal())) {
                
                Object principal = authentication.getPrincipal();
                if (principal instanceof org.springframework.security.core.userdetails.UserDetails) {
                    return ((org.springframework.security.core.userdetails.UserDetails) principal).getUsername();
                }
                return principal.toString();
            }
        } catch (Exception e) {
            log.debug("获取SecurityContext失败");
        }
        return null;
    }
}