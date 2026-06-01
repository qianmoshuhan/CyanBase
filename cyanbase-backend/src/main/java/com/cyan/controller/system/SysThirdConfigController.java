package com.cyan.controller.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cyan.common.R;
import com.cyan.entity.SysThirdConfig;
import com.cyan.service.SysThirdConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/system/third/config")
@RequiredArgsConstructor
public class SysThirdConfigController {
    private final SysThirdConfigService sysThirdConfigService;

    @GetMapping("/list")
    public R<?> list(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String configType,
            @RequestParam(required = false) String configName,
            @RequestParam(required = false) String status) {
        LambdaQueryWrapper<SysThirdConfig> wrapper = new LambdaQueryWrapper<>();
        if (configType != null && !configType.isEmpty()) wrapper.eq(SysThirdConfig::getConfigType, configType);
        if (configName != null && !configName.isEmpty()) wrapper.like(SysThirdConfig::getConfigName, configName);
        if (status != null && !status.isEmpty()) wrapper.eq(SysThirdConfig::getStatus, status);
        wrapper.orderByDesc(SysThirdConfig::getId);
        return R.ok(sysThirdConfigService.page(new Page<>(current, size), wrapper));
    }

    @GetMapping("/{id}")
    public R<?> getById(@PathVariable Long id) {
        return R.ok(sysThirdConfigService.getById(id));
    }

    @PostMapping
    public R<?> add(@RequestBody SysThirdConfig config) {
        sysThirdConfigService.save(config);
        return R.ok("新增成功");
    }

    @PutMapping
    public R<?> update(@RequestBody SysThirdConfig config) {
        sysThirdConfigService.updateById(config);
        return R.ok("更新成功");
    }

    @DeleteMapping("/{id}")
    public R<?> delete(@PathVariable Long id) {
        sysThirdConfigService.removeById(id);
        return R.ok("删除成功");
    }
}
