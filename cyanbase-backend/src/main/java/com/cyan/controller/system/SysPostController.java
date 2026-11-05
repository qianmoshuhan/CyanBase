package com.cyan.controller.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cyan.common.R;
import com.cyan.entity.SysDept;
import com.cyan.entity.SysPost;
import com.cyan.entity.SysTenant;
import com.cyan.service.SysDeptService;
import com.cyan.service.SysPostService;
import com.cyan.service.SysTenantService;
import com.cyan.security.annotation.RequiresPermission;
import com.cyan.security.aspect.PermissionGuard;
import com.cyan.security.datascope.ResourceScope;
import com.cyan.security.datascope.DataScopeContext;
import com.cyan.config.CustomTenantHandler;
import com.cyan.common.security.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/system/post")
@RequiredArgsConstructor
public class SysPostController {

    private final SysPostService sysPostService;
    private final SysTenantService sysTenantService;
    private final SysDeptService sysDeptService;
    private final PermissionGuard permissionGuard;

    @RequiresPermission("system:post:list")
    @GetMapping("/list")
    public R<?> list(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String postCode,
            @RequestParam(required = false) String postName,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long tenantId) {

        // ⭐ 只跳过 TenantLine 拦截器（DataPermissionInterceptor 会自动追加 DataScope 条件，
        //    且对 count SQL 和 records SQL 追加的是同一条条件，不会不对称）
        // TenantLine 跳过原因：admin 登录 tenantId=0 时 TenantLine 会把全部岗位过滤掉，
        //      跳过后手动构造租户条件，count 和 records 用同一份 WHERE
        CustomTenantHandler.skip();
        try {
            LambdaQueryWrapper<SysPost> wrapper = new LambdaQueryWrapper<>();

            // 手动租户隔离：平台 admin(tenantId=0) 或前端指定 tenantId → 按参数过滤；
            //             租户登录(tenantId>0) 且前端未指定 → 只看本租户岗位 + 平台共享岗位(tenant_id=0)
            Long currentTenantId = SecurityUtils.getTenantId();
            if (tenantId != null) {
                // 前端传了 tenantId → 严格按参数过滤
                wrapper.eq(SysPost::getTenantId, tenantId);
            } else if (currentTenantId != null && currentTenantId != 0L) {
                // 租户登录人，未指定 tenantId → 看本租户 + 平台共享
                wrapper.and(w -> w.eq(SysPost::getTenantId, currentTenantId)
                                  .or().eq(SysPost::getTenantId, 0L));
            }
            // 平台管理员 tenantId=0 且前端没传 tenantId → 看全部（不加 tenant_id 条件）

            if (postCode != null && !postCode.isEmpty()) {
                wrapper.like(SysPost::getPostCode, postCode);
            }
            if (postName != null && !postName.isEmpty()) {
                wrapper.like(SysPost::getPostName, postName);
            }
            if (status != null && !status.isEmpty()) {
                wrapper.eq(SysPost::getStatus, status);
            }
            wrapper.orderByAsc(SysPost::getPostSort);

            Page<SysPost> page = new Page<>(current, size);
            Page<SysPost> result = sysPostService.page(page, wrapper);
            fillTenantName(result.getRecords());
            fillDeptName(result.getRecords());
            return R.ok(result);
        } finally {
            CustomTenantHandler.reset();
        }
    }

