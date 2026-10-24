package com.cyan.controller.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cyan.common.R;
import com.cyan.entity.SysConfig;
import com.cyan.security.annotation.RequiresPermission;
import com.cyan.security.aspect.PermissionGuard;
import com.cyan.security.datascope.ResourceScope;
import com.cyan.service.SysConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/system/config")
@RequiredArgsConstructor
public class SysConfigController {
    private final SysConfigService sysConfigService;
    private final PermissionGuard permissionGuard;

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
        wrapper.orderByDesc(SysConfig::getId);
        return R.ok(sysConfigService.page(new Page<>(current, size), wrapper));
    }

    @GetMapping("/{configId}")
    public R<?> getById(@PathVariable Long configId) {
        SysConfig config = sysConfigService.getById(configId);
        if (config == null) return R.fail("参数不存在");
        // 读操作：L1 租户身份 + L2 DataScope
        permissionGuard.check(ResourceScope.of(null, null, config.getCreateBy()));
        return R.ok(config);
    }

    @PostMapping
    @RequiresPermission("system:config:add")
    public R<?> add(@RequestBody SysConfig config) {
        config.setId(null);
        sysConfigService.save(config);
        return R.ok("新增成功");
    }

    @PutMapping
    @RequiresPermission("system:config:edit")
    public R<?> update(@RequestBody SysConfig config) {
        SysConfig exist = sysConfigService.getById(config.getId());
        if (exist == null) return R.fail("参数不存在");
        // 写操作：完整三层 L2 SELF 范围防越权
        permissionGuard.check(ResourceScope.of(null, null, exist.getCreateBy()),
                              "system:config:edit");
        sysConfigService.updateById(config);
        return R.ok("更新成功");
    }

    @DeleteMapping("/{configId}")
    @RequiresPermission("system:config:remove")
    public R<?> delete(@PathVariable Long configId) {
        SysConfig exist = sysConfigService.getById(configId);
        if (exist == null) return R.fail("参数不存在");
        // 写操作：完整三层校验
        permissionGuard.check(ResourceScope.of(null, null, exist.getCreateBy()),
                              "system:config:remove");
        sysConfigService.removeById(configId);
        return R.ok("删除成功");
    }

    @GetMapping("/key/{configKey}")
    public R<String> getByKey(@PathVariable String configKey){
        LambdaQueryWrapper<SysConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysConfig::getConfigKey,configKey);
        SysConfig one = sysConfigService.getOne(wrapper);
        return R.ok(one==null?"":one.getConfigValue());
    }
}