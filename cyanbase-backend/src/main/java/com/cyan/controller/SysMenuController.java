package com.cyan.controller;


import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.concurrent.TimeUnit;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.cyan.common.R;
import com.cyan.entity.SysMenu;
import com.cyan.entity.SysTenantMenu;
import com.cyan.mapper.SysTenantMenuMapper;
import com.cyan.service.SysMenuService;
import com.cyan.common.security.util.SecurityUtils;
import com.cyan.common.util.TreeBuildUtil;
import com.cyan.security.annotation.RequiresPermission;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;


@Slf4j
@RestController
@RequestMapping("/system/menu")
@RequiredArgsConstructor
public class SysMenuController {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SysMenuService sysMenuService;

    @Autowired
    private SysTenantMenuMapper tenantMenuMapper;

    private static final String MENU_CACHE_KEY = "user:menu:";

    @GetMapping("/list")
    public R<Page<SysMenu>> list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String menuName,
            @RequestParam(required = false) Integer menuType,
            @RequestParam(required = false) Integer status) {

        LambdaQueryWrapper<SysMenu> wrapper = new LambdaQueryWrapper<>();
        if (menuName != null && !menuName.isEmpty()) {
            wrapper.like(SysMenu::getMenuName, menuName);
        }
        if (menuType != null) {
            wrapper.eq(SysMenu::getMenuType, menuType);
        }
        if (status != null) {
            wrapper.eq(SysMenu::getStatus, status);
        }
        wrapper.orderByAsc(SysMenu::getSortNum);

        Page<SysMenu> page = new Page<>(pageNum, pageSize);
        sysMenuService.page(page, wrapper);
        return R.ok(page);
    }

@GetMapping("/all")
public R<List<SysMenu>> all(
        @RequestParam(defaultValue = "false") Boolean includeDisabled) {
    // 缓存 key 区分是否含禁用菜单，避免租户分配场景和菜单管理场景串数据
    String cacheKey = includeDisabled ? "rbac:menu:flat:all:withDisabled" : "rbac:menu:flat:all";
    List<SysMenu> menus = null;

    //1.Redis读取扁平菜单
    String json = stringRedisTemplate.opsForValue().get(cacheKey);
    if (json != null && !json.trim().isEmpty()) {
        try {
            menus = objectMapper.readValue(json, new TypeReference<List<SysMenu>>() {});
        } catch (Exception e) {
            menus = null;
        }
    }

    //2.无缓存查询数据库
    if (menus == null) {
        LambdaQueryWrapper<SysMenu> wrapper = new LambdaQueryWrapper<>();
        if (!includeDisabled) {
            wrapper.eq(SysMenu::getStatus, 0);
        }
        wrapper.orderByAsc(SysMenu::getSortNum);
        menus = sysMenuService.list(wrapper);
        try {
            String saveJson = objectMapper.writeValueAsString(menus);
            stringRedisTemplate.opsForValue().set(cacheKey, saveJson, 1, TimeUnit.DAYS);
        } catch (Exception e) {
            log.error("全部菜单缓存写入异常", e);
        }
    }

    //3.使用现有TreeBuildUtil构建树形
    List<SysMenu> treeMenu = TreeBuildUtil.build(menus, 0L);
    return R.ok(treeMenu);
}

    @GetMapping("/{id}")
    public R<SysMenu> getById(@PathVariable Long id) {
        SysMenu menu = sysMenuService.getById(id);
        return menu != null ? R.ok(menu) : R.fail("菜单不存在");
    }

@PostMapping
@RequiresPermission("system:menu:add")
public R<Void> add(@RequestBody SysMenu sysMenu) {
    sysMenu.setId(null);
    boolean save = sysMenuService.save(sysMenu);
    //清空全部菜单缓存
    stringRedisTemplate.delete(stringRedisTemplate.keys("rbac:menu:flat:*"));
    return save ? R.ok() : R.fail("新增失败");
}

@PutMapping
@RequiresPermission("system:menu:edit")
public R<Void> update(@RequestBody SysMenu sysMenu) {
    boolean update = sysMenuService.updateById(sysMenu);
    //清空全部菜单缓存
    stringRedisTemplate.delete(stringRedisTemplate.keys("rbac:menu:flat:*"));
    return update ? R.ok() : R.fail("更新失败");
}

