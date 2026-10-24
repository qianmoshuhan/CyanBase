package com.cyan.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cyan.common.R;
import com.cyan.entity.SysTenant;
import com.cyan.entity.SysTenantMenu;
import com.cyan.mapper.SysTenantMenuMapper;
import com.cyan.security.annotation.RequiresPermission;
import com.cyan.security.aspect.PermissionGuard;
import com.cyan.security.datascope.ResourceScope;
import com.cyan.service.SysTenantService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/system/tenant")
@RequiredArgsConstructor
public class SysTenantController {

    private final SysTenantService sysTenantService;
    private final SysTenantMenuMapper tenantMenuMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final PermissionGuard permissionGuard;

    /**
     * 菜单缓存 key 前缀（和 SysMenuController.getUserMenu 保持一致）
     * 完整格式：rbac:menu:flat:{userId}:{tenantId}
     */
    private static final String MENU_CACHE_PREFIX = "rbac:menu:flat:";

    /**
     * 根路径 GET 映射，避免浏览器直接访问 /system/tenant 时报 HttpRequestMethodNotSupportedException
     * 重定向到 list 接口
     */
    @GetMapping
    public R<Page<SysTenant>> root(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String tenantName,
            @RequestParam(required = false) String tenantCode,
            @RequestParam(required = false) Integer status) {
        return list(pageNum, pageSize, tenantName, tenantCode, status);
    }

