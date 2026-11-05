package com.cyan.modules.message.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cyan.modules.message.entity.SysMessageUser;
import com.cyan.modules.message.mapper.SysMessageUserMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * SSE 连接管理器 —— 按 userId 维护多连接（多标签页），支持主动推送未读计数
 * <p>
 * 设计要点：
 * 1. 同一 userId 可能打开多个标签页 → 存 List<SseEmitter> 广播
 * 2. 用户发消息/标已读后 → pushCount(userId) 推最新 DB 值给所有连接
 * 3. 心跳 25s 一次 → 撑过 nginx/Tomcat 默认 60s 超时
 * 4. SseEmitter.onCompletion/onTimeout/onError 回调里清理自己
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SseEmitterManager {

    /**
     * 为什么注入 Mapper 而不是 Service？
     *   SysMessageServiceImpl → SseEmitterManager → SysMessageService(接口) → SysMessageServiceImpl
     *   形成循环依赖，Spring 启动直接炸。
     *   改用最底层的 SysMessageUserMapper，它不依赖任何 Service，循环立断。
     */
    private final SysMessageUserMapper sysMessageUserMapper;

    /**
     * userId → 该用户所有活跃 SSE 连接
     * ConcurrentHashMap 保证并发安全；CopyOnWriteArrayList 保证迭代时不抛 ConcurrentModification
     */
    private final Map<Long, List<SseEmitter>> connections = new ConcurrentHashMap<>();

    /** 定时心跳执行器（单线程足够，所有 SSE 共享） */
    private final ScheduledExecutorService heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "sse-heartbeat");
        t.setDaemon(true);
        return t;
    });

    /**
     * Spring 依赖注入完成后启动心跳
     * 为什么用 @PostConstruct 而不是构造器里写？
     *   Lombok @RequiredArgsConstructor 生成构造器、手写构造器二者只能取其一
     *   手写无参构造器 + final 字段引用 → 编译器报 "might not have been initialized"
     *   @PostConstruct 在所有 final 字段都赋值完毕后执行，是写初始化逻辑的正确位置
     */
    @PostConstruct
    public void init() {
        // 25 秒一次心跳（nginx 默认 proxy_read_timeout 60s，留 2.4x 余量）
        heartbeatExecutor.scheduleAtFixedRate(this::sendHeartbeat, 25, 25, TimeUnit.SECONDS);
        Runtime.getRuntime().addShutdownHook(new Thread(heartbeatExecutor::shutdownNow));
    }

    /** Bean 销毁时优雅关闭心跳线程池 */
    @PreDestroy
    public void destroy() {
        heartbeatExecutor.shutdownNow();
    }

    // ============= 连接建立 / 销毁 =============

    /**
     * 创建并注册 SSE 连接。timeout 设 0 = 永不超时，靠心跳保活 + 客户端 onclose 主动断。
     */
    public SseEmitter connect(Long userId) {
        SseEmitter emitter = new SseEmitter(0L); // 0 = 无超时
        List<SseEmitter> list = connections.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>());
        list.add(emitter);

        // 回调：连接正常关闭（客户端主动断）
        emitter.onCompletion(() -> remove(userId, emitter, "completion"));
        // 回调：超时（我们不会超时，但保险起见）
        emitter.onTimeout(() -> remove(userId, emitter, "timeout"));
        // 回调：网络错误
        emitter.onError(e -> remove(userId, emitter, "error: " + e.getMessage()));

        log.info("SSE connected userId={}, totalConnections={}", userId, countTotal());
        return emitter;
    }

    /**
     * 把某个 emitter 从用户列表里移除；如果用户已无连接，清理整个 entry。
     */
    private void remove(Long userId, SseEmitter emitter, String reason) {
        List<SseEmitter> list = connections.get(userId);
        if (list != null) {
            list.remove(emitter);
            if (list.isEmpty()) {
                connections.remove(userId);
            }
        }
        log.info("SSE disconnected userId={}, reason={}, remaining={}", userId, reason, countTotal());
    }

    /**
     * 强制关闭某用户所有连接（用户登出时调用）
     */
    public void disconnectAll(Long userId) {
        List<SseEmitter> list = connections.remove(userId);
        if (list != null) {
            list.forEach(SseEmitter::complete);
            log.info("SSE force disconnect userId={}, count={}", userId, list.size());
        }
    }

    /** 当前活跃连接总数（监控用） */
    public int countTotal() {
        return connections.values().stream().mapToInt(List::size).sum();
    }

    /** 已登录的 SSE 用户数 */
    public int countUsers() {
        return connections.size();
    }

    // ============= 推送 =============

    /**
     * 推送某用户的最新未读数 —— 每次消息变更后调用。
     * 直接查 DB 保证精确（不依赖内存状态，也避免 Service 循环依赖）。
     * 逻辑与 SysMessageServiceImpl.getUnreadCount 完全一致。
     */
    public void pushCount(Long userId) {
        if (userId == null) return;
        List<SseEmitter> list = connections.get(userId);
        if (list == null || list.isEmpty()) return;

        // 直接用 Mapper 查未读数（避免 Service 循环依赖）
        LambdaQueryWrapper<SysMessageUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysMessageUser::getUserId, userId)
               .eq(SysMessageUser::getIsRead, 0)
               .eq(SysMessageUser::getDelFlag, 0);
        Long count = sysMessageUserMapper.selectCount(wrapper);
        String data = String.valueOf(count != null ? count : 0);

        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event()
                        .name("count")   // 事件名，前端用 addEventListener('count', ...)
                        .data(data));
            } catch (IOException e) {
                log.warn("SSE push failed userId={}, error={}", userId, e.getMessage());
                // send 失败说明这个连接已经断了，让 emitter 的 onError 回调去清理
                emitter.complete();
            }
        }
    }

    /** 批量推送（sendToAll 后调用：先拿全量 userId，再逐个 push） */
    public void pushCountToAll(Set<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) return;
        for (Long uid : userIds) {
            pushCount(uid);
        }
    }

    /** 心跳：给所有连接发注释行（SSE 协议里冒号开头的行是注释，客户端忽略） */
    private void sendHeartbeat() {
        if (connections.isEmpty()) return;
        for (Map.Entry<Long, List<SseEmitter>> entry : connections.entrySet()) {
            for (SseEmitter emitter : entry.getValue()) {
                try {
                    emitter.send(SseEmitter.event().comment("hb"));
                } catch (IOException e) {
                    emitter.complete();
                }
            }
        }
    }
}