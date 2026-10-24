package com.cyan.controller.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cyan.common.R;
import com.cyan.entity.SysUserOnline;
import com.cyan.security.annotation.RequiresPermission;
import com.cyan.service.SysUserOnlineService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/monitor/online")
@RequiredArgsConstructor
public class SysUserOnlineController {
    private final SysUserOnlineService sysUserOnlineService;

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
        sysUserOnlineService.removeById(sessionId);
        return R.ok("强制下线成功");
    }
}