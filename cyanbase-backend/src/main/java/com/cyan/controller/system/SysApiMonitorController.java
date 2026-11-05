package com.cyan.controller.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cyan.common.R;
import com.cyan.entity.SysApiMonitor;
import com.cyan.security.annotation.RequiresPermission;
import com.cyan.service.SysApiMonitorService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/monitor/apiMonitor")
@RequiredArgsConstructor
public class SysApiMonitorController {
    private final SysApiMonitorService sysApiMonitorService;

    @RequiresPermission("monitor:api:list")
    @GetMapping("/list")
    public R<?> list(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String apiName,
            @RequestParam(required = false) String apiUrl) {
        LambdaQueryWrapper<SysApiMonitor> wrapper = new LambdaQueryWrapper<>();
        if (apiName != null && !apiName.isEmpty()) wrapper.like(SysApiMonitor::getApiName, apiName);
        if (apiUrl != null && !apiUrl.isEmpty()) wrapper.like(SysApiMonitor::getApiUrl, apiUrl);
        wrapper.orderByDesc(SysApiMonitor::getReqCount);
        return R.ok(sysApiMonitorService.page(new Page<>(current, size), wrapper));
    }

    @GetMapping("/{id}")
    public R<?> getById(@PathVariable Long id) {
        return R.ok(sysApiMonitorService.getById(id));
    }

    @PostMapping
    public R<?> add(@RequestBody SysApiMonitor monitor) {
        sysApiMonitorService.save(monitor);
        return R.ok("新增成功");
    }

    @PutMapping
    @RequiresPermission("monitor:api:edit")
    public R<?> update(@RequestBody SysApiMonitor monitor) {
        sysApiMonitorService.updateById(monitor);
        return R.ok("更新成功");
    }

    @DeleteMapping("/{id}")
    @RequiresPermission("monitor:api:delete")
    public R<?> delete(@PathVariable Long id) {
        sysApiMonitorService.removeById(id);
        return R.ok("删除成功");
    }
}