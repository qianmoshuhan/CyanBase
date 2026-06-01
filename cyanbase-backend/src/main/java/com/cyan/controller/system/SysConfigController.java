package com.cyan.controller.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cyan.common.R;
import com.cyan.entity.SysConfig;
import com.cyan.service.SysConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/system/config")
@RequiredArgsConstructor
public class SysConfigController {
    private final SysConfigService sysConfigService;

    @GetMapping("/list")
    public R<?> list(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String configName,
            @RequestParam(required = false) String configKey,
            @RequestParam(required = false) String configType) {
        LambdaQueryWrapper<SysConfig> wrapper = new LambdaQueryWrapper<>();
        if (configName != null && !configName.isEmpty()) wrapper.like(SysConfig::getConfigName, configName);
        if (configKey != null && !configKey.isEmpty()) wrapper.eq(SysConfig::getConfigKey, configKey);
        if (configType != null && !configType.isEmpty()) wrapper.eq(SysConfig::getConfigType, configType);
        wrapper.orderByDesc(SysConfig::getConfigId);
        return R.ok(sysConfigService.page(new Page<>(current, size), wrapper));
    }

    @GetMapping("/{configId}")
    public R<?> getById(@PathVariable Long configId) {
        return R.ok(sysConfigService.getById(configId));
    }

    @PostMapping
    public R<?> add(@RequestBody SysConfig config) {
        sysConfigService.save(config);
        return R.ok("新增成功");
    }

    @PutMapping
    public R<?> update(@RequestBody SysConfig config) {
        sysConfigService.updateById(config);
        return R.ok("更新成功");
    }

    @DeleteMapping("/{configId}")
    public R<?> delete(@PathVariable Long configId) {
        sysConfigService.removeById(configId);
        return R.ok("删除成功");
    }
}
