package com.cyan.modules.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cyan.common.exception.BusinessException;
import com.cyan.common.security.util.SecurityUtils;
import com.cyan.config.CustomTenantHandler;
import com.cyan.modules.workflow.entity.WorkflowApprove;
import com.cyan.modules.workflow.entity.WorkflowInstance;
import com.cyan.modules.workflow.entity.WorkflowNode;
import com.cyan.modules.workflow.entity.WfNodeRuntime;
import com.cyan.modules.workflow.mapper.WorkflowApproveMapper;
import com.cyan.modules.workflow.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import com.cyan.modules.workflow.service.WorkflowDefinitionService;
import com.cyan.modules.message.service.MessagePushService;

/**
 * 流程审批Service实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowApproveServiceImpl extends ServiceImpl<WorkflowApproveMapper, WorkflowApprove> implements WorkflowApproveService {

    private final WorkflowInstanceService workflowInstanceService;
    private final WorkflowNodeService workflowNodeService;
    private final WorkflowDefinitionService workflowDefinitionService;
    private final WfNodeRuntimeService wfNodeRuntimeService;
    /** ⭐ @Lazy 破循环依赖 */
    @Lazy
    @Autowired
    private List<WorkflowBusinessBinder> businessBinders;

    /** ⭐ 消息推送服务（@Lazy 避免循环依赖） */
    @Lazy
    @Autowired
    private MessagePushService messagePushService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean approveProcess(Long instanceId, Long userId, String action, String comment) {
        // 1. 查实例
        WorkflowInstance instance = workflowInstanceService.getById(instanceId);
        if (instance == null) throw new BusinessException("流程实例不存在");
        if (!"running".equals(instance.getStatus())) throw new BusinessException("流程已结束，无法审批");

        // 2. 权限校验：当前用户必须在当前节点的待审批人列表里
        List<Long> approvers = getCurrentNodeApprovers(instanceId);
        if (!approvers.contains(userId)) {
            throw new BusinessException("当前用户不是该节点的审批人，无权审批（待审批人=" + approvers + "）");
        }

        String userName = SecurityUtils.getUsername();

        // 3. ⭐ 更新原来的待办记录（不是 INSERT 新记录！）
        //    设计：approve 表同时充当 "待办队列" 和 "审批日志"
        //    action=null → 待办；action!=null → 已办（approve/reject/skip/transfer）
        WorkflowApprove myTodo = this.getOne(new LambdaQueryWrapper<WorkflowApprove>()
                .eq(WorkflowApprove::getInstanceId, instanceId)
                .eq(WorkflowApprove::getNodeId, instance.getCurrentNodeId())
                .eq(WorkflowApprove::getApproverId, userId)
                .isNull(WorkflowApprove::getAction));
        if (myTodo == null) {
            throw new BusinessException("未找到您在此节点的待办记录（可能已被他人处理）");
        }
        myTodo.setApproverName(userName);
        myTodo.setAction(action);
        myTodo.setComment(comment);
        myTodo.setApprovalTime(LocalDateTime.now());
        this.updateById(myTodo);

        // 3.1 ⭐ 更新节点运行时状态（会签/或签计数）
        WorkflowNode currentNode = workflowNodeService.getById(instance.getCurrentNodeId());
        WfNodeRuntime runtime = wfNodeRuntimeService.getByInstanceAndNode(instanceId, instance.getCurrentNodeId());
        String approvalType = currentNode != null && currentNode.getApprovalType() != null
                ? currentNode.getApprovalType() : "OR";

        boolean nodePassed = false;
        boolean nodeRejected = false;

        if (runtime != null && "WAITING".equals(runtime.getStatus())) {
            if ("approve".equals(action)) {
                runtime.setApprovedCount((runtime.getApprovedCount() == null ? 0 : runtime.getApprovedCount()) + 1);
                if ("AND".equalsIgnoreCase(approvalType)) {
                    // 会签：approvedCount >= totalCount 才算过
                    nodePassed = runtime.getApprovedCount() >= runtime.getTotalCount();
                } else {
                    // 或签：任一人 approve 即过
                    nodePassed = true;
                }
            } else if ("reject".equals(action)) {
                runtime.setRejectedCount((runtime.getRejectedCount() == null ? 0 : runtime.getRejectedCount()) + 1);
                if ("AND".equalsIgnoreCase(approvalType)) {
                    // 会签：任一 reject 直接节点失败
                    nodeRejected = true;
                } else {
                    // 或签：全部 reject 才算失败
                    nodeRejected = runtime.getRejectedCount() >= runtime.getTotalCount();
                }
            }
            if (nodePassed) {
                runtime.setStatus("APPROVED");
                runtime.setEndTime(LocalDateTime.now());
                // 把剩余 WAITING 的待办作废（自己的那条已被 UPDATE 成 approve，自动排除）
                cancelRemainingTodos(instanceId, instance.getCurrentNodeId());
            } else if (nodeRejected) {
                runtime.setStatus("REJECTED");
                runtime.setEndTime(LocalDateTime.now());
                cancelRemainingTodos(instanceId, instance.getCurrentNodeId());
            }
            wfNodeRuntimeService.updateById(runtime);
            log.info("[WorkflowRuntime] instanceId={}, nodeId={}, approvalType={}, approved={}, rejected={}, nodePassed={}, nodeRejected={}",
                    instanceId, instance.getCurrentNodeId(), approvalType,
                    runtime.getApprovedCount(), runtime.getRejectedCount(), nodePassed, nodeRejected);
        } else {
            // 兜底：没有 runtime 也继续走旧逻辑
            if ("approve".equals(action)) nodePassed = true;
            if ("reject".equals(action)) nodeRejected = true;
        }

        // 4. 按审批结果流转
        if (nodePassed) {
            forwardToNextNode(instance);
        } else if (nodeRejected) {
            instance.setStatus("rejected");
            instance.setEndTime(LocalDateTime.now());
            workflowInstanceService.updateById(instance);
            log.info("[Workflow] 审批驳回 instanceId={}, approver={}, comment={}", instanceId, userName, comment);
            // ⭐ 推送驳回通知给发起人
            try {
                messagePushService.pushWorkflowRejected(instance.getInitiatorId(), instance.getInitiatorName(),
                        instance.getProcessName(), instance.getCurrentNodeName(), comment);
            } catch (Exception ex) {
                log.error("推送驳回通知失败（不影响流程）: {}", ex.getMessage());
            }
            fireOnWorkflowEnd(instance, "rejected");
        } else {
            // 节点还没通过（或签还没凑齐会签的 approvedCount，或或签还没人 reject 全），等待下一个审批人
            log.info("[Workflow] 节点 {} 未结束（approvalType={}），等待下一位审批人，instanceId={}",
                    instance.getCurrentNodeName(), approvalType, instanceId);
        }

        return true;
    }

    /**
     * 把同一节点下其他审批人未处理的待办作废
     * （会签节点全过/或签节点一人过/节点被驳回时，剩余待办自动 skip）
     * <p>
     * ⭐ 设计：改成 UPDATE 模式后，自己的待办 action 已被更新为 approve/reject，
     * isNull(action) 条件自动排除自己，不需要再手动跳过 exceptUserId。
     */
    private void cancelRemainingTodos(Long instanceId, Long nodeId) {
        com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<WorkflowApprove> uw = new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<>();
        uw.eq(WorkflowApprove::getInstanceId, instanceId)
          .eq(WorkflowApprove::getNodeId, nodeId)
          .isNull(WorkflowApprove::getAction);
        uw.set(WorkflowApprove::getAction, "skip")
          .set(WorkflowApprove::getApprovalTime, LocalDateTime.now());
        int affected = baseMapper.update(null, uw);
        if (affected > 0) {
            log.info("[Workflow] 剩余待办已作废: instanceId={}, nodeId={}, 数量={}", instanceId, nodeId, affected);
        }
    }

    /**
     * 审批通过后：定位下一节点 → 更新指针 → 给下一节点审批人生成待办。
     * 下一节点是 end → 直接完结实例（status=completed）。
     * <p>增强：支持 Gateway SpEL 条件分支、并行网关、创建节点运行时状态、计算 deadline。</p>
     */
    private void forwardToNextNode(WorkflowInstance instance) {
        // ⭐ skip 跨租户查节点 — definition 可能是 admin 分发给当前租户的（node.tenantId=0）
        CustomTenantHandler.skip();
        WorkflowNode current;
        boolean reachedEnd;
        WorkflowNode firstNode;
        List<WorkflowNode> allNextUserTasks;
        try {
            // 1. 取当前节点
            current = workflowNodeService.getById(instance.getCurrentNodeId());
            if (current == null) {
                log.warn("[Workflow] 当前节点不存在，instanceId={}, currentNodeId={}",
                        instance.getId(), instance.getCurrentNodeId());
                return;
            }

            // 2. 遍历 nextNodeIds，处理 Gateway 分支判断（SpEL）
            List<Long> candidateNextIds = resolveNextNodes(current, instance);
            // ⭐ 排他网关：记录被跳过的分支节点（SKIPPED runtime）
            if ("gateway".equals(current.getNodeType())) {
                recordSkippedRuntime(current, candidateNextIds, instance);
            }
            if (candidateNextIds.isEmpty()) {
                log.warn("[Workflow] current node {} 后面没有可达节点，instanceId={}",
                        current.getNodeName(), instance.getId());
                return;
            }

            // 3. 处理每个 next 节点（并行网关 = 多个节点同时推进）
            reachedEnd = false;
            firstNode = null;
            allNextUserTasks = new ArrayList<>();
            for (Long nid : candidateNextIds) {
                WorkflowNode n = workflowNodeService.getById(nid);
                if (n == null) continue;
                if ("end".equals(n.getNodeType())) {
                    reachedEnd = true;
                } else if ("userTask".equals(n.getNodeType())) {
                    allNextUserTasks.add(n);
                    if (firstNode == null) firstNode = n;
                } else if ("gateway".equals(n.getNodeType())) {
                    // Gateway 继续往后推
                    List<Long> deeper = resolveNextNodes(n, instance);
                    // ⭐ 排他网关：记录被跳过的分支节点（SKIPPED runtime）
                    recordSkippedRuntime(n, deeper, instance);
                    for (Long dn : deeper) {
                        WorkflowNode dd = workflowNodeService.getById(dn);
                        if (dd == null) continue;
                        if ("end".equals(dd.getNodeType())) reachedEnd = true;
                        else if ("userTask".equals(dd.getNodeType())) {
                            allNextUserTasks.add(dd);
                            if (firstNode == null) firstNode = dd;
                        }
                    }
                }
            }
        } finally {
            CustomTenantHandler.reset();
        }

        // 4. 更新 instance 指针（并行取第一个）
        LocalDateTime now = LocalDateTime.now();
        if (reachedEnd && allNextUserTasks.isEmpty()) {
            instance.setCurrentNodeId(null);
            instance.setCurrentNodeName("流程结束");
            instance.setCurrentNodeDeadline(null);
            instance.setStatus("completed");
            instance.setEndTime(now);
            workflowInstanceService.updateById(instance);
            log.info("[Workflow] instanceId={} 到达 end 节点，流程完结", instance.getId());
            // ⭐ 推送完结通知给发起人
            try {
                messagePushService.pushWorkflowCompleted(instance.getInitiatorId(), instance.getInitiatorName(),
                        instance.getProcessName(), instance.getBusinessKey());
            } catch (Exception ex) {
                log.error("推送流程完结通知失败（不影响流程）: {}", ex.getMessage());
            }
            fireOnWorkflowEnd(instance, "completed");
            return;
        }

        if (firstNode == null) {
            log.warn("[Workflow] 没有找到下一个 userTask，instanceId={}", instance.getId());
            return;
        }

        // 5. 更新 instance 指针到第一个 userTask（并行时同时写多个 runtime，但指针只显示一个）
        instance.setCurrentNodeId(firstNode.getId());
        instance.setCurrentNodeName(firstNode.getNodeName());
        LocalDateTime deadline = firstNode.getTimeoutHours() != null
                ? now.plusHours(firstNode.getTimeoutHours()) : now.plusHours(72);
        instance.setCurrentNodeDeadline(deadline);
        workflowInstanceService.updateById(instance);

        // 6. 给每个 next userTask 写 runtime + approve（并行网关 = 多个节点同时生成待办）
        for (WorkflowNode tn : allNextUserTasks) {
            List<Long> assigneeIds = workflowDefinitionService.resolveAssigneeIds(tn, instance.getInitiatorId());
            String aType = tn.getApprovalType() != null ? tn.getApprovalType() : "OR";
            LocalDateTime nodeDeadline = tn.getTimeoutHours() != null ? now.plusHours(tn.getTimeoutHours()) : now.plusHours(72);

            // runtime
            WfNodeRuntime rt = new WfNodeRuntime();
            rt.setInstanceId(instance.getId());
            rt.setNodeId(tn.getId());
            rt.setNodeKey("NodeKey_" + tn.getId());
            rt.setNodeName(tn.getNodeName());
            rt.setApprovalType(aType);
            rt.setAssigneeType(tn.getAssigneeType());
            rt.setTotalCount(assigneeIds.size());
            rt.setApprovedCount(0);
            rt.setRejectedCount(0);
            rt.setStatus("WAITING");
            rt.setDeadline(nodeDeadline);
            rt.setStartTime(now);
            rt.setTenantId(instance.getTenantId());
            wfNodeRuntimeService.save(rt);

            // approve 表
            for (Long aid : assigneeIds) {
                WorkflowApprove nextTodo = new WorkflowApprove();
                nextTodo.setInstanceId(instance.getId());
                nextTodo.setNodeId(tn.getId());
                nextTodo.setNodeName(tn.getNodeName());
                nextTodo.setApproverId(aid);
                nextTodo.setApproverName(""); // ⭐ 必须塞值，数据库 approver_name NOT NULL
                nextTodo.setSignType("NORMAL");
                nextTodo.setDeadline(nodeDeadline);
                this.save(nextTodo);
            }
            // ⭐ 推送待办通知给这些 assignee
            try {
                messagePushService.pushWorkflowTodo(instance.getProcessName(), tn.getNodeName(),
                        instance.getInitiatorName(), instance.getBusinessKey(), assigneeIds);
            } catch (Exception ex) {
                log.error("推送待办通知失败（不影响流程）: {}", ex.getMessage());
            }

            log.info("[Workflow] instanceId={} 流转到 {} (approvalType={})，生成待办 {} 条",
                    instance.getId(), tn.getNodeName(), aType, assigneeIds.size());
        }
    }

    /**
     * 解析 Gateway 的下一个可达节点（处理 exclusive/parallel 分支条件）
     * <p>SpEL 表达式用 instance.variables（JSON Map）作为上下文变量
     */
    private List<Long> resolveNextNodes(WorkflowNode current, WorkflowInstance instance) {
        List<Long> result = new ArrayList<>();
        List<Long> nextIds = parseNextIds(current.getNextNodeIds());
        if (nextIds.isEmpty()) return result;

        if ("gateway".equals(current.getNodeType())) {
            String gt = current.getGatewayType() != null ? current.getGatewayType() : "exclusive";
            if ("parallel".equalsIgnoreCase(gt)) {
                // 并行网关：所有分支都走
                return nextIds;
            }
            // 排他网关：按 conditionExpr 选一个（默认取第一个没条件的分支）
            // 用 org.springframework.expression.ExpressionParser
            org.springframework.expression.ExpressionParser parser = new org.springframework.expression.spel.standard.SpelExpressionParser();
            // 把 instance.variables 解析成 Map
            java.util.Map<String, Object> vars = new java.util.HashMap<>();
            if (instance.getVariables() != null && !instance.getVariables().isEmpty()) {
                try {
                    com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
                    vars = om.readValue(instance.getVariables(), new com.fasterxml.jackson.core.type.TypeReference<>() {});
                } catch (Exception e) {
                    log.warn("[Workflow] variables JSON 解析失败，Gateway 用兜底分支: {}", e.getMessage());
                }
            }
            vars.put("initiatorId", instance.getInitiatorId());
            vars.put("processKey", instance.getProcessKey());
            
            // ⭐ 防御性：把看起来像数字的 String 自动转成 Number
            // 原因：前端 form-create el-input 组件存 "5670000"（字符串），
            //       SpEL "5670000" >= 1000000 类型不匹配 → 可能抛异常或返回 false
            for (java.util.Map.Entry<String, Object> e : vars.entrySet()) {
                Object v = e.getValue();
                if (v instanceof String s && !s.isBlank()) {
                    try {
                        if (s.contains(".")) {
                            e.setValue(Double.parseDouble(s));
                        } else {
                            e.setValue(Long.parseLong(s));
                        }
                    } catch (NumberFormatException ignore) { /* 不是数字字符串，保持原样 */ }
                }
            }

            // ⭐ 构建 SpEL 上下文：vars 同时作为 rootObject + 显式变量
            // ⭐ 必须加自定义 MapPropertyAccessor！旧版 Spring 没有内置 MapAccessor，
            //   SpEL 把 Map 当 JavaBean 找 getter → 报 Property or field 找不到
            org.springframework.expression.spel.support.StandardEvaluationContext ctx = new org.springframework.expression.spel.support.StandardEvaluationContext(vars);
            ctx.addPropertyAccessor(new com.cyan.common.spel.MapPropertyAccessor());
            ctx.setVariable("variables", vars);
            ctx.setVariable("initiatorId", instance.getInitiatorId());
            ctx.setVariable("processKey", instance.getProcessKey());

            WorkflowNode picked = null;
            WorkflowNode defaultNode = null;
            for (Long nid : nextIds) {
                WorkflowNode n = workflowNodeService.getById(nid);
                if (n == null) continue;
                // ⭐ 关键设计：条件配在「网关出边指向的下一个节点」上，不是网关自身
                // 网关可以有多个 nextNodeIds，每个目标节点配一个 conditionExpr
                // 引擎遍历所有目标节点，找到第一个条件为 true 的分支
                // 留空的目标节点 = 默认分支（所有有条件分支都不满足时走这里）
                if (n.getConditionExpr() == null || n.getConditionExpr().isEmpty()) {
                    defaultNode = n;
                    continue;
                }
                try {
                    // ⭐ SpEL 防御：strip 掉 // 注释，避免注释里的中文导致解析器报 Unsupported character
                    String cleanExpr = n.getConditionExpr();
                    int commentIdx = cleanExpr.indexOf("//");
                    if (commentIdx >= 0) {
                        cleanExpr = cleanExpr.substring(0, commentIdx).trim();
                    }
                    Object val = parser.parseExpression(cleanExpr).getValue(ctx);
                    if (Boolean.TRUE.equals(val)) {
                        picked = n;
                        break;
                    }
                } catch (Exception e) {
                    log.warn("[Workflow] Gateway condition 解析失败 expr={}, err={}", n.getConditionExpr(), e.getMessage());
                }
            }
            WorkflowNode chosen = picked != null ? picked : defaultNode;
            if (chosen != null) result.add(chosen.getId());
            log.info("[Workflow] Gateway {} 分支选择: picked={}, default={}, chosen={}",
                    current.getNodeName(), picked != null ? picked.getNodeName() : "",
                    defaultNode != null ? defaultNode.getNodeName() : "",
                    chosen != null ? chosen.getNodeName() : "null");
            return result;
        }

        // 非 gateway：直接返回 next
        return nextIds;
    }

    private List<Long> parseNextIds(String nextNodeIds) {
        List<Long> result = new ArrayList<>();
        if (nextNodeIds == null || nextNodeIds.isEmpty()) return result;
        for (String s : nextNodeIds.split(",")) {
            try { result.add(Long.parseLong(s.trim())); } catch (NumberFormatException ignore) {}
        }
        return result;
    }

    @Override
    public Page<WorkflowApprove> queryApproveList(Integer pageNum, Integer pageSize, Long instanceId) {
        LambdaQueryWrapper<WorkflowApprove> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WorkflowApprove::getInstanceId, instanceId);
        wrapper.orderByDesc(WorkflowApprove::getApprovalTime);
        
        Page<WorkflowApprove> page = new Page<>(pageNum, pageSize);
        return this.page(page, wrapper);
    }

    /**
     * ⭐ 触发业务回调：流程完结时通知所有 binder
     */
    private void fireOnWorkflowEnd(WorkflowInstance instance, String finalStatus) {
        String businessKey = instance.getBusinessKey();
        if (businessKey == null || businessKey.isEmpty() || businessBinders == null) return;
        for (WorkflowBusinessBinder binder : businessBinders) {
            if (binder.supports(businessKey)) {
                try {
                    binder.onWorkflowEnd(businessKey, finalStatus);
                    log.info("[Workflow→Business] onWorkflowEnd: businessKey={}, finalStatus={}, binder={}",
                            businessKey, finalStatus, binder.getClass().getSimpleName());
                } catch (Exception e) {
                    log.error("[Workflow→Business] onWorkflowEnd 失败: businessKey={}, binder={}",
                            businessKey, binder.getClass().getSimpleName(), e);
                }
            }
        }
    }

    @Override
    public List<Long> getCurrentNodeApprovers(Long instanceId) {
        WorkflowInstance instance = workflowInstanceService.getById(instanceId);
        if (instance == null) return List.of();

        // 优先从 approve 表查 action=null 的待办审批人（start 之后已经写过了）
        List<WorkflowApprove> todos = this.list(new LambdaQueryWrapper<WorkflowApprove>()
                .eq(WorkflowApprove::getInstanceId, instanceId)
                .eq(WorkflowApprove::getNodeId, instance.getCurrentNodeId())
                .isNull(WorkflowApprove::getAction));
        if (!todos.isEmpty()) {
            return todos.stream().map(WorkflowApprove::getApproverId).toList();
        }

        // 兜底：从 WorkflowNode 配置解析
        WorkflowNode node = workflowNodeService.getById(instance.getCurrentNodeId());
        if (node == null) return List.of();
        return workflowDefinitionService.resolveAssigneeIds(node, instance.getInitiatorId());
    }

    // ================== Phase 3 新方法：加签 / 转办 ==================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean addSigner(Long instanceId, Long nodeId, Long addByUserId, List<Long> addUserIds, String comment) {
        if (addUserIds == null || addUserIds.isEmpty()) {
            throw new com.cyan.common.exception.BusinessException("请选择至少一个加签人");
        }

        WorkflowInstance instance = workflowInstanceService.getById(instanceId);
        if (instance == null) throw new com.cyan.common.exception.BusinessException("流程实例不存在");
        if (!instance.getCurrentNodeId().equals(nodeId)) {
            throw new com.cyan.common.exception.BusinessException("只能给当前待审批节点加签");
        }

        // 1. 更新 runtime：total_count += addUserIds.size()，add_signers 记录
        WfNodeRuntime runtime = wfNodeRuntimeService.getByInstanceAndNode(instanceId, nodeId);
        if (runtime != null) {
            runtime.setTotalCount(runtime.getTotalCount() == null ? addUserIds.size() : runtime.getTotalCount() + addUserIds.size());
            String existed = runtime.getAddSigners();
            StringBuilder sb = new StringBuilder(existed == null ? "" : existed);
            for (Long uid : addUserIds) {
                if (sb.length() > 0) sb.append(",");
                sb.append(uid);
            }
            runtime.setAddSigners(sb.toString());
            wfNodeRuntimeService.updateById(runtime);
        }

        // 2. 写 approve 记录（sign_type=ADD，parent_approve_id 指向加签发起人自己那条待办）
        WorkflowApprove parentApprove = this.getOne(new LambdaQueryWrapper<WorkflowApprove>()
                .eq(WorkflowApprove::getInstanceId, instanceId)
                .eq(WorkflowApprove::getNodeId, nodeId)
                .eq(WorkflowApprove::getApproverId, addByUserId)
                .isNull(WorkflowApprove::getAction));
        Long parentId = parentApprove != null ? parentApprove.getId() : null;

        for (Long uid : addUserIds) {
            WorkflowApprove add = new WorkflowApprove();
            add.setInstanceId(instanceId);
            add.setNodeId(nodeId);
            add.setNodeName(instance.getCurrentNodeName());
            add.setApproverId(uid);
            add.setApproverName("");
            add.setSignType("ADD");
            add.setParentApproveId(parentId);
            add.setDeadline(runtime != null ? runtime.getDeadline() : null);
            this.save(add);
        }

        log.info("[WorkflowAddSign] instanceId={}, nodeId={}, addBy={}, addUsers={}, comment={}",
                instanceId, nodeId, addByUserId, addUserIds, comment);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean transfer(Long instanceId, Long nodeId, Long fromUserId, Long toUserId, String comment) {
        WorkflowInstance instance = workflowInstanceService.getById(instanceId);
        if (instance == null) throw new com.cyan.common.exception.BusinessException("流程实例不存在");
        if (!instance.getCurrentNodeId().equals(nodeId)) {
            throw new com.cyan.common.exception.BusinessException("只能转办当前待审批节点的待办");
        }

        // 1. 找到 fromUserId 的那条待办，把 action 改成 transfer
        WorkflowApprove fromTodo = this.getOne(new LambdaQueryWrapper<WorkflowApprove>()
                .eq(WorkflowApprove::getInstanceId, instanceId)
                .eq(WorkflowApprove::getNodeId, nodeId)
                .eq(WorkflowApprove::getApproverId, fromUserId)
                .isNull(WorkflowApprove::getAction));
        if (fromTodo == null) {
            throw new com.cyan.common.exception.BusinessException("你不是当前节点的审批人，无法转办");
        }
        fromTodo.setAction("transfer");
        fromTodo.setComment("转办给用户ID=" + toUserId + (comment != null ? " | " + comment : ""));
        fromTodo.setApprovalTime(LocalDateTime.now());
        this.updateById(fromTodo);

        // 2. 新写一条 toUserId 的待办
        WorkflowApprove toTodo = new WorkflowApprove();
        toTodo.setInstanceId(instanceId);
        toTodo.setNodeId(nodeId);
        toTodo.setNodeName(instance.getCurrentNodeName());
        toTodo.setApproverId(toUserId);
        toTodo.setApproverName("");
        toTodo.setSignType(fromTodo.getSignType()); // 继承 NORMAL/ADD
        toTodo.setParentApproveId(fromTodo.getParentApproveId());
        toTodo.setDeadline(fromTodo.getDeadline());
        this.save(toTodo);

        log.info("[WorkflowTransfer] instanceId={}, nodeId={}, from={}, to={}, comment={}",
                instanceId, nodeId, fromUserId, toUserId, comment);
        return true;
    }

    // ====================================================================
    // 排他网关 SKIPPED 分支记录辅助方法（与 WorkflowInstanceServiceImpl 保持同构）
    // ====================================================================

    /**
     * 排他网关分流后，把被跳过的分支节点（及其后续整条不可达链路）记录为 SKIPPED runtime。
     * 只对 EXCLUSIVE 网关生效，PARALLEL 网关所有分支都走，没有跳过的概念。
     */
    private void recordSkippedRuntime(WorkflowNode gateway, List<Long> chosenIds, WorkflowInstance instance) {
        String gt = gateway.getGatewayType() != null ? gateway.getGatewayType() : "exclusive";
        if (!"exclusive".equalsIgnoreCase(gt)) return;

        List<Long> allNextIds = parseNextIds(gateway.getNextNodeIds());
        Set<Long> chosenSet = new HashSet<>(chosenIds);
        List<Long> skippedRootIds = allNextIds.stream()
                .filter(id -> !chosenSet.contains(id))
                .toList();
        if (skippedRootIds.isEmpty()) return;

        // 递归展开：每个被跳过的根节点 + 它后续所有可达节点
        Set<Long> allSkippedNodeIds = new LinkedHashSet<>();
        CustomTenantHandler.skip();
        try {
            for (Long rootId : skippedRootIds) {
                collectDescendants(rootId, allSkippedNodeIds);
            }
        } finally {
            CustomTenantHandler.reset();
        }
        if (allSkippedNodeIds.isEmpty()) return;

        // 查已有 runtime（排除已经写了 WAITING/APPROVED 的节点）
        List<WfNodeRuntime> existing = wfNodeRuntimeService.list(
                new LambdaQueryWrapper<WfNodeRuntime>()
                        .eq(WfNodeRuntime::getInstanceId, instance.getId())
                        .in(WfNodeRuntime::getNodeId, allSkippedNodeIds));
        Set<Long> existingIds = existing.stream().map(WfNodeRuntime::getNodeId).collect(java.util.stream.Collectors.toSet());

        LocalDateTime now = LocalDateTime.now();
        for (Long nodeId : allSkippedNodeIds) {
            if (existingIds.contains(nodeId)) continue;
            WorkflowNode node = workflowNodeService.getById(nodeId);
            if (node == null) continue;
            WfNodeRuntime rt = new WfNodeRuntime();
            rt.setInstanceId(instance.getId());
            rt.setNodeId(nodeId);
            rt.setNodeKey("NodeKey_" + nodeId);
            rt.setNodeName(node.getNodeName());
            rt.setApprovalType(node.getApprovalType() != null ? node.getApprovalType() : "OR");
            rt.setStatus("SKIPPED");
            rt.setStartTime(now);
            rt.setEndTime(now);
            rt.setTenantId(instance.getTenantId());
            wfNodeRuntimeService.save(rt);
        }
        log.info("[Workflow] instanceId={} Gateway [{}] SKIPPED 分支已记录 {} 个节点",
                instance.getId(), gateway.getNodeName(), allSkippedNodeIds.size());
    }

    /** 递归收集 nodeId 及其后续所有可达节点 ID（防环） */
    private void collectDescendants(Long nodeId, Set<Long> result) {
        if (!result.add(nodeId)) return; // 已加入或有环，终止
        CustomTenantHandler.skip();
        WorkflowNode node;
        try {
            node = workflowNodeService.getById(nodeId);
        } finally {
            CustomTenantHandler.reset();
        }
        if (node == null) return;
        List<Long> nextIds = parseNextIds(node.getNextNodeIds());
        for (Long nid : nextIds) {
            collectDescendants(nid, result);
        }
    }
}