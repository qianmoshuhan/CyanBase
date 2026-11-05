package com.cyan.modules.message.controller;

import com.cyan.common.security.util.SecurityUtils;
import com.cyan.modules.message.service.SseEmitterManager;
import com.cyan.modules.message.service.SysMessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

/**
 * SSE 推送端点 —— 给前端铃铛实时推未读数
 * <p>
 * 路由：/system/message/sse
 * 鉴权：JwtAuthenticationFilter 先于 Controller 执行，解析 header 或 query?token= 里的 JWT，
 *       把 LoginUser 塞进 SecurityContextHolder；Controller 里直接 SecurityUtils.getUserId() 拿。
 * 租户隔离：JWT 自带 tenantId，sys_message_user 按 userId 查，天然隔离。
 * 多标签页：SseEmitterManager 按 userId 存 List<SseEmitter>，每个标签页一个连接。
 */
@Slf4j
@RestController
@RequestMapping("/system/message")
@RequiredArgsConstructor
public class SseController {

    private final SseEmitterManager sseEmitterManager;
    private final SysMessageService sysMessageService;

    @GetMapping(value = "/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter sse() {
        Long userId = SecurityUtils.getUserId();
        if (userId == null) {
            // ⚠️ token 过期或未登录：返回一个立即结束的 SSE，告诉前端关掉
            SseEmitter fail = new SseEmitter();
            try {
                fail.send(SseEmitter.event().name("error").data("unauthorized"));
            } catch (IOException ignored) {}
            fail.complete();
            return fail;
        }

        SseEmitter emitter = sseEmitterManager.connect(userId);

        // 建连成功立即推当前未读数
        try {
            Long count = sysMessageService.getUnreadCount(userId);
            emitter.send(SseEmitter.event()
                    .name("count")
                    .data(String.valueOf(count != null ? count : 0)));
        } catch (Exception e) {
            log.warn("SSE 初始推送失败 userId={}", userId);
        }

        return emitter;
    }
}