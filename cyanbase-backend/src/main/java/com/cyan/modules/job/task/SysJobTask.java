package com.cyan.modules.job.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cyan.common.redis.RedisUtil;
import com.cyan.entity.SysLogLogin;
import com.cyan.entity.SysLogOperation;
import com.cyan.mapper.SysLogLoginMapper;
import com.cyan.mapper.SysLogOperationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.time.LocalDateTime;
import java.util.Set;

/**
 * 系统自动化任务执行类
 */
@Slf4j
@Component("sysJobTask")
@RequiredArgsConstructor
public class SysJobTask {

    private final SysLogOperationMapper sysLogOperationMapper;
    private final SysLogLoginMapper sysLogLoginMapper;
    private final RedisUtil redisUtil;

    /**
     * 清理7天前的操作日志
     */
    public void cleanOperationLog() {
        log.info("开始清理7天前的操作日志...");
        
        LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
        LambdaQueryWrapper<SysLogOperation> wrapper = new LambdaQueryWrapper<>();
        wrapper.lt(SysLogOperation::getCreateTime, sevenDaysAgo);
        
        int count = sysLogOperationMapper.delete(wrapper);
        log.info("清理操作日志完成，共删除 {} 条记录", count);
    }

    /**
     * 清理7天前的登录日志
     */
    public void cleanLoginLog() {
        log.info("开始清理7天前的登录日志...");
        
        LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
        LambdaQueryWrapper<SysLogLogin> wrapper = new LambdaQueryWrapper<>();
        wrapper.lt(SysLogLogin::getLoginTime, sevenDaysAgo);
        
        int count = sysLogLoginMapper.delete(wrapper);
        log.info("清理登录日志完成，共删除 {} 条记录", count);
    }

    /**
     * 清理30天前的临时文件
     */
    public void cleanTempFile() {
        log.info("开始清理30天前的临时文件...");
        
        String tempDir = System.getProperty("java.io.tmpdir");
        File dir = new File(tempDir);
        
        if (!dir.exists() || !dir.isDirectory()) {
            log.warn("临时目录不存在: {}", tempDir);
            return;
        }
        
        long thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000);
        File[] files = dir.listFiles();
        
        if (files == null) {
            return;
        }
        
        int count = 0;
        for (File file : files) {
            if (file.isFile() && file.lastModified() < thirtyDaysAgo) {
                if (file.delete()) {
                    count++;
                }
            }
        }
        
        log.info("清理临时文件完成，共删除 {} 个文件", count);
    }

    /**
     * 清理过期Redis缓存
     */
    public void cleanExpiredCache() {
        log.info("开始清理过期Redis缓存...");
        
        // Redis会自动清理过期的key，这里可以做一些额外的清理工作
        // 例如清理特定的缓存前缀
        
        // 示例：清理所有以 "cyan:temp:" 开头的key
        Set<String> keys = redisUtil.keys("cyan:temp:*");
        if (keys != null && !keys.isEmpty()) {
            redisUtil.delete(keys.toArray(new String[0]));
            log.info("清理临时缓存完成，共删除 {} 个key", keys.size());
        } else {
            log.info("无需清理的临时缓存");
        }
    }

    /**
     * 每日用户登录统计
     */
    public void dailyLoginStatistics() {
        log.info("开始统计昨日用户登录数据...");
        
        // TODO: 实现具体的统计逻辑
        // 1. 查询昨日的登录记录
        // 2. 统计成功/失败次数
        // 3. 存储到统计表
        
        log.info("用户登录统计完成");
    }

    /**
     * 同步权限数据到Redis
     */
    public void syncPermissionToRedis() {
        log.info("开始同步权限数据到Redis...");
        
        // TODO: 实现权限同步逻辑
        // 1. 从数据库查询所有权限数据
        // 2. 更新Redis缓存
        
        log.info("权限数据同步完成");
    }
}