    @GetMapping("/list")
    public R<Page<SysTenant>> list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String tenantName,
            @RequestParam(required = false) String tenantCode,
            @RequestParam(required = false) Integer status) {

        LambdaQueryWrapper<SysTenant> wrapper = new LambdaQueryWrapper<>();
        if (tenantName != null && !tenantName.isEmpty()) {
            wrapper.like(SysTenant::getTenantName, tenantName);
        }
        if (tenantCode != null && !tenantCode.isEmpty()) {
            wrapper.eq(SysTenant::getTenantCode, tenantCode);
        }
        if (status != null) {
            wrapper.eq(SysTenant::getStatus, status);
        }
        wrapper.orderByDesc(SysTenant::getCreateTime);

        Page<SysTenant> page = new Page<>(pageNum, pageSize);
        sysTenantService.page(page, wrapper);
        return R.ok(page);
    }

    @GetMapping("/{id}")
    public R<SysTenant> getById(@PathVariable Long id) {
        SysTenant tenant = sysTenantService.getById(id);
        if (tenant == null) return R.fail("租户不存在");
        // 读操作：L1 租户身份 + L2 DataScope（平台级对象，tenantId=null 跳过 L1 归属比对）
        permissionGuard.check(ResourceScope.of(null, null, tenant.getCreateBy()));
        return R.ok(tenant);
    }

    /**
     * 获取全部启用的租户（不分页，给前端下拉选）
     */
    @GetMapping("/all")
    public R<List<SysTenant>> all() {
        LambdaQueryWrapper<SysTenant> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysTenant::getStatus, 0)
                .orderByAsc(SysTenant::getId);
        return R.ok(sysTenantService.list(wrapper));
    }

    @PostMapping
    @RequiresPermission("system:tenant:add")
    public R<SysTenant> add(@RequestBody SysTenant sysTenant) {
        // ⚠️ 防御：强制清空 id，防止前端残留 id 造成主键冲突
        sysTenant.setId(null);
        // 纯新建场景：targetScope=null，只过 L1 租户身份 + L3 system:tenant:add
        permissionGuard.check((ResourceScope) null, "system:tenant:add");
        if (sysTenantService.save(sysTenant)) {
            // MyBatis-Plus save 后自动回填自增ID到原对象，直接返回
            return R.ok(sysTenant);
        }
        return R.fail("新增失败");
    }

    @PutMapping
    @RequiresPermission("system:tenant:edit")
    public R<Void> update(@RequestBody SysTenant sysTenant) {
        SysTenant exist = sysTenantService.getById(sysTenant.getId());
        if (exist == null) return R.fail("租户不存在");
        // 写操作：完整三层（tenantId=null 跳过 L1 归属比对）
        permissionGuard.check(ResourceScope.of(null, null, exist.getCreateBy()),
                              "system:tenant:edit");
        return sysTenantService.updateById(sysTenant) ? R.ok() : R.fail("更新失败");
    }

    @DeleteMapping("/{id}")
    @RequiresPermission("system:tenant:remove")
    public R<Void> delete(@PathVariable Long id) {
        SysTenant exist = sysTenantService.getById(id);
        if (exist == null) return R.fail("租户不存在");
        // 写操作：完整三层
        permissionGuard.check(ResourceScope.of(null, null, exist.getCreateBy()),
                              "system:tenant:remove");
        return sysTenantService.removeById(id) ? R.ok() : R.fail("删除失败");
    }

    /**
     * 查询指定租户已分配的菜单ID列表
     */
    @GetMapping("/{tenantId}/menu-ids")
    public R<List<Long>> getTenantMenuIds(@PathVariable Long tenantId) {
        // 读操作：L1 租户身份校验（平台级对象）
        permissionGuard.check((ResourceScope) null);
        LambdaQueryWrapper<SysTenantMenu> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysTenantMenu::getTenantId, tenantId);
        List<Long> menuIds = tenantMenuMapper.selectList(wrapper).stream()
                .map(SysTenantMenu::getMenuId).collect(Collectors.toList());
        return R.ok(menuIds);
    }

    /**
     * 保存租户菜单分配（全量覆盖）
     */
    @PutMapping("/{tenantId}/menus")
    @Transactional(rollbackFor = Exception.class)
    @RequiresPermission("system:tenant:edit")
    public R<Void> saveTenantMenus(@PathVariable Long tenantId, @RequestBody List<Long> menuIds) {
        // 写操作：先查租户存在性 + 完整三层校验
        SysTenant exist = sysTenantService.getById(tenantId);
        if (exist == null) return R.fail("租户不存在");
        permissionGuard.check(ResourceScope.of(null, null, exist.getCreateBy()),
                              "system:tenant:edit");
        // 先删除旧关联
        LambdaQueryWrapper<SysTenantMenu> delWrapper = new LambdaQueryWrapper<>();
        delWrapper.eq(SysTenantMenu::getTenantId, tenantId);
        tenantMenuMapper.delete(delWrapper);
        // 批量插入新关联
        if (menuIds != null && !menuIds.isEmpty()) {
            for (Long menuId : menuIds) {
                SysTenantMenu tm = new SysTenantMenu();
                tm.setTenantId(tenantId);
                tm.setMenuId(menuId);
                tenantMenuMapper.insert(tm);
            }
        }
        // 关键：清 Redis 菜单缓存！否则租户用户还拿旧菜单
        evictMenuCacheByTenant(tenantId);
        log.info("[saveTenantMenus] tenantId={} 菜单保存完成，已清缓存", tenantId);
        return R.ok();
    }

    /**
     * 清除指定租户下所有用户的菜单缓存
     * 缓存 key 格式：rbac:menu:flat:{userId}:{tenantId}
     */
    private void evictMenuCacheByTenant(Long tenantId) {
        try {
            Set<String> keys = stringRedisTemplate.keys(MENU_CACHE_PREFIX + "*:" + tenantId);
            if (keys != null && !keys.isEmpty()) {
                stringRedisTemplate.delete(keys);
                log.info("[evictMenuCache] tenantId={}, 清掉 {} 个菜单缓存", tenantId, keys.size());
            } else {
                log.info("[evictMenuCache] tenantId={}, 无缓存需清理", tenantId);
            }
        } catch (Exception e) {
            log.warn("[evictMenuCache] 清缓存失败: {}", e.getMessage());
        }
    }
}