    /**
     * ⭐ 岗位下拉数据源（编辑用户弹窗用）
     * <p>只返回当前租户、状态正常的岗位</p>
     * <p>平台管理员登录 → 返回全部状态正常岗位；租户登录 → MP 自动隔离只返本租户岗位</p>
     */
    @GetMapping("/all")
    public R<List<SysPost>> getAll() {
        LambdaQueryWrapper<SysPost> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysPost::getStatus, "0")
               .orderByAsc(SysPost::getPostSort);
        return R.ok(sysPostService.list(wrapper));
    }

    @GetMapping("/{postId}")
    public R<?> getById(@PathVariable Long postId) {
        SysPost post = sysPostService.getById(postId);
        if (post == null) return R.fail("岗位不存在");
        // 读操作：L1 租户归属 + L2 DataScope
        permissionGuard.check(ResourceScope.of(post.getTenantId(), null, post.getCreateBy()));
        return R.ok(post);
    }

    @PostMapping
    @RequiresPermission("system:post:add")
    public R<?> add(@RequestBody SysPost post) {
        post.setPostId(null);
        // ⭐ 强制以后端安全上下文为准：不管前端传什么 tenantId 都覆盖掉
        // 前端 handleAdd 曾错误地把租户登录的 tenantId 写死成 0，
        // strictInsertFill 又会跳过已非 null 的属性，导致 tenant_id=0 被存进库
        Long tenantId = SecurityUtils.getTenantId();
        if (tenantId == null) tenantId = 0L;
        post.setTenantId(tenantId);
        // ⭐ 岗位编码由系统自动生成，覆盖前端可能传来的值
        post.setPostCode(generatePostCode(tenantId));
        sysPostService.save(post);
        return R.ok("新增成功");
    }

    @PutMapping
    @RequiresPermission("system:post:edit")
    public R<?> update(@RequestBody SysPost post) {
        SysPost exist = sysPostService.getById(post.getPostId());
        if (exist == null) return R.fail("岗位不存在");
        // 写操作：完整三层 L1 租户归属 + L2 DataScope + L3 system:post:edit
        permissionGuard.check(ResourceScope.of(exist.getTenantId(), null, exist.getCreateBy()),
                              "system:post:edit");
        sysPostService.updateById(post);
        return R.ok("更新成功");
    }

    @DeleteMapping("/{postId}")
    @RequiresPermission("system:post:delete")
    public R<?> delete(@PathVariable Long postId) {
        SysPost exist = sysPostService.getById(postId);
        if (exist == null) return R.fail("岗位不存在");
        // 写操作：完整三层校验
        permissionGuard.check(ResourceScope.of(exist.getTenantId(), null, exist.getCreateBy()),
                              "system:post:delete");
        sysPostService.removeById(postId);
        return R.ok("删除成功");
    }

    /**
     * 自动生成岗位编码：{tenantId}{yyyyMMdd}{3位序号}
     * <p>例：tenantId=6, 2026-10-27, 当天第一个岗位 → 620261027001</p>
     * <p>序号按「同一租户 + 同一天」独立递增</p>
     */
    private String generatePostCode(Long tenantId) {
        String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String prefix = tenantId + dateStr;
        // 查当前租户下、当天前缀匹配的最大 post_code
        LambdaQueryWrapper<SysPost> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysPost::getTenantId, tenantId)
               .likeRight(SysPost::getPostCode, prefix)
               .orderByDesc(SysPost::getPostCode)
               .last("LIMIT 1");
        SysPost latest = sysPostService.getOne(wrapper);
        int seq = 1;
        if (latest != null && latest.getPostCode() != null) {
            // 从末尾提取3位序号
            Matcher m = Pattern.compile(prefix + "(\\d{3})$").matcher(latest.getPostCode());
            if (m.find()) seq = Integer.parseInt(m.group(1)) + 1;
        }
        return prefix + String.format("%03d", seq);
    }

    private void fillTenantName(java.util.List<SysPost> records) {
        if (records == null || records.isEmpty()) return;
        // 收集所有 tenantId（去重）
        java.util.Set<Long> tenantIds = new java.util.HashSet<>();
        for (SysPost p : records) {
            if (p.getTenantId() != null && p.getTenantId() > 0) tenantIds.add(p.getTenantId());
        }
        if (tenantIds.isEmpty()) {
            for (SysPost p : records) p.setTenantName("平台岗位");
            return;
        }
        // 批量查租户
        java.util.List<SysTenant> tenants = sysTenantService.listByIds(tenantIds);
        java.util.Map<Long, String> map = new java.util.HashMap<>();
        for (SysTenant t : tenants) map.put(t.getId(), t.getTenantName());
        for (SysPost p : records) {
            if (p.getTenantId() == null || p.getTenantId() <= 0) {
                p.setTenantName("平台岗位");
            } else {
                p.setTenantName(map.getOrDefault(p.getTenantId(), "未知租户"));
            }
        }
    }

    private void fillDeptName(java.util.List<SysPost> records) {
        if (records == null || records.isEmpty()) return;
        java.util.Set<Long> deptIds = new java.util.HashSet<>();
        for (SysPost p : records) {
            if (p.getDeptId() != null && p.getDeptId() > 0) deptIds.add(p.getDeptId());
        }
        if (deptIds.isEmpty()) {
            for (SysPost p : records) p.setDeptName("未分配");
            return;
        }
        java.util.List<SysDept> depts = sysDeptService.listByIds(deptIds);
        java.util.Map<Long, String> map = new java.util.HashMap<>();
        for (SysDept d : depts) map.put(d.getId(), d.getDeptName());
        for (SysPost p : records) {
            if (p.getDeptId() == null || p.getDeptId() <= 0) {
                p.setDeptName("未分配");
            } else {
                p.setDeptName(map.getOrDefault(p.getDeptId(), "未分配"));
            }
        }
    }
}