@DeleteMapping("/{id}")
@RequiresPermission("system:menu:remove")
public R<Void> delete(@PathVariable Long id) {
    boolean remove = sysMenuService.removeById(id);
    //清空全部菜单缓存
    stringRedisTemplate.delete(stringRedisTemplate.keys("rbac:menu:flat:*"));
    return remove ? R.ok() : R.fail("删除失败");
}

   @GetMapping("/getUserMenu")
    public R<List<SysMenu>> getUserMenu() {
        Long userId = SecurityUtils.getUserId();
        Long tenantId = SecurityUtils.getTenantId();
        log.info("[getUserMenu] userId={}, tenantId={}", userId, tenantId);

        // 缓存key带上tenantId，避免不同租户串菜单
        String cacheKey = "rbac:menu:flat:" + userId + ":" + (tenantId != null ? tenantId : "root");
        List<SysMenu> menus = null;

        // 1. 从 Redis 取
        String json = stringRedisTemplate.opsForValue().get(cacheKey);
        if (json != null && !json.trim().isEmpty()) {
            log.info("[getUserMenu] 命中Redis缓存, cacheKey={}", cacheKey);
            try {
                menus = objectMapper.readValue(json, new TypeReference<List<SysMenu>>() {});
            } catch (Exception e) {
                menus = null;
            }
        }

        // 2. 缓存没有 → 查库
        if (menus == null) {
            // 2a. 按角色查出完整菜单
            menus = sysMenuService.selectMenuByUserId(userId);
            log.info("[getUserMenu] 角色菜单数={}", menus != null ? menus.size() : 0);

            // 2b. 租户用户还要二次过滤：角色菜单 ∩ 租户分配菜单
            if (tenantId != null) {
                menus = filterByTenant(menus, tenantId);
            }
            log.info("[getUserMenu] 最终菜单数={}", menus != null ? menus.size() : 0);

            try {
                String saveJson = objectMapper.writeValueAsString(menus);
                stringRedisTemplate.opsForValue().set(cacheKey, saveJson, 1, TimeUnit.DAYS);
            } catch (Exception e) {
                log.error("缓存写入失败", e);
            }
        }

        // 3. 建树
        List<SysMenu> treeMenu = TreeBuildUtil.build(menus, 0L);
        log.info("[getUserMenu] 建树后菜单数={}", treeMenu != null ? treeMenu.size() : 0);
        return R.ok(treeMenu);
    }

    /**
     * 按租户分配过滤菜单：角色菜单 ∩ 租户已分配菜单
     * <p>
     * 关键点：交集后必须补齐所有父节点，否则 TreeBuildUtil.build 构建不出完整树。
     * 和 SysMenuServiceImpl.selectMenuByUserId 中的 collectParentIds 逻辑对齐。
     */
    private List<SysMenu> filterByTenant(List<SysMenu> roleMenus, Long tenantId) {
        // 1. 查出该租户已分配的菜单ID
        LambdaQueryWrapper<SysTenantMenu> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysTenantMenu::getTenantId, tenantId);
        java.util.Set<Long> tenantMenuIds = tenantMenuMapper.selectList(wrapper).stream()
                .map(SysTenantMenu::getMenuId)
                .collect(java.util.stream.Collectors.toSet());

        log.info("[filterByTenant] tenantId={}, 租户分配菜单数={}, 角色菜单数={}", tenantId, tenantMenuIds.size(), roleMenus != null ? roleMenus.size() : 0);
        log.info("[filterByTenant] 租户菜单IDs={}", tenantMenuIds);

        if (roleMenus == null || roleMenus.isEmpty()) return List.of();

        // 2. 先取直接命中的交集
        List<SysMenu> directHits = roleMenus.stream()
                .filter(m -> tenantMenuIds.contains(m.getId()))
                .collect(java.util.stream.Collectors.toList());

        // 3. 构建 roleMenus 的 id → node 映射（已经补齐了所有父节点）
        java.util.Map<Long, SysMenu> roleMenuMap = new java.util.HashMap<>();
        for (SysMenu m : roleMenus) roleMenuMap.put(m.getId(), m);

        // 4. 递归向上补齐所有父节点（和 SysMenuServiceImpl.collectParentIds 逻辑一致）
        java.util.Set<Long> finalIds = new java.util.HashSet<>();
        for (SysMenu hit : directHits) {
            collectParentsIntoSet(hit.getId(), roleMenuMap, finalIds);
        }

        // 5. 返回完整菜单：直接命中 + 所有补齐的父节点
        List<SysMenu> result = roleMenus.stream()
                .filter(m -> finalIds.contains(m.getId()))
                .collect(java.util.stream.Collectors.toList());

        log.info("[filterByTenant] 补齐父节点后菜单数={}", result.size());
        return result;
    }

    /**
     * 递归向上收集菜单的所有父节点 ID（不查库，直接从 roleMenus 内存 Map 中找）
     */
    private void collectParentsIntoSet(Long menuId, java.util.Map<Long, SysMenu> menuMap, java.util.Set<Long> result) {
        if (menuId == null || result.contains(menuId)) return;
        SysMenu node = menuMap.get(menuId);
        if (node == null) return;
        result.add(menuId);
        if (node.getParentId() != null && node.getParentId() != 0) {
            collectParentsIntoSet(node.getParentId(), menuMap, result);
        }
    }
}