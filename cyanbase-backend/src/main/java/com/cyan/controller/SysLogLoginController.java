package com.cyan.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cyan.common.R;
import com.cyan.entity.SysLogLogin;
import com.cyan.service.SysLogLoginService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;


@RestController
@RequestMapping("/monitor/login")
@RequiredArgsConstructor
public class SysLogLoginController {

    private final SysLogLoginService sysLogLoginService;

    @GetMapping("/list")
    public R<Page<SysLogLogin>> list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String userName,
            @RequestParam(required = false) String ipaddr,
            @RequestParam(required = false) Integer status) {

        LambdaQueryWrapper<SysLogLogin> wrapper = new LambdaQueryWrapper<>();
        if (userName != null && !userName.isEmpty()) {
            wrapper.like(SysLogLogin::getUserName, userName);
        }
        if (ipaddr != null && !ipaddr.isEmpty()) {
            wrapper.eq(SysLogLogin::getIpaddr, ipaddr);
        }
        if (status != null) {
            wrapper.eq(SysLogLogin::getStatus, status);
        }
        wrapper.orderByDesc(SysLogLogin::getLoginTime);

        Page<SysLogLogin> page = new Page<>(pageNum, pageSize);
        sysLogLoginService.page(page, wrapper);
        return R.ok(page);
    }

    @GetMapping("/{id}")
    public R<SysLogLogin> getById(@PathVariable Long id) {
        SysLogLogin log = sysLogLoginService.getById(id);
        return log != null ? R.ok(log) : R.fail("日志不存在");
    }
}