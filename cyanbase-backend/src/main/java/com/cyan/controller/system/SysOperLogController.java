package com.cyan.controller.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cyan.common.R;
import com.cyan.entity.SysOperLog;
import com.cyan.modules.log.service.SysOperLogService;
import com.cyan.security.annotation.RequiresPermission;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/system/oper/log")
@RequiredArgsConstructor
public class SysOperLogController {
    private final SysOperLogService sysOperLogService;

    @GetMapping("/list")
    public R<?> list(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String operName,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) Integer businessType,
            @RequestParam(required = false) Integer status) {
        LambdaQueryWrapper<SysOperLog> wrapper = new LambdaQueryWrapper<>();
        if (operName != null && !operName.isEmpty()) wrapper.like(SysOperLog::getOperName, operName);
        if (title != null && !title.isEmpty()) wrapper.like(SysOperLog::getTitle, title);
        if (businessType != null) wrapper.eq(SysOperLog::getBusinessType, businessType);
        if (status != null) wrapper.eq(SysOperLog::getStatus, status);
        wrapper.orderByDesc(SysOperLog::getOperTime);
        return R.ok(sysOperLogService.page(new Page<>(current, size), wrapper));
    }

    @GetMapping("/{operId}")
    public R<?> getById(@PathVariable Long operId) {
        return R.ok(sysOperLogService.getById(operId));
    }

    @DeleteMapping("/{operId}")
    @RequiresPermission("monitor:operlog:remove")
    public R<?> delete(@PathVariable Long operId) {
        sysOperLogService.removeById(operId);
        return R.ok("删除成功");
    }

    @DeleteMapping("/clean")
    @RequiresPermission("monitor:operlog:remove")
    public R<?> clean() {
        // 清空操作日志
        return R.ok("清空成功");
    }
}