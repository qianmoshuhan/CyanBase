package com.cyan.controller.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cyan.common.R;
import com.cyan.entity.SysUserOnline;
import com.cyan.security.annotation.RequiresPermission;
import com.cyan.security.service.RefreshTokenService;
import com.cyan.service.SysUserOnlineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/monitor/online")
@RequiredArgsConstructor
public class SysUserOnlineController {
    private final SysUserOnlineService sysUserOnlineService;
    private final RefreshTokenService refreshTokenService;

    @RequiresPermission("monitor:online:list")
    @GetMapping("/list")
    public R<?> list(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String loginName,
            @RequestParam(required = false) String ipaddr) {
        LambdaQueryWrapper<SysUserOnline> wrapper = new LambdaQueryWrapper<>();
        if (loginName != null && !loginName.isEmpty()) wrapper.like(SysUserOnline::getLoginName, loginName);
        if (ipaddr != null && !ipaddr.isEmpty()) wrapper.eq(SysUserOnline::getIpaddr, ipaddr);
        wrapper.orderByDesc(SysUserOnline::getLoginTime);
        Page<SysUserOnline> page = new Page<>(current, size);
        return R.ok(sysUserOnlineService.page(page, wrapper));
    }

    @DeleteMapping("/{sessionId}")
    @RequiresPermission("monitor:online:kick")
    public R<?> delete(@PathVariable String sessionId) {
        // ⭐ sessionId 格式是 "u_{userId}"，从中提取 userId
        Long userId = null;
        if (sessionId != null && sessionId.startsWith("u_")) {
            try {
                userId = Long.parseLong(sessionId.substring(2));
            } catch (NumberFormatException ignored) {
            }
        }

        // ① 从 Redis 踢下线（删 Refresh Token + 写踢标记，让 Access Token 后续请求 401）
        if (userId != null) {
            refreshTokenService.kickUser(userId);
            log.info("管理员强制下线 userId={}（sessionId={}）", userId, sessionId);
        }

        // ② 删在线记录
        sysUserOnlineService.removeById(sessionId);
        return R.ok("强制下线成功");
    }
}