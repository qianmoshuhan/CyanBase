package com.cyan.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cyan.common.R;
import com.cyan.common.security.util.SecurityUtils;
import com.cyan.config.CustomTenantHandler;
import com.cyan.entity.SysDept;
import com.cyan.entity.SysTenant;
import com.cyan.service.PrincipalTenantScopeService;
import com.cyan.service.SysDeptService;
import com.cyan.service.SysTenantService;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 组织架构树聚合接口。
 * <p>
 * 根据当前登录身份自动裁剪权限范围，一次性返回组装好的「租户 + 部门」混合树。
 * <p>
 * 权限判定链：
 * <ul>
 *   <li>root 超管（tenant_id=0）    → 全部启用租户 + 全部部门</li>
 *   <li>平台主体（tenant_id=NULL）  → PrincipalTenantScope 授权租户 + 其下部门</li>
 *   <li>租户账号（tenant_id>0）     → 仅当前租户的部门树（扁平，不带租户分组）</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/system/org")
@RequiredArgsConstructor
public class SysOrgTreeController {

    private final SysTenantService sysTenantService;
    private final SysDeptService sysDeptService;
    private final PrincipalTenantScopeService principalTenantScopeService;

    /**
     * 返回组织树。
     * <p>
     * 三种场景的返回结构：
     * <ol>
     *   <li>root / 平台主体：platform-root 虚拟节点 → 租户节点（可多个）→ 每个租户下的部门树</li>
     *   <li>租户账号：直接返回当前租户的部门树（children 形式，无租户层级）</li>
     * </ol>
     */
    @GetMapping("/tree")
    public R<List<OrgNodeVO>> getOrgTree() {
        Long principalTenantId = SecurityUtils.getPrincipalTenantId();

        if (principalTenantId != null && principalTenantId > 0) {
            // ============ 场景3：普通租户账号 —— 只查当前租户的部门树 ============
            Long tenantId = SecurityUtils.getTenantId();
            List<OrgNodeVO> deptTree = buildDeptTreeForTenant(tenantId);
            return R.ok(deptTree);
        }

        // ============ 场景1 & 2：root 超管 or 平台主体 ============
        List<SysTenant> tenants;
        if (principalTenantId != null && principalTenantId == 0L) {
            // root 超管：全部租户（含冻结/过期，由前端置灰）
            tenants = sysTenantService.list();
        } else {
            // 平台主体：按 PrincipalTenantScope 过滤
            List<Long> scopedTenantIds = principalTenantScopeService
                    .listScopedTenantIds(SecurityUtils.getUserId());
            if (scopedTenantIds.isEmpty()) {
                tenants = Collections.emptyList();
            } else {
                tenants = sysTenantService.listByIds(scopedTenantIds);
            }
        }

        // 构造 platform-root 虚拟节点
        OrgNodeVO root = new OrgNodeVO();
        root.setNodeId("platform-root");
        root.setNodeType("platform-root");
        root.setLabel("平台主体");
        root.setTenantId(null);
        root.setStatus(0);
        root.setIsBizUnit(0);

        // 每个租户 → 租户节点 → 其下部门树
        List<OrgNodeVO> tenantNodes = new ArrayList<>();
        for (SysTenant tenant : tenants) {
            OrgNodeVO tNode = new OrgNodeVO();
            tNode.setNodeId("tenant-" + tenant.getId());
            tNode.setNodeType("tenant");
            tNode.setLabel(tenant.getTenantName());
            tNode.setTenantId(tenant.getId());
            tNode.setStatus(tenant.getStatus());    // 0=正常 1=冻结 2=过期
            tNode.setIsBizUnit(0);
            tNode.setChildren(buildDeptTreeForTenant(tenant.getId()));
            tenantNodes.add(tNode);
        }
        root.setChildren(tenantNodes);

        return R.ok(Collections.singletonList(root));
    }

    // ============== 工具方法 ==============

    /**
     * 构建指定租户下的部门树。
     * <p>
     * 手动调用 MP skip 拦截器，让 tenantId=null 的 root/平台主体能查到所有租户的部门。
     */
    private List<OrgNodeVO> buildDeptTreeForTenant(Long tenantId) {
        List<SysDept> allDepts;
        try {
            // 跳过租户拦截，显式指定 tenantId 查询
            CustomTenantHandler.skip();
            allDepts = sysDeptService.list(new LambdaQueryWrapper<SysDept>()
                    .eq(SysDept::getTenantId, tenantId)
                    .orderByAsc(SysDept::getSortNum));
        } finally {
            CustomTenantHandler.reset();
        }
        if (allDepts == null || allDepts.isEmpty()) {
            return new ArrayList<>();
        }

        // 按 parentId 分组
        Map<Long, List<SysDept>> byParent = allDepts.stream()
                .collect(Collectors.groupingBy(
                        d -> d.getParentId() != null ? d.getParentId() : 0L,
                        HashMap::new, Collectors.toList()));

        List<OrgNodeVO> roots = new ArrayList<>();
        List<SysDept> topLevel = byParent.getOrDefault(0L, Collections.emptyList());
        for (SysDept d : topLevel) {
            roots.add(convertDept(d, byParent));
        }
        return roots;
    }

    private OrgNodeVO convertDept(SysDept dept, Map<Long, List<SysDept>> byParent) {
        OrgNodeVO node = new OrgNodeVO();
        node.setNodeId("dept-" + dept.getId());
        node.setNodeType("dept");
        node.setLabel(dept.getDeptName());
        node.setTenantId(dept.getTenantId());
        node.setDeptId(dept.getId());
        node.setStatus(dept.getStatus() != null ? dept.getStatus() : 0);
        node.setIsBizUnit(dept.getIsBizUnit() != null ? dept.getIsBizUnit() : 0);

        List<SysDept> children = byParent.get(dept.getId());
        if (children != null && !children.isEmpty()) {
            List<OrgNodeVO> childNodes = new ArrayList<>();
            for (SysDept c : children) {
                childNodes.add(convertDept(c, byParent));
            }
            node.setChildren(childNodes);
        }
        return node;
    }

    // ============== VO ==============

    @Data
    public static class OrgNodeVO {
        /** 前端虚拟唯一ID，避免不同租户的同名字段冲突 */
        private String nodeId;
        /** platform-root / tenant / dept */
        private String nodeType;
        /** 显示文本 */
        private String label;
        /** 0=正常 1=冻结 2=过期 —— 租户节点专属 */
        private Integer status;
        /** 0=否 1=是 —— 部门节点专属（经营单元标记） */
        private Integer isBizUnit;
        /** 该节点所属租户ID，null 表示平台主体 */
        private Long tenantId;
        /** 部门节点专属 */
        private Long deptId;
        /** 子节点 */
        @JsonProperty("children")
        private List<OrgNodeVO> children;
    }
}