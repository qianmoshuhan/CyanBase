package com.cyan.modules.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cyan.common.exception.BusinessException;
import com.cyan.common.security.util.SecurityUtils;
import com.cyan.modules.sydyd.entity.ThreeBigItem;
import com.cyan.modules.sydyd.mapper.ThreeBigItemMapper;
import com.cyan.config.CustomTenantHandler;
import com.cyan.modules.workflow.entity.WfDefinitionTenant;
import com.cyan.modules.workflow.entity.WorkflowDefinition;
import com.cyan.modules.workflow.entity.WorkflowInstance;
import com.cyan.modules.workflow.entity.WorkflowNode;
import com.cyan.modules.workflow.entity.WorkflowApprove;
import com.cyan.modules.workflow.entity.WfNodeRuntime;
import com.cyan.modules.workflow.mapper.WorkflowApproveMapper;
import com.cyan.modules.workflow.mapper.WorkflowInstanceMapper;
import com.cyan.modules.workflow.service.WfDefinitionTenantService;
import com.cyan.modules.message.service.MessagePushService;
import com.cyan.modules.workflow.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 流程实例Service实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowInstanceServiceImpl extends ServiceImpl<WorkflowInstanceMapper, WorkflowInstance> implements WorkflowInstanceService {

    private final WorkflowDefinitionService workflowDefinitionService;
    private final WorkflowNodeService workflowNodeService;
    private final WorkflowApproveMapper approveMapper;
    private final ThreeBigItemMapper threeBigItemMapper;
    private final WfNodeRuntimeService wfNodeRuntimeService;
    private final WfDefinitionTenantService wfDefinitionTenantService;
    /** 辅助：从节点列表按 ID 查 */
    private WorkflowNode findNodeById(List<WorkflowNode> nodes, Long id) {
        return nodes.stream().filter(n -> n.getId().equals(id)).findFirst().orElse(null);
    }

    /**
     * 解析 Gateway 的下一个可达节点（排他/并行）。
     * <p>和 WorkflowApproveServiceImpl.resolveNextNodes 里的逻辑一致，复用 SpEL 引擎。</p>
     */
    private List<Long> resolveGatewayBranch(WorkflowNode gateway, List<Long> nextIds, WorkflowInstance instance) {
        String gt = gateway.getGatewayType() != null ? gateway.getGatewayType() : "exclusive";

        if ("parallel".equalsIgnoreCase(gt)) {
            // 并行网关：所有分支都走
            return nextIds;
        }

        // 排他网关：用 SpEL 条件选一个
        org.springframework.expression.ExpressionParser parser = new org.springframework.expression.spel.standard.SpelExpressionParser();
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
        // 原因：前端 form-create el-input 组件存 "5630000"（字符串），
        //       SpEL "5630000" >= 1000000 类型不匹配可能走兜底分支
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

        // ⭐ 必须加自定义 MapPropertyAccessor！旧版 Spring 没有内置 MapAccessor
        org.springframework.expression.spel.support.StandardEvaluationContext ctx = new org.springframework.expression.spel.support.StandardEvaluationContext(vars);
        ctx.addPropertyAccessor(new com.cyan.common.spel.MapPropertyAccessor());
        ctx.setVariable("variables", vars);
        ctx.setVariable("initiatorId", instance.getInitiatorId());
        ctx.setVariable("processKey", instance.getProcessKey());

        // 需要查目标节点的 conditionExpr — 用 skip 跨租户查（definition 可能是 admin 分发给当前租户的）
        CustomTenantHandler.skip();
        try {
            WorkflowNode picked = null;
            WorkflowNode defaultNode = null;
            for (Long nid : nextIds) {
                WorkflowNode n = workflowNodeService.getById(nid);
                if (n == null) continue;
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
            log.info("[Workflow] Gateway {} 分支选择: picked={}, default={}, chosen={}",
                    gateway.getNodeName(), picked != null ? picked.getNodeName() : "",
                    defaultNode != null ? defaultNode.getNodeName() : "",
                    chosen != null ? chosen.getNodeName() : "null");
            return chosen != null ? List.of(chosen.getId()) : List.of();
        } finally {
            CustomTenantHandler.reset();
        }
    }

    /** ⭐ 所有业务回调实现（@Lazy 破循环依赖：业务 Service 可能反向依赖 workflow Service） */
    @Lazy
    @Autowired
    private List<WorkflowBusinessBinder> businessBinders;

    /** ⭐ 消息推送服务（@Lazy 避免和 message Service 形成循环依赖） */
    @Lazy
    @Autowired
    private MessagePushService messagePushService;

    /**
     * ⭐ 发起权限校验：definition 要么属于我自己租户，要么是平台admin租户(0/null)，要么分发给了我。
     * 逻辑和 WorkflowDefinitionController.checkReadPermission 完全一致。
     */
    private void checkStartPermission(WorkflowDefinition definition) {
        Long myTenantId = SecurityUtils.getTenantId();
        Long defTenantId = definition.getTenantId();

        // 1. 自己租户的 definition — 直接放行
        if (myTenantId != null && myTenantId > 0 && myTenantId.equals(defTenantId)) {
            return;
        }
        // 2. 平台管理员（admin, tenantId 为 null/0）— 放行
        if (myTenantId == null || myTenantId <= 0) {
            return;
        }
        // 3. 分发给我的 — 查 wf_definition_tenant
        boolean distributed = wfDefinitionTenantService.exists(definition.getId(), myTenantId, 1);
        if (!distributed) {
            throw new BusinessException(403, "无权发起该流程（definitionId=" + definition.getId()
                    + "，既非本租户所有，也未分发给您）");
        }
    }

    /**
     * 发起流程实例。
     * <p>
     * ⭐ propagation=REQUIRES_NEW：workflow 发起在<strong>独立事务</strong>执行。
     * 原因：ThreeBigItemServiceImpl.submit() 外层也开了 @Transactional(REQUIRED)，
     * 默认嵌套时 startProcess 抛的 RuntimeException 会把外层事务一起标为 rollback-only，
     * 导致外层 catch 住后业务更新仍会 UnexpectedRollbackException。
     * 独立事务后，workflow 异常只回滚 workflow 自己的事务，业务主事务不受影响。
     * </p>
     * <p>
     * ⭐ CustomTenantHandler.skip()：definition 可能是平台 admin 创建后分发给当前租户的
     * （definition.tenant_id=0 ≠ 当前 tenant_id），必须跳过租户拦截查询。
     * 配合 checkReadPermission 校验权限（OWN / 平台admin / DISTRIBUTED 三选一）。
     * </p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRES_NEW)
    public Long startProcess(Long definitionId, String businessKey, String variables) {
        // ⭐ 跨租户查询：跳过租户拦截，后面自己做权限校验
        CustomTenantHandler.skip();
        WorkflowDefinition definition;
        try {
            definition = workflowDefinitionService.getById(definitionId);
        } finally {
            CustomTenantHandler.reset();
        }
        if (definition == null) {
            throw new BusinessException("流程定义不存在（definitionId=" + definitionId + "，可能是跨租户分发未授权）");
        }

        // ⭐ 权限校验：当前租户能发起这个 definition 吗？
        checkStartPermission(definition);

        if (definition.getStatus() != 1) {
            throw new BusinessException("流程未发布，无法发起");
        }

        // 获取当前用户
        Long userId = SecurityUtils.getUserId();
        String userName = SecurityUtils.getUsername();

        // ⭐ 跨租户查节点：同样要 skip，因为节点和 definition 同属一个 tenant_id
        CustomTenantHandler.skip();
        List<WorkflowNode> nodes;
        WorkflowNode startNode;
        try {
            nodes = workflowNodeService.listByDefinitionId(definitionId);
            if (definition.getStartNodeId() != null) {
                startNode = workflowNodeService.getById(definition.getStartNodeId());
            } else {
                startNode = nodes.stream()
                        .filter(node -> "start".equals(node.getNodeType()))
                        .findFirst()
                        .orElse(null);
            }
        } finally {
            CustomTenantHandler.reset();
        }

        if (startNode == null) {
            throw new BusinessException("流程定义的起始节点不存在");
        }
        
        // 创建流程实例
        WorkflowInstance instance = new WorkflowInstance();
        instance.setDefinitionId(definitionId);
        instance.setProcessKey(definition.getProcessKey());
        instance.setProcessName(definition.getProcessName());
        instance.setBusinessKey(businessKey);
        instance.setCurrentNodeId(startNode.getId());
        instance.setCurrentNodeName(startNode.getNodeName());
        instance.setStatus("running");
        instance.setInitiatorId(userId);
        instance.setInitiatorName(userName);
        instance.setStartTime(LocalDateTime.now());
        instance.setVariables(variables);
        
        this.save(instance);

        // 自动定位到第一个 userTask 节点（跳过 start 节点本身），生成第一条待办
        generateFirstTodo(instance, nodes, startNode);

        // ⭐ 推送通知：发起人收到「已提交」确认（推送失败只记日志，不影响流程发起）
        try {
            messagePushService.pushWorkflowStarted(userId, userName, definition.getProcessName(), businessKey);
        } catch (Exception ex) {
            log.error("推送流程发起通知失败（不影响流程）: {}", ex.getMessage());
        }

        log.info("流程发起成功: instanceId={}, processName={}, initiator={}, businessKey={}", 
                instance.getId(), definition.getProcessName(), userName, businessKey);

        // ⭐ 回调业务模块：回写 workflowInstanceId 到业务表
        if (businessKey != null && !businessKey.isEmpty() && businessBinders != null) {
            for (WorkflowBusinessBinder binder : businessBinders) {
                if (binder.supports(businessKey)) {
                    try {
                        binder.bindBusiness(businessKey, instance.getId());
                        log.info("[Workflow→Business] bindBusiness: businessKey={}, instanceId={}, binder={}",
                                businessKey, instance.getId(), binder.getClass().getSimpleName());
                    } catch (Exception e) {
                        // 回调失败只记日志，不影响流程发起——业务表回写可以补，但流程实例不能丢
                        log.error("[Workflow→Business] bindBusiness 失败: businessKey={}, binder={}",
                                businessKey, binder.getClass().getSimpleName(), e);
                    }
                }
            }
        }
        
        return instance.getId();
    }

    /**
     * 自动跳转到第一个 userTask 节点 + 生成待办。
     * <p>
     * ⭐ 核心逻辑：start 节点可能直接连 gateway（不连 userTask），
     * 所以这里用 <strong>while 迭代</strong> 一直走到第一个 userTask 为止，
     * 网关用 instance.variables 解析排他/并行分支。
     * </p>
     */
    private void generateFirstTodo(WorkflowInstance instance, List<WorkflowNode> allNodes, WorkflowNode startNode) {
        // 1. 从 start 开始，迭代走直到找到 userTask
        //    可能的链路：start → userTask（直接） / start → gateway → userTask / start → gateway → gateway → userTask
        WorkflowNode current = startNode;
        WorkflowNode firstUserTask = null;
        int safetyCounter = 0;
        while (current != null && safetyCounter++ < 20) {
            String nextIds = current.getNextNodeIds();
            if (nextIds == null || nextIds.isEmpty()) {
                log.warn("[Workflow] 节点没有 nextNodeIds，instanceId={}, node={}", instance.getId(), current.getNodeName());
                break;
            }
            List<Long> nextIdList = Arrays.stream(nextIds.split(","))
                    .map(String::trim).filter(s -> !s.isEmpty()).map(Long::parseLong).toList();
            if (nextIdList.isEmpty()) break;

            // ⭐ 如果 current 是 gateway，先解析分支拿到真正的目标节点
            if ("gateway".equals(current.getNodeType())) {
                List<Long> resolvedIds = resolveGatewayBranch(current, nextIdList, instance);
                if (resolvedIds.isEmpty()) {
                    log.warn("[Workflow] Gateway 解析结果为空，instanceId={}, gateway={}", instance.getId(), current.getNodeName());
                    break;
                }
                // ⭐ 排他网关：记录被跳过的分支节点（SKIPPED runtime）
                recordSkippedRuntime(current, resolvedIds, instance);
                // 取第一个解析后的 ID（并行网关也先处理第一个分支，后续并行激活在 approve 那边走）
                current = findNodeById(allNodes, resolvedIds.get(0));
                continue;
            }

            // 非 gateway：看 next 里有没有 userTask
            for (Long nid : nextIdList) {
                WorkflowNode n = findNodeById(allNodes, nid);
                if (n != null && "userTask".equals(n.getNodeType())) {
                    firstUserTask = n;
                    break;
                }
                // next 里有 gateway 也继续走
                if (n != null && "gateway".equals(n.getNodeType())) {
                    current = n;
                    break;
                }
            }
            if (firstUserTask != null) break;
            // 如果 current 没有被改成 gateway，而且也没找到 userTask，那就是 start → end 之类的异常链路
            if (!"gateway".equals(current.getNodeType())) break;
        }

        if (firstUserTask == null) {
            log.warn("[Workflow] 迭代走完没找到 userTask 节点，instanceId={}, 当前停在={}", instance.getId(), current != null ? current.getNodeName() : "null");
            return;
        }
        log.info("[Workflow] instanceId={} 定位到第一个 userTask: {}", instance.getId(), firstUserTask.getNodeName());

        // 3. 更新 instance 指针到第一个 userTask 节点
        instance.setCurrentNodeId(firstUserTask.getId());
        instance.setCurrentNodeName(firstUserTask.getNodeName());

        // ⭐ 计算 deadline：node.timeoutHours 有值就用，默认 72 小时
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime deadline = firstUserTask.getTimeoutHours() != null
                ? now.plusHours(firstUserTask.getTimeoutHours())
                : now.plusHours(72);
        instance.setCurrentNodeDeadline(deadline);
        this.updateById(instance);

        // 4. 写节点运行时状态（会签/或签核心表）
        List<Long> assigneeIds = workflowDefinitionService.resolveAssigneeIds(firstUserTask, instance.getInitiatorId());
        String approvalType = firstUserTask.getApprovalType() != null ? firstUserTask.getApprovalType() : "OR";
        WfNodeRuntime runtime = new WfNodeRuntime();
        runtime.setInstanceId(instance.getId());
        runtime.setNodeId(firstUserTask.getId());
        runtime.setNodeKey("NodeKey_" + firstUserTask.getId());
        runtime.setNodeName(firstUserTask.getNodeName());
        runtime.setApprovalType(approvalType);
        runtime.setAssigneeType(firstUserTask.getAssigneeType());
        runtime.setTotalCount(assigneeIds.size());
        runtime.setApprovedCount(0);
        runtime.setRejectedCount(0);
        runtime.setStatus("WAITING");
        runtime.setDeadline(deadline);
        runtime.setStartTime(now);
        runtime.setTenantId(instance.getTenantId());
        wfNodeRuntimeService.save(runtime);

        // 5. 给审批人写入 approve 表（action=null 表示待办）
        for (Long assigneeId : assigneeIds) {
            WorkflowApprove approve = new WorkflowApprove();
            approve.setInstanceId(instance.getId());
            approve.setNodeId(firstUserTask.getId());
            approve.setNodeName(firstUserTask.getNodeName());
            approve.setApproverId(assigneeId);
            approve.setApproverName("");
            approve.setSignType("NORMAL");
            approve.setDeadline(deadline);
            approveMapper.insert(approve);
        }

        // ⭐ 推送待办通知给审批人（推送失败只记日志，不阻塞流程）
        try {
            messagePushService.pushWorkflowTodo(instance.getProcessName(), firstUserTask.getNodeName(),
                    instance.getInitiatorName(), instance.getBusinessKey(), assigneeIds);
        } catch (Exception ex) {
            log.error("推送待办通知失败（不影响流程）: {}", ex.getMessage());
        }

        log.info("[Workflow] instanceId={} 已跳转到 userTask 节点 {}，approvalType={}, totalApprovers={}, 生成待办 {} 条",
                instance.getId(), firstUserTask.getNodeName(), approvalType, assigneeIds.size(), assigneeIds.size());
    }



    @Override
    public Page<WorkflowInstance> queryMyInitiatedList(Integer pageNum, Integer pageSize, Long userId,
                                                       String status, String keyword,
                                                       String startTime, String endTime) {
        LambdaQueryWrapper<WorkflowInstance> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WorkflowInstance::getInitiatorId, userId);

        if (status != null && !status.isEmpty()) {
            wrapper.eq(WorkflowInstance::getStatus, status);
        }
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.like(WorkflowInstance::getProcessName, keyword.trim());
        }
        if (startTime != null && !startTime.isEmpty()) {
            wrapper.ge(WorkflowInstance::getCreateTime, startTime);
        }
        if (endTime != null && !endTime.isEmpty()) {
            wrapper.le(WorkflowInstance::getCreateTime, endTime);
        }

        wrapper.orderByDesc(WorkflowInstance::getCreateTime);

        Page<WorkflowInstance> page = new Page<>(pageNum, pageSize);
        return this.page(page, wrapper);
    }

    @Override
    public Page<WorkflowInstance> queryMyTodoList(Integer pageNum, Integer pageSize, Long userId) {
        // 1. 查当前用户在 approve 表里 action=null 的待办 → 拿到 instanceId
        List<Long> instanceIds = approveMapper.selectList(new LambdaQueryWrapper<WorkflowApprove>()
                .eq(WorkflowApprove::getApproverId, userId)
                .isNull(WorkflowApprove::getAction)
                .select(WorkflowApprove::getInstanceId)
                .groupBy(WorkflowApprove::getInstanceId))
                .stream().map(WorkflowApprove::getInstanceId).toList();

        if (instanceIds.isEmpty()) return new Page<>(pageNum, pageSize);

        // 2. 反查 instance 表（状态仍是 running）
        Page<WorkflowInstance> page = new Page<>(pageNum, pageSize);
        Page<WorkflowInstance> result = this.page(page, new LambdaQueryWrapper<WorkflowInstance>()
                .in(WorkflowInstance::getId, instanceIds)
                .eq(WorkflowInstance::getStatus, "running")
                .orderByDesc(WorkflowInstance::getCreateTime));

        // 3. 根据 businessKey 批量回填业务事项名称（避免 N+1）
        fillItemNames(result.getRecords());
        return result;
    }

    @Override
    public Page<WorkflowInstance> queryMyDoneList(Integer pageNum, Integer pageSize, Long userId) {
        // 1. 查当前用户在 approve 表里 action 非空的已办
        List<Long> instanceIds = approveMapper.selectList(new LambdaQueryWrapper<WorkflowApprove>()
                .eq(WorkflowApprove::getApproverId, userId)
                .isNotNull(WorkflowApprove::getAction)
                .select(WorkflowApprove::getInstanceId)
                .groupBy(WorkflowApprove::getInstanceId))
                .stream().map(WorkflowApprove::getInstanceId).toList();

        if (instanceIds.isEmpty()) return new Page<>(pageNum, pageSize);

        // 2. 反查 instance（所有状态都算已办）
        Page<WorkflowInstance> page = new Page<>(pageNum, pageSize);
        Page<WorkflowInstance> result = this.page(page, new LambdaQueryWrapper<WorkflowInstance>()
                .in(WorkflowInstance::getId, instanceIds)
                .orderByDesc(WorkflowInstance::getCreateTime));

        // 3. 根据 businessKey 批量回填业务事项名称
        fillItemNames(result.getRecords());
        return result;
    }

    /**
     * 批量回填业务事项名称。
     * businessKey 格式约定：threeBigItem:{itemId} → 查 ThreeBigItem.itemName
     */
    private void fillItemNames(List<WorkflowInstance> instances) {
        if (instances == null || instances.isEmpty()) return;

        // 1. 从 businessKey 中提取所有 itemId
        Set<Long> itemIds = new HashSet<>();
        for (WorkflowInstance inst : instances) {
            String bk = inst.getBusinessKey();
            if (bk != null && bk.startsWith("threeBigItem:")) {
                try {
                    itemIds.add(Long.parseLong(bk.substring("threeBigItem:".length())));
                } catch (NumberFormatException ignored) {}
            }
        }
        if (itemIds.isEmpty()) return;

        // 2. 一次性查 ThreeBigItem（MP 自动逻辑删除隔离，不会查出已删除事项）
        List<ThreeBigItem> items = threeBigItemMapper.selectBatchIds(itemIds);
        Map<Long, String> idToName = items.stream()
                .collect(Collectors.toMap(ThreeBigItem::getId, ThreeBigItem::getItemName, (a, b) -> a));

        // 3. 回填
        for (WorkflowInstance inst : instances) {
            String bk = inst.getBusinessKey();
            if (bk != null && bk.startsWith("threeBigItem:")) {
                try {
                    Long itemId = Long.parseLong(bk.substring("threeBigItem:".length()));
                    inst.setItemName(idToName.getOrDefault(itemId, "[事项已删除]"));
                } catch (NumberFormatException e) {
                    inst.setItemName(inst.getProcessName());
                }
            } else {
                inst.setItemName(inst.getProcessName());
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean cancelProcess(Long instanceId, Long userId) {
        WorkflowInstance instance = this.getById(instanceId);
        if (instance == null) {
            throw new BusinessException("流程实例不存在");
        }
        
        if (!instance.getInitiatorId().equals(userId)) {
            throw new BusinessException("只有发起人可以取消流程");
        }
        
        if (!"running".equals(instance.getStatus())) {
            throw new BusinessException("流程已结束，无法取消");
        }
        
        instance.setStatus("cancelled");
        instance.setEndTime(LocalDateTime.now());
        boolean ok = this.updateById(instance);
        
        // ⭐ 取消也算完结，回调业务模块
        if (ok) fireOnWorkflowEnd(instance, "cancelled");
        return ok;
    }

    /**
     * 流程完结时（completed/rejected/cancelled）通知所有业务 binder。
     * 公共方法：WorkflowApproveServiceImpl 那边也有一份同样的（各自管自己的完结场景）。
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

    /**
     * 业务单据删除时级联清理工作流脏数据。
     * <p>
     * 场景：用户在三重一大事项列表里把事项删了，但走的是 MP 逻辑删除（delFlag=1），
     * 工作流的 wf_process_instance / wf_approve 表里的记录还在，
     * 导致审批人登录后仍能在"我的待办"看到这条脏数据，且前台无法删除。
     * <p>
     * 本方法做物理删除：先删 approve 表（FK 依赖），再删 instance 表。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteByBusinessKey(String businessKey) {
        if (businessKey == null || businessKey.isEmpty()) return;

        // 1. 查出所有匹配的实例 ID（逻辑删除也能查到，先查再物理删）
        List<WorkflowInstance> instances = this.list(new LambdaQueryWrapper<WorkflowInstance>()
                .eq(WorkflowInstance::getBusinessKey, businessKey));
        if (instances == null || instances.isEmpty()) return;

        List<Long> instanceIds = instances.stream().map(WorkflowInstance::getId).toList();
        log.warn("[Workflow] 业务删除级联清理: businessKey={}, 待删 instanceIds={}", businessKey, instanceIds);

        // 2. 物理删 wf_approve（审批记录表）
        int approveDel = approveMapper.delete(new LambdaQueryWrapper<WorkflowApprove>()
                .in(WorkflowApprove::getInstanceId, instanceIds));
        log.info("[Workflow] 已物理删除 wf_approve: {} 条", approveDel);

        // 3. 物理删 wf_node_runtime（节点运行时表）
        int runtimeDel = wfNodeRuntimeService.remove(new LambdaQueryWrapper<WfNodeRuntime>()
                .in(WfNodeRuntime::getInstanceId, instanceIds)) ? 1 : 0;
        log.info("[Workflow] 已物理删除 wf_node_runtime: {} 条", runtimeDel);

        // 4. 物理删 wf_process_instance（关闭逻辑删除过滤器，强制物理删）
        // 用 XML 写的自定义 deleteCustom 或直接写 wrapper
        int instDel = baseMapper.delete(new LambdaQueryWrapper<WorkflowInstance>()
                .in(WorkflowInstance::getId, instanceIds));
        log.info("[Workflow] 已物理删除 wf_process_instance: {} 条", instDel);
    }

    /**
     * ⭐ 手动删除流程实例（操作人自己发起的已撤销/已驳回流程）
     * 审计约束：completed / running 状态不允许删除
     *
     * @param id         实例ID
     * @param operatorId 当前登录用户ID（只能删自己发起的）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteInstanceById(Long id, Long operatorId) {
        WorkflowInstance inst = this.getById(id);
        if (inst == null) {
            throw new BusinessException("流程实例不存在（可能已被删除）");
        }
        // 权限：只能删自己发起的
        if (!inst.getInitiatorId().equals(operatorId)) {
            throw new BusinessException("无权删除：只能删除自己发起的流程");
        }
        // 审计约束：只有 cancelled / rejected 可以手动删
        String status = inst.getStatus();
        if ("running".equals(status) || "completed".equals(status)) {
            throw new BusinessException(
                "审计约束：已完成或运行中的流程不可手动删除（当前状态=" + status + "）");
        }
        // 级联物理删除 3 张表
        int approveDel = approveMapper.delete(new LambdaQueryWrapper<WorkflowApprove>()
                .eq(WorkflowApprove::getInstanceId, id));
        int runtimeDel = wfNodeRuntimeService.remove(new LambdaQueryWrapper<WfNodeRuntime>()
                .eq(WfNodeRuntime::getInstanceId, id)) ? 1 : 0;
        int instDel = baseMapper.deleteById(id);
        log.warn("[Workflow] 实例已手动删除: id={}, status={}, operatorId={}, approveDel={}, runtimeDel={}",
                id, status, operatorId, approveDel, runtimeDel);
        return instDel > 0;
    }

    // ====================================================================
    // 排他网关 SKIPPED 分支记录辅助方法（修复 #10：网关未选中分支应显示 SKIPPED）
    // ====================================================================

    /**
     * 排他网关分流后，把被跳过的分支节点（及其后续整条不可达链路）记录为 SKIPPED runtime。
     * 只对 EXCLUSIVE 网关生效，PARALLEL 网关所有分支都走，没有跳过的概念。
     * <p>recordSkippedRuntime 写 runtime 时会先查一次已有 runtime，避免重复写入。
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
        Set<Long> existingIds = existing.stream().map(WfNodeRuntime::getNodeId).collect(Collectors.toSet());

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

    /** 解析节点 nextNodeIds 字符串 → List<Long> */
    private List<Long> parseNextIds(String nextNodeIds) {
        List<Long> result = new ArrayList<>();
        if (nextNodeIds == null || nextNodeIds.isEmpty()) return result;
        for (String s : nextNodeIds.split(",")) {
            try { result.add(Long.parseLong(s.trim())); } catch (NumberFormatException ignore) {}
        }
        return result;
    }
}