package com.cyan.modules.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cyan.config.CustomTenantHandler;
import com.cyan.common.exception.BusinessException;
import com.cyan.common.security.util.SecurityUtils;
import com.cyan.entity.SysRole;
import com.cyan.entity.SysUser;
import com.cyan.entity.SysUserRole;
import com.cyan.modules.workflow.entity.WfDefinitionTenant;
import com.cyan.modules.workflow.entity.WorkflowDefinition;
import com.cyan.modules.workflow.entity.WorkflowNode;
import com.cyan.modules.workflow.mapper.WorkflowDefinitionMapper;
import com.cyan.modules.workflow.service.WfDefinitionTenantService;
import com.cyan.modules.workflow.service.WorkflowDefinitionService;
import com.cyan.modules.workflow.service.WorkflowNodeService;
import com.cyan.service.SysRoleService;
import com.cyan.service.SysUserRoleService;
import com.cyan.service.SysUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * 流程定义Service实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowDefinitionServiceImpl extends ServiceImpl<WorkflowDefinitionMapper, WorkflowDefinition> implements WorkflowDefinitionService {

    private final WorkflowNodeService workflowNodeService;
    private final SysUserService sysUserService;
    private final SysRoleService sysRoleService;
    private final SysUserRoleService sysUserRoleService;
    private final WfDefinitionTenantService wfDefinitionTenantService;

    /**
     * ⭐ 获取当前租户接收到的所有已分发 definitionId
     */
    private Set<Long> listDistributedDefinitionIds() {
        return listDistributedMeta().keySet();
    }

    /**
     * ⭐ 获取当前租户接收到的所有已分发 definitionId → 分发记录映射
     * 用来回填 allowEdit 字段
     */
    private Map<Long, WfDefinitionTenant> listDistributedMeta() {
        Long tenantId = SecurityUtils.getTenantId();
        if (tenantId == null || tenantId <= 0) return Collections.emptyMap();
        LambdaQueryWrapper<WfDefinitionTenant> w = new LambdaQueryWrapper<>();
        w.eq(WfDefinitionTenant::getTenantId, tenantId)
         .eq(WfDefinitionTenant::getStatus, 1);
        List<WfDefinitionTenant> list = wfDefinitionTenantService.list(w);
        Map<Long, WfDefinitionTenant> map = new HashMap<>();
        for (WfDefinitionTenant t : list) map.put(t.getDefinitionId(), t);
        return map;
    }

    /**
     * ⭐ 回填每条 definition 的 source + allowEdit
     * definition.tenantId == 当前用户 tenantId → OWN, allowEdit=true
     * definition.id 在 distMeta 里 → DISTRIBUTED, allowEdit=分发记录.allowEdit
     */
    private void fillSourceAndAllowEdit(Collection<WorkflowDefinition> list,
                                        Map<Long, WfDefinitionTenant> distMeta) {
        Long myTenantId = SecurityUtils.getTenantId();
        for (WorkflowDefinition d : list) {
            boolean isOwn = (myTenantId != null && myTenantId > 0
                    && myTenantId.equals(d.getTenantId()));
            WfDefinitionTenant dist = distMeta.get(d.getId());
            if (isOwn) {
                d.setSource("OWN");
                d.setAllowEdit(true);
            } else if (dist != null) {
                d.setSource("DISTRIBUTED");
                d.setAllowEdit(dist.getAllowEdit() != null && dist.getAllowEdit() == 1);
            } else {
                // 平台管理员场景：tenantId=null → 全部视为 OWN
                d.setSource("OWN");
                d.setAllowEdit(true);
            }
        }
    }

    @Override
    public boolean existsProcessKey(String processKey, Long excludeId) {
        if (processKey == null || processKey.isEmpty()) return false;
        LambdaQueryWrapper<WorkflowDefinition> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WorkflowDefinition::getProcessKey, processKey);
        if (excludeId != null) wrapper.ne(WorkflowDefinition::getId, excludeId);
        return this.count(wrapper) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveDefinitionAndNodes(WorkflowDefinition definition, List<WorkflowNode> nodes) {
        // 1. processKey 唯一校验
        if (existsProcessKey(definition.getProcessKey(), definition.getId())) {
            throw new BusinessException("流程标识 " + definition.getProcessKey() + " 已存在，请修改");
        }

        boolean isNew = definition.getId() == null;

        if (isNew) {
            if (definition.getProcessKey() == null || definition.getProcessKey().isEmpty()) {
                definition.setProcessKey("PROCESS_" + System.currentTimeMillis());
            }
            if (definition.getVersion() == null) definition.setVersion(1);
            if (definition.getStatus() == null) definition.setStatus(0);
            this.save(definition);
        } else {
            this.updateById(definition);
        }

        // 2. 节点处理
        if (nodes != null) {
            // 2a. 先保存节点 —— batchSaveNodes 内部会：
            //       - 物理删旧数据
            //       - 保存前把每个 node.id 置 null（让数据库自增）
            //       - 保存后自动把数据库真实自增 id 回填到每个 node 对象上
            //       - 修正每个 node.nextNodeIds 里的临时 id → 真实 id
            workflowNodeService.batchSaveNodes(definition.getId(), nodes);

            // 2b. 现在 nodes 里每个 node.getId() 都是数据库真实自增 id 了，拿它回填 startNodeId
            Long startNodeId = null;
            for (WorkflowNode n : nodes) {
                if ("start".equals(n.getNodeType())) { startNodeId = n.getId(); break; }
            }
            // 兜底：前端没填 start 就取第一个节点的真实 id
            if (startNodeId == null && !nodes.isEmpty()) {
                startNodeId = nodes.get(0).getId();
            }
            definition.setStartNodeId(startNodeId);
            this.updateById(definition);
        }

        return definition.getId();
    }

    @Override
    public List<WorkflowDefinition> listPublished(String businessType) {
        CustomTenantHandler.skip();
        try {
            Map<Long, WfDefinitionTenant> distMeta = listDistributedMeta();
            Set<Long> distIds = distMeta.keySet();

            LambdaQueryWrapper<WorkflowDefinition> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(WorkflowDefinition::getStatus, 1);
            if (businessType != null && !businessType.isEmpty()) {
                wrapper.eq(WorkflowDefinition::getBusinessType, businessType);
            }
            // ⭐ 永真占位 1=1 确保 and 括号不为空 —— 否则生成 WHERE () ORDER BY ...
            // 让 MP 3.5.5 的 JSqlParser 解析异常，PaginationInnerInterceptor 无法剥离
            // ORDER BY，count SQL 直接变成 COUNT(*) FROM (... ORDER BY) TOTAL → MariaDB 500
            wrapper.and(w -> {
                w.apply("1=1");
                Long myTenantId = SecurityUtils.getTenantId();
                if (myTenantId != null && myTenantId > 0) {
                    w.eq(WorkflowDefinition::getTenantId, myTenantId);
                }
                if (!distIds.isEmpty()) {
                    if (myTenantId != null && myTenantId > 0) w.or();
                    w.in(WorkflowDefinition::getId, distIds);
                }
            });
            wrapper.orderByDesc(WorkflowDefinition::getVersion);
            List<WorkflowDefinition> result = this.list(wrapper);
            fillSourceAndAllowEdit(result, distMeta);
            return result;
        } finally {
            CustomTenantHandler.reset();
        }
    }

    @Override
    public Page<WorkflowDefinition> queryPageList(Integer pageNum, Integer pageSize, String processName, String category, Integer status) {
        CustomTenantHandler.skip();
        try {
            Map<Long, WfDefinitionTenant> distMeta = listDistributedMeta();
            Set<Long> distIds = distMeta.keySet();

            LambdaQueryWrapper<WorkflowDefinition> wrapper = new LambdaQueryWrapper<>();
            if (processName != null && !processName.isEmpty()) {
                wrapper.like(WorkflowDefinition::getProcessName, processName);
            }
            if (category != null && !category.isEmpty()) {
                wrapper.eq(WorkflowDefinition::getCategory, category);
            }
            if (status != null) {
                wrapper.eq(WorkflowDefinition::getStatus, status);
            }
            // ⭐ 永真占位 1=1 确保 and 括号不为空 —— 否则生成 WHERE () ORDER BY ...
            // 让 MP 3.5.5 的 JSqlParser 解析异常，PaginationInnerInterceptor 无法剥离
            // ORDER BY，count SQL 直接变成 COUNT(*) FROM (... ORDER BY) TOTAL → MariaDB 500
            wrapper.and(w -> {
                w.apply("1=1");
                Long myTenantId = SecurityUtils.getTenantId();
                if (myTenantId != null && myTenantId > 0) {
                    w.eq(WorkflowDefinition::getTenantId, myTenantId);
                }
                if (!distIds.isEmpty()) {
                    if (myTenantId != null && myTenantId > 0) w.or();
                    w.in(WorkflowDefinition::getId, distIds);
                }
            });
            wrapper.orderByDesc(WorkflowDefinition::getCreateTime);

            // ⭐ 手动 count + selectPage，彻底绕开 PaginationInnerInterceptor 对 count SQL 的包装
            // MP 3.5.5 + JSqlParser 在 wrapper 含空括号时无法正确剥离 ORDER BY，
            // 导致 count SQL 生成 ... ORDER BY xxx) TOTAL → MariaDB 语法错误
            // 手动 count 天然不带 ORDER BY，零额外性能开销（和 page() 一样是 count+select 两次请求）
            long total = baseMapper.selectCount(wrapper);
            Page<WorkflowDefinition> page = new Page<>(pageNum, pageSize);
            Page<WorkflowDefinition> result = baseMapper.selectPage(page, wrapper);
            result.setTotal(total);

            fillSourceAndAllowEdit(result.getRecords(), distMeta);
            return result;
        } finally {
            CustomTenantHandler.reset();
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean publishProcess(Long id) {
        WorkflowDefinition definition = this.getById(id);
        if (definition == null) {
            throw new BusinessException("流程定义不存在");
        }
        if (definition.getStatus() == 1) {
            throw new BusinessException("流程已发布");
        }

        // 发布前校验节点：至少要有一个 userTask
        List<WorkflowNode> nodes = workflowNodeService.listByDefinitionId(id);
        long userTaskCount = nodes.stream().filter(n -> "userTask".equals(n.getNodeType())).count();
        if (nodes.isEmpty()) {
            throw new BusinessException("发布失败：请先配置审批节点");
        }
        if (userTaskCount == 0) {
            throw new BusinessException("发布失败：流程中至少要有一个审批节点(userTask)");
        }
        if (definition.getStartNodeId() == null) {
            throw new BusinessException("发布失败：流程未设置起始节点");
        }

        definition.setStatus(1);
        return this.updateById(definition);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean disableProcess(Long id) {
        WorkflowDefinition definition = this.getById(id);
        if (definition == null) {
            throw new BusinessException("流程定义不存在");
        }
        
        if (definition.getStatus() == 2) {
            throw new BusinessException("流程已停用");
        }
        
        definition.setStatus(2);
        return this.updateById(definition);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long copyProcess(Long id) {
        WorkflowDefinition source = this.getById(id);
        if (source == null) {
            throw new BusinessException("流程定义不存在");
        }

        // 1. 复制 definition
        WorkflowDefinition newDefinition = new WorkflowDefinition();
        newDefinition.setProcessKey(source.getProcessKey());
        newDefinition.setProcessName(source.getProcessName() + "(副本)");
        newDefinition.setDescription(source.getDescription());
        newDefinition.setCategory(source.getCategory());
        newDefinition.setProcessXml(source.getProcessXml());
        newDefinition.setVersion(source.getVersion() + 1);
        newDefinition.setStatus(0); // 草稿状态
        this.save(newDefinition);

        // 2. 连节点一起复制（生成新的 id，并修正 nextNodeIds 指向新节点 id）
        List<WorkflowNode> oldNodes = workflowNodeService.listByDefinitionId(id);
        if (!oldNodes.isEmpty()) {
            // 2a. 先按原顺序存新节点，用 Map 记 oldId → newId
            List<Long> oldIds = new ArrayList<>();
            List<Long> newIds = new ArrayList<>();
            for (WorkflowNode on : oldNodes) {
                oldIds.add(on.getId());
                WorkflowNode nn = new WorkflowNode();
                nn.setDefinitionId(newDefinition.getId());
                nn.setNodeKey(on.getNodeKey());
                nn.setNodeName(on.getNodeName());
                nn.setNodeType(on.getNodeType());
                nn.setAssigneeType(on.getAssigneeType());
                nn.setAssigneeValue(on.getAssigneeValue());
                nn.setApprovalType(on.getApprovalType());
                nn.setTimeoutHours(on.getTimeoutHours());
                nn.setNextNodeIds(on.getNextNodeIds());
                nn.setSortOrder(on.getSortOrder());
                workflowNodeService.save(nn);
                newIds.add(nn.getId());
            }

            // 2b. 修正 nextNodeIds：把每个 next 里的旧 id 换成新 id
            java.util.Map<Long, Long> idMap = new java.util.HashMap<>();
            for (int i = 0; i < oldIds.size(); i++) idMap.put(oldIds.get(i), newIds.get(i));
            for (WorkflowNode on : oldNodes) {
                if (on.getNextNodeIds() == null || on.getNextNodeIds().isEmpty()) continue;
                StringBuilder sb = new StringBuilder();
                for (String s : on.getNextNodeIds().split(",")) {
                    try {
                        Long oldNext = Long.parseLong(s.trim());
                        Long newNext = idMap.getOrDefault(oldNext, oldNext);
                        if (sb.length() > 0) sb.append(",");
                        sb.append(newNext);
                    } catch (NumberFormatException ignore) {}
                }
                // 找到刚复制出来的对应新节点，更新它的 nextNodeIds
                for (int i = 0; i < oldNodes.size(); i++) {
                    if (oldNodes.get(i).getId().equals(on.getId())) {
                        WorkflowNode toUpdate = workflowNodeService.getById(newIds.get(i));
                        if (toUpdate != null) {
                            toUpdate.setNextNodeIds(sb.toString());
                            workflowNodeService.updateById(toUpdate);
                        }
                        break;
                    }
                }
            }

            // 2c. 修正 startNodeId
            if (source.getStartNodeId() != null) {
                Long newStartId = idMap.get(source.getStartNodeId());
                newDefinition.setStartNodeId(newStartId);
                this.updateById(newDefinition);
            }
        }

        log.info("流程复制成功: 原ID={}, 新ID={}, 新版本={}, 节点数={}", id, newDefinition.getId(), newDefinition.getVersion(), oldNodes.size());
        return newDefinition.getId();
    }

    @Override
    public List<Long> resolveAssigneeIds(WorkflowNode node, Long initiatorId) {
        List<Long> result = new ArrayList<>();
        String type = node.getAssigneeType();
        String value = node.getAssigneeValue();

        // --- 1. initiator: 直接用发起人自己 ---
        if ("initiator".equals(type)) {
            result.add(initiatorId);
            log.debug("[Workflow] assigneeType=initiator → 发起人 {}", initiatorId);
            return result;
        }

        // --- 2. user: 解析 assigneeValue 里的用户 id（逗号串） ---
        if ("user".equals(type)) {
            if (value != null && !value.isEmpty()) {
                for (String v : value.split(",")) {
                    try { result.add(Long.parseLong(v.trim())); } catch (NumberFormatException ignore) {}
                }
            }
            log.debug("[Workflow] assigneeType=user → {}", result);
            if (!result.isEmpty()) return result;
        }

        // --- 3. role: assigneeValue 是 roleKey 或 roleId，先找 role 再找用户 ---
        if ("role".equals(type)) {
            if (value != null && !value.isEmpty()) {
                Set<Long> roleIds = new HashSet<>();
                for (String v : value.split(",")) {
                    String vv = v.trim();
                    if (vv.isEmpty()) continue;
                    // 先尝试当 roleId 数字
                    try {
                        roleIds.add(Long.parseLong(vv));
                    } catch (NumberFormatException e) {
                        // 不是数字 → 当 roleKey 去查
                        List<SysRole> roles = sysRoleService.list(
                                new LambdaQueryWrapper<SysRole>().eq(SysRole::getRoleKey, vv));
                        for (SysRole r : roles) roleIds.add(r.getId());
                    }
                }
                // 用 roleIds 查 user_role → 用户 id
                if (!roleIds.isEmpty()) {
                    List<SysUserRole> urs = sysUserRoleService.list(
                            new LambdaQueryWrapper<SysUserRole>().in(SysUserRole::getRoleId, roleIds));
                    for (SysUserRole ur : urs) result.add(ur.getUserId());
                }
            }
            log.debug("[Workflow] assigneeType=role → {}", result);
            if (!result.isEmpty()) return result;
        }

        // --- 4. dept: assigneeValue 是 deptId（逗号串），查该部门所有用户 ---
        if ("dept".equals(type)) {
            if (value != null && !value.isEmpty()) {
                Set<Long> deptIds = new HashSet<>();
                for (String v : value.split(",")) {
                    try { deptIds.add(Long.parseLong(v.trim())); } catch (NumberFormatException ignore) {}
                }
                if (!deptIds.isEmpty()) {
                    List<SysUser> users = sysUserService.list(
                            new LambdaQueryWrapper<SysUser>().in(SysUser::getDeptId, deptIds));
                    for (SysUser u : users) result.add(u.getId());
                }
            }
            log.debug("[Workflow] assigneeType=dept → {}", result);
            if (!result.isEmpty()) return result;
        }

        // --- 兜底：解析失败返回发起人自己 ---
        if (result.isEmpty()) {
            result.add(initiatorId);
            log.debug("[Workflow] assigneeType={} 解析失败，兜底发起人 {}", type, initiatorId);
        }
        return result;
    }
}