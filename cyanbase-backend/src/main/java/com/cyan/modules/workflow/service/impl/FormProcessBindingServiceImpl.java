package com.cyan.modules.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cyan.common.security.util.SecurityUtils;
import com.cyan.entity.SysDept;
import com.cyan.entity.SysUser;
import com.cyan.modules.workflow.entity.FormProcessBinding;
import com.cyan.modules.workflow.entity.WorkflowDefinition;
import com.cyan.modules.workflow.mapper.FormProcessBindingMapper;
import com.cyan.modules.workflow.service.WorkflowDefinitionService;
import com.cyan.modules.workflow.service.FormProcessBindingService;
import com.cyan.service.SysDeptService;
import com.cyan.service.SysUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 表单 ↔ 流程定义 多对多绑定 ServiceImpl
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FormProcessBindingServiceImpl
        extends ServiceImpl<FormProcessBindingMapper, FormProcessBinding>
        implements FormProcessBindingService {

    private final SysUserService sysUserService;
    private final SysDeptService sysDeptService;
    private final WorkflowDefinitionService workflowDefinitionService;

    @Override
    public Page<FormProcessBinding> pageList(Integer pageNum, Integer pageSize,
                                              Long definitionId, Long processDefinitionId,
                                              String businessType, Integer status) {
        LambdaQueryWrapper<FormProcessBinding> w = new LambdaQueryWrapper<>();
        if (definitionId != null) w.eq(FormProcessBinding::getDefinitionId, definitionId);
        if (processDefinitionId != null) w.eq(FormProcessBinding::getProcessDefinitionId, processDefinitionId);
        if (businessType != null && !businessType.isEmpty())
            w.eq(FormProcessBinding::getBusinessType, businessType);
        if (status != null) w.eq(FormProcessBinding::getStatus, status);
        w.orderByDesc(FormProcessBinding::getPriority)
         .orderByDesc(FormProcessBinding::getId);

        Page<FormProcessBinding> page = this.page(new Page<>(pageNum, pageSize), w);

        // 组装 formName / processName（把非数据库字段填上）
        enrichWithNames(page.getRecords());
        return page;
    }

    @Override
    public List<FormProcessBinding> listByDefinitionId(Long definitionId) {
        LambdaQueryWrapper<FormProcessBinding> w = new LambdaQueryWrapper<>();
        w.eq(FormProcessBinding::getDefinitionId, definitionId)
         .eq(FormProcessBinding::getStatus, 1)
         .orderByDesc(FormProcessBinding::getPriority);
        List<FormProcessBinding> list = this.list(w);
        enrichWithNames(list);
        return list;
    }

    @Override
    @Transactional(readOnly = true)
    public Long matchProcess(Long definitionId, Long userId) {
        if (definitionId == null) {
            log.warn("[FormBinding] matchProcess: definitionId is null");
            return null;
        }
        Long actualUserId = (userId != null) ? userId : SecurityUtils.getUserId();
        if (actualUserId == null) {
            log.warn("[FormBinding] matchProcess: userId is null (no security context)");
            return null;
        }

        // 1. 取该表单所有启用的绑定，按 priority DESC 排序
        LambdaQueryWrapper<FormProcessBinding> w = new LambdaQueryWrapper<>();
        w.eq(FormProcessBinding::getDefinitionId, definitionId)
         .eq(FormProcessBinding::getStatus, 1)
         .orderByDesc(FormProcessBinding::getPriority)
         .orderByDesc(FormProcessBinding::getId);
        List<FormProcessBinding> bindings = this.list(w);
        if (bindings.isEmpty()) {
            log.info("[FormBinding] matchProcess: no bindings for definitionId={}", definitionId);
            return null;
        }

        // 2. 取当前用户的部门信息（含所有子孙部门 ID）
        SysUser user = sysUserService.getById(actualUserId);
        if (user == null) {
            log.warn("[FormBinding] matchProcess: user not found id={}", actualUserId);
            return null;
        }
        Long userDeptId = user.getDeptId();
        Set<Long> userDeptAndChildren = new HashSet<>();
        if (userDeptId != null) {
            userDeptAndChildren.addAll(sysDeptService.getChildDeptIds(userDeptId));
        }

        // 3. 按优先级遍历，第一个命中就返回
        for (FormProcessBinding b : bindings) {
            if (matchBinding(b, userDeptId, userDeptAndChildren)) {
                log.info("[FormBinding] matchProcess hit: definitionId={}, bindingId={}, processDefId={}",
                        definitionId, b.getId(), b.getProcessDefinitionId());
                return b.getProcessDefinitionId();
            }
        }

        log.info("[FormBinding] matchProcess: no binding matched for definitionId={}, userId={}",
                definitionId, actualUserId);
        return null;
    }

    /**
     * 判断单条 binding 是否命中当前用户
     * <p>匹配优先级：
     * <pre>
     * ① dept_path 包含用户部门（含子部门交集）  → 命中
     * ② dept_id 精确匹配用户部门              → 命中
     * ③ business_type 匹配                     → 命中
     * ④ 以上三个都空                           → 命中（兜底）
     * </pre>
     * 只要 binding 的条件非空，且用户不满足 → 跳过，继续下一条。
     * 每条 binding 的多个条件之间是 OR 关系（满足任意一个就命中）。
     * </p>
     */
    private boolean matchBinding(FormProcessBinding b, Long userDeptId, Set<Long> userDeptAndChildren) {
        boolean hasAnyRule = false;

        // ① dept_path 匹配（逗号分隔的部门 ID 字符串 → Set → 交集判断）
        if (b.getDeptPath() != null && !b.getDeptPath().isEmpty()) {
            hasAnyRule = true;
            Set<Long> bindingDeptIds = parseDeptPath(b.getDeptPath());
            // 用户的 dept + 子部门 和 binding 的 deptPath 有交集 → 命中
            for (Long did : userDeptAndChildren) {
                if (bindingDeptIds.contains(did)) return true;
            }
        }

        // ② dept_id 精确匹配
        if (b.getDeptId() != null) {
            hasAnyRule = true;
            if (b.getDeptId().equals(userDeptId)) return true;
        }

        // ③ business_type 匹配（暂不实现——需要从流程定义拿 businessType，
        //    这里只做 binding.businessType 非空时的占位匹配）
        // TODO: Phase 1 暂不做 businessType 匹配，binding 的 businessType 字段存上以后 Phase 2 再启用
        // if (b.getBusinessType() != null && !b.getBusinessType().isEmpty()) {
        //     hasAnyRule = true;
        //     ...
        // }

        // ④ 兜底：binding 没有任何规则 → 所有人命中
        if (!hasAnyRule) return true;

        return false; // 有规则但都没命中
    }

    @Override
    public boolean existsBinding(Long definitionId, Long processDefinitionId, Long excludeId) {
        LambdaQueryWrapper<FormProcessBinding> w = new LambdaQueryWrapper<>();
        w.eq(FormProcessBinding::getDefinitionId, definitionId)
         .eq(FormProcessBinding::getProcessDefinitionId, processDefinitionId);
        if (excludeId != null) w.ne(FormProcessBinding::getId, excludeId);
        return this.count(w) > 0;
    }

    @Override
    public Map<Long, Boolean> existsBindingForFormIds(Collection<Long> definitionIds) {
        Map<Long, Boolean> result = new HashMap<>();
        if (definitionIds == null || definitionIds.isEmpty()) return result;

        // 去重 + 过滤 null
        Set<Long> ids = definitionIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) return result;

        LambdaQueryWrapper<FormProcessBinding> w = new LambdaQueryWrapper<>();
        w.in(FormProcessBinding::getDefinitionId, ids)
         .eq(FormProcessBinding::getStatus, 1)
         .select(FormProcessBinding::getDefinitionId)
         .groupBy(FormProcessBinding::getDefinitionId);

        List<FormProcessBinding> rows = this.list(w);
        Set<Long> hitIds = rows.stream()
                .map(FormProcessBinding::getDefinitionId)
                .collect(Collectors.toSet());

        for (Long id : ids) {
            result.put(id, hitIds.contains(id));
        }
        return result;
    }

    // ═══════════════════════════════════════════════════════════
    // 内部辅助方法
    // ═══════════════════════════════════════════════════════════

    /** "100,101,102" → Set<Long>{100, 101, 102} */
    private Set<Long> parseDeptPath(String deptPath) {
        Set<Long> result = new HashSet<>();
        if (deptPath == null || deptPath.isEmpty()) return result;
        for (String s : deptPath.split(",")) {
            try { result.add(Long.parseLong(s.trim())); } catch (NumberFormatException ignore) {}
        }
        return result;
    }

    /** 批量把 formName / processName 填到 binding 记录里 */
    private void enrichWithNames(List<FormProcessBinding> bindings) {
        if (bindings == null || bindings.isEmpty()) return;

        // 收集所有 definitionId / processDefinitionId
        Set<Long> processIds = bindings.stream()
                .map(FormProcessBinding::getProcessDefinitionId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        if (!processIds.isEmpty()) {
            List<WorkflowDefinition> defs = workflowDefinitionService.listByIds(processIds);
            Map<Long, WorkflowDefinition> defMap = defs.stream()
                    .collect(Collectors.toMap(WorkflowDefinition::getId, d -> d, (a, b) -> a));
            for (FormProcessBinding b : bindings) {
                WorkflowDefinition def = defMap.get(b.getProcessDefinitionId());
                if (def != null) {
                    b.setProcessName(def.getProcessName());
                    b.setProcessKey(def.getProcessKey());
                }
            }
        }
        // formName 要 JOIN form_definition，Phase 1.4 Controller 里单独查或 Service 注入 formDefinitionService
        // 这里先只填 processName，Controller 层如果需要再补 formName
    }
}