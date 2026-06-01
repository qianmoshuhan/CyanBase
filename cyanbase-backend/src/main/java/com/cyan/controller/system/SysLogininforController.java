package com.cyan.controller.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cyan.common.R;
import com.cyan.entity.SysLogininfor;
import com.cyan.service.SysLogininforService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/system/login/log")
@RequiredArgsConstructor
public class SysLogininforController {
    private final SysLogininforService sysLogininforService;

    @GetMapping("/list")
    public R<?> list(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String loginName,
            @RequestParam(required = false) String ipaddr,
            @RequestParam(required = false) String status) {
        LambdaQueryWrapper<SysLogininfor> wrapper = new LambdaQueryWrapper<>();
        if (loginName != null && !loginName.isEmpty()) wrapper.like(SysLogininfor::getLoginName, loginName);
        if (ipaddr != null && !ipaddr.isEmpty()) wrapper.eq(SysLogininfor::getIpaddr, ipaddr);
        if (status != null && !status.isEmpty()) wrapper.eq(SysLogininfor::getStatus, status);
        wrapper.orderByDesc(SysLogininfor::getLoginTime);
        return R.ok(sysLogininforService.page(new Page<>(current, size), wrapper));
    }

    @DeleteMapping("/{infoId}")
    public R<?> delete(@PathVariable Long infoId) {
        sysLogininforService.removeById(infoId);
        return R.ok("删除成功");
    }

    @DeleteMapping("/clean")
    public R<?> clean() {
        return R.ok("清空成功");
    }
}
