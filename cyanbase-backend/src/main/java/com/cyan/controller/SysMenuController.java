package com.cyan.controller;


import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
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
import com.cyan.service.SysMenuService;
import com.cyan.common.security.util.SecurityUtils;
import com.cyan.common.util.TreeBuildUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;


@Slf4j
@RestController
@RequestMapping("/system/menu")
@RequiredArgsConstructor
public class SysMenuController {

    // 日志对象
    private static final Logger log = LoggerFactory.getLogger(SysMenuController.class);

@Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SysMenuService sysMenuService;

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
public R<List<SysMenu>> all() {
    String cacheKey = "rbac:menu:flat:all";
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
        wrapper.eq(SysMenu::getStatus, 0).orderByAsc(SysMenu::getSortNum);
        menus = sysMenuService.list(wrapper);
        //存入Redis（原始扁平数据，先存缓存再建树，满足DB→Redis→建树规则）
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
public R<Void> add(@RequestBody SysMenu sysMenu) {
    boolean save = sysMenuService.save(sysMenu);
    //清空全部菜单缓存
    stringRedisTemplate.delete(stringRedisTemplate.keys("rbac:menu:flat:*"));
    return save ? R.ok() : R.fail("新增失败");
}

@PutMapping
public R<Void> update(@RequestBody SysMenu sysMenu) {
    boolean update = sysMenuService.updateById(sysMenu);
    //清空全部菜单缓存
    stringRedisTemplate.delete(stringRedisTemplate.keys("rbac:menu:flat:*"));
    return update ? R.ok() : R.fail("更新失败");
}

@DeleteMapping("/{id}")
public R<Void> delete(@PathVariable Long id) {
    boolean remove = sysMenuService.removeById(id);
    //清空全部菜单缓存
    stringRedisTemplate.delete(stringRedisTemplate.keys("rbac:menu:flat:*"));
    return remove ? R.ok() : R.fail("删除失败");
}

   @GetMapping("/getUserMenu")
    public R<List<SysMenu>> getUserMenu() {
        Long userId = SecurityUtils.getUserId();
        String cacheKey = "rbac:menu:flat:" + userId;
        List<SysMenu> menus = null;

        // 1. 从 Redis 取（绝对不报错）
        String json = stringRedisTemplate.opsForValue().get(cacheKey);
        if (json != null && !json.trim().isEmpty()) {
            try {
                menus = objectMapper.readValue(json, new TypeReference<List<SysMenu>>() {});
            } catch (Exception e) {
                menus = null;
            }
        }

        // 2. 缓存没有 → 查库
        if (menus == null) {
            menus = sysMenuService.selectMenuByUserId(userId);

            try {
                // 存 Redis（标准写法）
                String saveJson = objectMapper.writeValueAsString(menus);
                stringRedisTemplate.opsForValue().set(cacheKey, saveJson, 1, TimeUnit.DAYS);
            } catch (Exception e) {
                log.error("缓存写入失败", e);
            }
        }

        // 3. 建树
        List<SysMenu> treeMenu = TreeBuildUtil.build(menus, 0L);

        return R.ok(treeMenu);
    }
}
