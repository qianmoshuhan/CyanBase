package com.cyan.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cyan.common.R;
import com.cyan.common.security.util.SecurityUtils;
import com.cyan.entity.SysDept;
import com.cyan.service.SysDeptService;
import com.cyan.security.annotation.RequiresPermission;
import com.cyan.security.aspect.PermissionGuard;
import com.cyan.security.datascope.ResourceScope;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/system/dept")
@RequiredArgsConstructor
public class SysDeptController {

    private final SysDeptService sysDeptService;
    private final PermissionGuard permissionGuard;

    @RequiresPermission("system:dept:list")
    @GetMapping("/list")
    public R<Page<SysDept>> list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String deptName,
            @RequestParam(required = false) Integer status) {

        LambdaQueryWrapper<SysDept> wrapper = new LambdaQueryWrapper<>();
        if (deptName != null && !deptName.isEmpty()) {
            wrapper.like(SysDept::getDeptName, deptName);
        }
        if (status != null) {
            wrapper.eq(SysDept::getStatus, status);
        }
        wrapper.orderByAsc(SysDept::getSortNum);

        Page<SysDept> page = new Page<>(pageNum, pageSize);
        sysDeptService.page(page, wrapper);
        return R.ok(page);
    }

    /**
     * 全量部门（树形表格 + 搜索用）
     * <p>
     * 支持 deptName（模糊）、status（精确）、tenantId（精确）三个可选过滤条件。
     * 树形搜索经典问题：只返回匹配节点会导致父节点缺失、树断链。
     * 解法：先查隔离范围内全量 → 内存过滤匹配节点 → 递归向上补齐祖先链 → 返回完整集合。
     * </p>
     */
    @GetMapping("/all")
    public R<?> all(
            @RequestParam(required = false) String deptName,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Long tenantId) {

        // 1. 先确定「查询范围」：tenantId 过滤 + status 过滤（注意：有 deptName 时不在这里按 status 过滤，
        //    否则会把祖先节点也过滤掉，导致树断链。祖先节点的 status 不影响自身展示）
        LambdaQueryWrapper<SysDept> scopeWrapper = new LambdaQueryWrapper<>();
        if (tenantId != null) scopeWrapper.eq(SysDept::getTenantId, tenantId);
        // 如果没有 deptName 搜索条件，直接按 status 过滤全量（保持原有行为）
        if (deptName == null || deptName.isEmpty()) {
            if (status != null) scopeWrapper.eq(SysDept::getStatus, status);
        }
        scopeWrapper.orderByAsc(SysDept::getSortNum);
        List<SysDept> scopeList = sysDeptService.list(scopeWrapper);

        // 2. 如果没有任何过滤条件，直接返回（最优路径）
        if ((deptName == null || deptName.isEmpty()) && status == null) {
            return R.ok(scopeList);
        }

        // 3. 构建内存 Map 加速查找
        Map<Long, SysDept> idMap = scopeList.stream()
                .collect(Collectors.toMap(SysDept::getId, d -> d));

        // 4. 先找出「命中节点」：deptName 模糊匹配 + status 精确匹配（只对命中节点校验 status）
        Set<Long> hitIds = new HashSet<>();
        for (SysDept d : scopeList) {
            boolean nameHit = deptName == null || deptName.isEmpty()
                    || (d.getDeptName() != null && d.getDeptName().contains(deptName));
            boolean statusHit = status == null || Objects.equals(d.getStatus(), status);
            if (nameHit && statusHit) {
                hitIds.add(d.getId());
            }
        }

        // 5. 对每个命中节点，递归向上补齐祖先链（保证树不断链）
        Set<Long> finalIds = new HashSet<>(hitIds);
        for (Long hitId : hitIds) {
            SysDept cur = idMap.get(hitId);
            while (cur != null && cur.getParentId() != null && cur.getParentId() != 0) {
                finalIds.add(cur.getParentId());
                cur = idMap.get(cur.getParentId());
            }
        }

        // 6. 收集最终结果，按 sortNum 排序
        List<SysDept> result = scopeList.stream()
                .filter(d -> finalIds.contains(d.getId()))
                .sorted(Comparator.comparingInt(d -> d.getSortNum() != null ? d.getSortNum() : 0))
                .collect(Collectors.toList());

        return R.ok(result);
    }

    @GetMapping("/{id}")
    public R<SysDept> getById(@PathVariable Long id) {
        SysDept dept = sysDeptService.getById(id);
        if (dept == null) return R.fail("部门不存在");
        // 读操作：L1 租户归属 + L2 DataScope（targetDeptId 即自身部门 id）
        permissionGuard.check(ResourceScope.of(dept.getTenantId(), dept.getId(), dept.getCreateBy()));
        return R.ok(dept);
    }

    @PostMapping
    @RequiresPermission("system:dept:add")
    public R<Void> add(@RequestBody SysDept sysDept) {
        sysDept.setId(null);
        return sysDeptService.save(sysDept) ? R.ok() : R.fail("新增失败");
    }

    @PutMapping
    @RequiresPermission("system:dept:edit")
    public R<Void> update(@RequestBody SysDept sysDept) {
        SysDept exist = sysDeptService.getById(sysDept.getId());
        if (exist == null) return R.fail("部门不存在");
        // 写操作：完整三层 L1 租户归属 + L2 DataScope + L3 system:dept:edit
        permissionGuard.check(ResourceScope.of(exist.getTenantId(), exist.getId(), exist.getCreateBy()),
                              "system:dept:edit");
        return sysDeptService.updateById(sysDept) ? R.ok() : R.fail("更新失败");
    }

    @DeleteMapping("/{id}")
    @RequiresPermission("system:dept:delete")
    public R<Void> delete(@PathVariable Long id) {
        SysDept exist = sysDeptService.getById(id);
        if (exist == null) return R.fail("部门不存在");
        // 写操作：完整三层校验
        permissionGuard.check(ResourceScope.of(exist.getTenantId(), exist.getId(), exist.getCreateBy()),
                              "system:dept:delete");
        // 1. 查询是否存在子部门
        LambdaQueryWrapper<SysDept> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SysDept::getParentId, id);
        queryWrapper.eq(SysDept::getIsDeleted, 0);
        long count = sysDeptService.count(queryWrapper);

        // 2. 有子部门禁止删除
        if (count > 0) {
            return R.fail("该部门下存在子部门，无法删除！");
        }

        // 3. 执行删除
        boolean result = sysDeptService.removeById(id);
        return result ? R.ok() : R.fail("删除失败");
    }

    /**
     * ⭐ 远程搜索部门（工作流节点配置下拉用）
     * 自动按当前登录租户过滤
     */
    @GetMapping("/search")
    public R<List<Map<String, Object>>> searchDepts(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "50") Integer limit) {
        LambdaQueryWrapper<SysDept> wrapper = new LambdaQueryWrapper<>();
        Long tenantId = SecurityUtils.getTenantId();
        if (tenantId != null) wrapper.eq(SysDept::getTenantId, tenantId);
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.like(SysDept::getDeptName, keyword);
        }
        wrapper.eq(SysDept::getStatus, 0).orderByAsc(SysDept::getSortNum).last("limit " + limit);
        List<Map<String, Object>> result = sysDeptService.list(wrapper).stream()
                .map(d -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", d.getId());
                    m.put("label", d.getDeptName());
                    return m;
                }).collect(Collectors.toList());
        return R.ok(result);
    }
}