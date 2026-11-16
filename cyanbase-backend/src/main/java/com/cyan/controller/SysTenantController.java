package com.cyan.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cyan.common.R;
import com.cyan.common.security.util.SecurityUtils;
import com.cyan.dto.TenantDetailVO;
import com.cyan.dto.TenantModuleItemVO;
import com.cyan.entity.SysTenant;
import com.cyan.entity.SysTenantMenu;
import com.cyan.entity.SysTenantModule;
import com.cyan.entity.SysUser;
import com.cyan.mapper.SysTenantMenuMapper;
import com.cyan.mapper.SysTenantModuleMapper;
import com.cyan.mapper.SysUserMapper;
import com.cyan.security.annotation.RequiresPermission;
import com.cyan.security.aspect.PermissionGuard;
import com.cyan.security.datascope.ResourceScope;
import com.cyan.security.domain.LoginUser;
import com.cyan.service.PrincipalTenantScopeService;
import com.cyan.service.SysTenantService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
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
    private final SysTenantModuleMapper tenantModuleMapper;
    private final SysUserMapper sysUserMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final PermissionGuard permissionGuard;
    private final PrincipalTenantScopeService principalTenantScopeService;

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
        LocalDateTime now = LocalDateTime.now();
        if (status != null) {
            if (status == 2) {
                // ⭐ "已过期" = 数据库已标过期(2) OR 正常(0)但到期时间已过
                wrapper.and(w -> w.eq(SysTenant::getStatus, 2)
                                .or()
                                .eq(SysTenant::getStatus, 0)
                                .isNotNull(SysTenant::getExpireDate)
                                .lt(SysTenant::getExpireDate, now));
            } else {
                wrapper.eq(SysTenant::getStatus, status);
            }
        }
        wrapper.orderByDesc(SysTenant::getCreateTime);

        Page<SysTenant> page = new Page<>(pageNum, pageSize);
        sysTenantService.page(page, wrapper);

        // ⭐ 二次修正：数据库里 status 可能还是 0（新建默认），但 expireDate 已过期的，
        // 返回给前端时强制改成 2，保证列表显示正确的过期状态
        for (SysTenant t : page.getRecords()) {
            if (t.getStatus() != null && t.getStatus() == 0
                    && t.getExpireDate() != null
                    && t.getExpireDate().isBefore(now)) {
                t.setStatus(2);
            }
        }
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
     * 租户详情聚合接口（只读，独立于 list）
     * 返回：基础信息 + 已用配额 + 已开通模块列表
     */
    @GetMapping("/{id}/detail")
    public R<TenantDetailVO> detail(@PathVariable Long id) {
        SysTenant tenant = sysTenantService.getById(id);
        if (tenant == null) return R.fail("租户不存在");

        // 读操作：L1 租户身份 + L2 DataScope（平台级对象，tenantId=null 跳过 L1 归属比对）
        permissionGuard.check(ResourceScope.of(null, null, tenant.getCreateBy()));

        // 1) 统计已用配额
        LambdaQueryWrapper<SysUser> userWrapper = new LambdaQueryWrapper<>();
        userWrapper.eq(SysUser::getTenantId, id);
        int usedUserNum = sysUserMapper.selectCount(userWrapper).intValue();

        // 2) 已开通模块列表
        LambdaQueryWrapper<SysTenantModule> moduleWrapper = new LambdaQueryWrapper<>();
        moduleWrapper.eq(SysTenantModule::getTenantId, id)
                     .orderByAsc(SysTenantModule::getModuleCode);
        List<TenantModuleItemVO> modules = tenantModuleMapper.selectList(moduleWrapper).stream()
                .map(m -> {
                    TenantModuleItemVO vo = new TenantModuleItemVO();
                    vo.setModuleCode(m.getModuleCode());
                    vo.setModuleName(m.getModuleName());
                    vo.setEnabled(m.getEnabled());
                    vo.setValidFrom(m.getValidFrom());
                    vo.setValidTo(m.getValidTo());
                    vo.setRemark(m.getRemark());
                    return vo;
                })
                .collect(Collectors.toList());

        // 3) 组装详情 VO
        TenantDetailVO detail = new TenantDetailVO();
        detail.setId(tenant.getId());
        detail.setTenantName(tenant.getTenantName());
        detail.setTenantCode(tenant.getTenantCode());
        detail.setContactUser(tenant.getContactUser());
        detail.setContactPhone(tenant.getContactPhone());
        detail.setAddress(tenant.getAddress());
        detail.setStatus(tenant.getStatus());
        detail.setExpireDate(tenant.getExpireDate());
        detail.setMaxUserNum(tenant.getMaxUserNum());
        detail.setUsedUserNum(usedUserNum);
        detail.setRemark(tenant.getRemark());
        detail.setCreateBy(tenant.getCreateBy());
        detail.setCreateTime(tenant.getCreateTime());
        detail.setModules(modules);

        // 4) 过期状态二次修正（和 list 接口保持一致：status=0 但 expire_date 已过 → 显示 2）
        LocalDateTime now = LocalDateTime.now();
        if (detail.getStatus() != null && detail.getStatus() == 0
                && detail.getExpireDate() != null
                && detail.getExpireDate().isBefore(now)) {
            detail.setStatus(2);
        }

        return R.ok(detail);
    }

    /**
     * 获取全部启用的租户（不分页，给前端下拉选）
     * <p>⚠️ 此接口返回全部启用租户 + tenantId=null 的平台主体（由数据库决定），
     * 给租户管理页面、用户搜索等通用场景复用。<br>
     * 平台侧新增用户弹窗请使用 /system/tenant/authorized（按操作者授权范围过滤）。</p>
     */
    @GetMapping("/all")
    public R<List<SysTenant>> all() {
        LambdaQueryWrapper<SysTenant> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysTenant::getStatus, 0)
                .orderByAsc(SysTenant::getId);
        return R.ok(sysTenantService.list(wrapper));
    }

    /**
     * 获取当前操作者「可授权的租户列表」
     * <p>专供平台侧新增用户弹窗下拉，不在前端做二次过滤，后端直接按身份返回合法范围。</p>
     *
     * <h4>身份判定链：</h4>
     * <ul>
     *   <li>普通租户账号（rawTenantId &gt; 0）→ 直接拒绝调用</li>
     *   <li>root 超管（rawTenantId == 0）→ 返回全部启用租户</li>
     *   <li>平台主体（rawTenantId == null, platformPrincipal=true）→
     *       查 PrincipalTenantScope 关联表，无分配则返回空列表</li>
     * </ul>
     *
     * @return 可授权的启用租户列表（tenantId 均 ≥ 1，不含平台主体项）
     */
    @GetMapping("/authorized")
    public R<List<SysTenant>> authorized() {
        LoginUser currentUser = SecurityUtils.getLoginUser();
        if (currentUser == null) return R.fail("未登录");

        // ⚠️ 必须用 getRawTenantId：getTenantId() 会被 activeTenantId 替换，
        // 导致平台主体选了活跃租户后被误判为普通租户账号
        Long principalTenantId = SecurityUtils.getPrincipalTenantId();

        // 1) 普通租户账号 → 无权调用平台侧授权接口
        if (principalTenantId != null && principalTenantId > 0) {
            return R.fail("普通租户账号无权调用此接口");
        }

        // 2) root 超管 → 全部启用租户
        if (principalTenantId != null && principalTenantId == 0) {
            LambdaQueryWrapper<SysTenant> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(SysTenant::getStatus, 0).orderByAsc(SysTenant::getId);
            return R.ok(sysTenantService.list(wrapper));
        }

        // 3) 平台主体（tenantId=null 且 platformPrincipal=true）→ 按 PrincipalTenantScope 关联表过滤
        if (principalTenantId == null && SecurityUtils.isPlatformPrincipal()) {
            List<Long> scopedIds = principalTenantScopeService.listScopedTenantIds(currentUser.getUserId());
            if (scopedIds.isEmpty()) {
                // 无分配 → 返回空列表，不报错（前端下拉自然为空，提交时后端还会二次拦截）
                return R.ok(List.of());
            }
            LambdaQueryWrapper<SysTenant> wrapper = new LambdaQueryWrapper<>();
            wrapper.in(SysTenant::getId, scopedIds)
                    .eq(SysTenant::getStatus, 0)
                    .orderByAsc(SysTenant::getId);
            return R.ok(sysTenantService.list(wrapper));
        }

        // 兜底：未知身份
        return R.fail("无法识别当前登录身份");
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

        // ⭐ 不能用 updateById：默认 NOT_NULL 策略会跳过 null 字段，
        // 导致用户清空 expireDate（设为永不过期）时无法落库。
        // 改用 LambdaUpdateWrapper 手动 set 所有传入字段，包括 null 值。
        LambdaUpdateWrapper<SysTenant> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(SysTenant::getId, sysTenant.getId());
        wrapper.set(sysTenant.getTenantName() != null, SysTenant::getTenantName, sysTenant.getTenantName());
        wrapper.set(sysTenant.getTenantCode() != null, SysTenant::getTenantCode, sysTenant.getTenantCode());
        wrapper.set(sysTenant.getContactUser() != null, SysTenant::getContactUser, sysTenant.getContactUser());
        wrapper.set(sysTenant.getContactPhone() != null, SysTenant::getContactPhone, sysTenant.getContactPhone());
        wrapper.set(sysTenant.getAddress() != null, SysTenant::getAddress, sysTenant.getAddress());
        wrapper.set(sysTenant.getStatus() != null, SysTenant::getStatus, sysTenant.getStatus());
        wrapper.set(SysTenant::getExpireDate, sysTenant.getExpireDate());    // null 也要写入
        wrapper.set(sysTenant.getMaxUserNum() != null, SysTenant::getMaxUserNum, sysTenant.getMaxUserNum());
        wrapper.set(sysTenant.getRemark() != null, SysTenant::getRemark, sysTenant.getRemark());
        wrapper.set(SysTenant::getUpdateBy, SecurityUtils.getUsername());
        wrapper.set(SysTenant::getUpdateTime, LocalDateTime.now());
        return sysTenantService.update(wrapper) ? R.ok() : R.fail("更新失败");
    }

    @DeleteMapping("/{id}")
    @RequiresPermission("system:tenant:delete")
    public R<Void> delete(@PathVariable Long id) {
        SysTenant exist = sysTenantService.getById(id);
        if (exist == null) return R.fail("租户不存在");
        // 写操作：完整三层
        permissionGuard.check(ResourceScope.of(null, null, exist.getCreateBy()),
                              "system:tenant:delete");
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