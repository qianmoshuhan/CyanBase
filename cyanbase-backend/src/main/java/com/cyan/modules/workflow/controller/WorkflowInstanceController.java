package com.cyan.modules.workflow.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cyan.common.R;
import com.cyan.common.log.annotation.Log;
import com.cyan.common.log.enums.BusinessType;
import com.cyan.security.annotation.RequiresPermission;
import com.cyan.common.security.util.SecurityUtils;
import com.cyan.modules.workflow.entity.*;
import com.cyan.modules.workflow.service.*;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 流程实例Controller
 */
@RestController
@RequestMapping("/workflow/instance")
@RequiredArgsConstructor
public class WorkflowInstanceController {

    private final WorkflowInstanceService workflowInstanceService;
    private final WorkflowNodeService workflowNodeService;
    private final WfNodeRuntimeService wfNodeRuntimeService;
    private final WorkflowApproveService workflowApproveService;

    /**
     * 发起流程
     */
    @PostMapping("/start")
    @Log(title = "流程实例", businessType = BusinessType.INSERT)
    @RequiresPermission("workflow:start")
    public R<Long> start(@RequestBody StartProcessRequest request) {
        Long instanceId = workflowInstanceService.startProcess(
                request.getDefinitionId(), 
                request.getBusinessKey(), 
                request.getVariables());
        return R.ok(instanceId);
    }

    /**
     * 分页查询我发起的流程
     */
    @GetMapping("/my-initiated")
    public R<Page<WorkflowInstance>> myInitiatedList(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {
        Long userId = SecurityUtils.getUserId();
        Page<WorkflowInstance> page = workflowInstanceService
                .queryMyInitiatedList(pageNum, pageSize, userId, status, keyword, startTime, endTime);
        return R.ok(page);
    }

    /**
     * 分页查询我的待办流程
     */
    @GetMapping("/my-todo")
    public R<Page<WorkflowInstance>> myTodoList(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        Long userId = SecurityUtils.getUserId();
        Page<WorkflowInstance> page = workflowInstanceService.queryMyTodoList(pageNum, pageSize, userId);
        return R.ok(page);
    }

    /**
     * 分页查询我已办流程
     */
    @GetMapping("/my-done")
    public R<Page<WorkflowInstance>> myDoneList(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        Long userId = SecurityUtils.getUserId();
        Page<WorkflowInstance> page = workflowInstanceService.queryMyDoneList(pageNum, pageSize, userId);
        return R.ok(page);
    }

    /**
     * 获取流程实例详情
     */
    @GetMapping("/{id}")
    public R<WorkflowInstance> getInfo(@PathVariable Long id) {
        return R.ok(workflowInstanceService.getById(id));
    }

    /**
     * 取消流程
     */
    @PutMapping("/cancel/{id}")
    @Log(title = "流程实例", businessType = BusinessType.UPDATE)
    @RequiresPermission("workflow:start")
    public R<Void> cancel(@PathVariable Long id) {
        Long userId = SecurityUtils.getUserId();
        return workflowInstanceService.cancelProcess(id, userId) ? R.ok() : R.fail("取消失败");
    }

    /**
     * ⭐ 手动删除已撤销/已驳回的流程实例
     * 审计约束：running / completed 不可删除
     * 权限约束：只能删自己发起的
     */
    @DeleteMapping("/{id}")
    @Log(title = "流程实例", businessType = BusinessType.DELETE)
    @RequiresPermission("workflow:start")
    public R<Void> deleteInstance(@PathVariable Long id) {
        Long userId = SecurityUtils.getUserId();
        return workflowInstanceService.deleteInstanceById(id, userId) ? R.ok() : R.fail("删除失败");
    }

    /**
     * ⭐ Phase 1：获取实例轨迹（给前端算 BPMN 高亮用）
     * 返回：已走节点keys / 当前节点keys / 已驳回节点keys / 未来可达节点keys / 完整审批轨迹日志
     */
    @GetMapping("/{id}/trail")
    public R<Map<String, Object>> getTrail(@PathVariable Long id) {
        WorkflowInstance instance = workflowInstanceService.getById(id);
        if (instance == null) return R.fail("流程实例不存在");

        Map<String, Object> result = new java.util.HashMap<>();
        result.put("instanceId", instance.getId());
        result.put("processName", instance.getProcessName());
        result.put("status", instance.getStatus());
        result.put("currentNodeName", instance.getCurrentNodeName());

        // 1. 从 runtime 表聚合节点状态
        List<WfNodeRuntime> runtimes = wfNodeRuntimeService.listByInstanceId(id);
        Set<String> completed = new java.util.LinkedHashSet<>();
        Set<String> current = new java.util.LinkedHashSet<>();
        Set<String> rejected = new java.util.LinkedHashSet<>();
        for (WfNodeRuntime rt : runtimes) {
            String nodeKey = "NodeKey_" + rt.getNodeId();
            if ("APPROVED".equals(rt.getStatus())) completed.add(nodeKey);
            else if ("WAITING".equals(rt.getStatus())) current.add(nodeKey);
            else if ("REJECTED".equals(rt.getStatus())) rejected.add(nodeKey);
        }

        // 2. 如果 runtime 里没有 start 节点（不会被 runtime 记录，runtime 只从第一个 userTask 开始），补 start
        List<WorkflowNode> allNodes = workflowNodeService.listByDefinitionId(instance.getDefinitionId());
        for (WorkflowNode n : allNodes) {
            if ("start".equals(n.getNodeType())) completed.add("NodeKey_" + n.getId());
            // end 节点如果实例 completed 了也算
            if ("end".equals(n.getNodeType()) && "completed".equals(instance.getStatus())) {
                completed.add("NodeKey_" + n.getId());
            }
        }

        result.put("completedNodeKeys", completed);
        result.put("currentNodeKeys", current);
        result.put("rejectedNodeKeys", rejected);

        // 3. ⭐ 完整节点状态序列（前端渲染已走+待审批完整轨迹）
        allNodes.sort((a, b) -> {
            Integer so = a.getSortOrder(); Integer to = b.getSortOrder();
            if (so == null && to == null) return 0;
            if (so == null) return 1;
            if (to == null) return -1;
            return so.compareTo(to);
        });
        Set<Long> runtimeNodeIds = runtimes.stream().map(WfNodeRuntime::getNodeId).collect(java.util.stream.Collectors.toSet());
        Map<Long, String> runtimeStatusMap = new java.util.HashMap<>();
        for (WfNodeRuntime rt : runtimes) runtimeStatusMap.put(rt.getNodeId(), rt.getStatus());
        List<Map<String, Object>> nodeStatusList = new java.util.ArrayList<>();
        for (WorkflowNode n : allNodes) {
            Map<String, Object> item = new java.util.LinkedHashMap<>();
            item.put("nodeId", n.getId());
            item.put("nodeName", n.getNodeName());
            item.put("nodeType", n.getNodeType());
            item.put("sortOrder", n.getSortOrder());
            String inferred;
            if ("start".equals(n.getNodeType())) {
                inferred = "COMPLETED";
            } else if ("end".equals(n.getNodeType())) {
                inferred = "completed".equals(instance.getStatus()) ? "COMPLETED" : "PENDING";
            } else if (runtimeNodeIds.contains(n.getId())) {
                String rs = runtimeStatusMap.get(n.getId());
                if ("APPROVED".equals(rs)) inferred = "COMPLETED";
                else if ("REJECTED".equals(rs)) inferred = "REJECTED";
                else if ("WAITING".equals(rs)) inferred = "CURRENT";
                else if ("SKIPPED".equals(rs)) inferred = "SKIPPED";
                else inferred = "COMPLETED";
            } else {
                // gateway 特殊处理：网关本身不写 runtime，但如果它的出边节点
                // 已有任一在 runtime 里（说明网关已做分支决策）→ COMPLETED
                if ("gateway".equals(n.getNodeType())) {
                    boolean gatewayProcessed = false;
                    String nis = n.getNextNodeIds();
                    if (nis != null && !nis.isEmpty()) {
                        for (String s : nis.split(",")) {
                            try {
                                Long nid = Long.parseLong(s.trim());
                                if (runtimeNodeIds.contains(nid)) {
                                    gatewayProcessed = true;
                                    break;
                                }
                            } catch (NumberFormatException ignore) {}
                        }
                    }
                    inferred = gatewayProcessed ? "COMPLETED" : "PENDING";
                } else {
                    inferred = "PENDING";
                }
            }
            item.put("status", inferred);
            nodeStatusList.add(item);
        }
        result.put("nodeStatusList", nodeStatusList);

        // 4. 审批日志
        Page<WorkflowApprove> approvePage = workflowApproveService.queryApproveList(1, 500, id);
        result.put("approveRecords", approvePage.getRecords());

        return R.ok(result);
    }

    /**
     * 发起流程请求对象
     */
    @Data
    public static class StartProcessRequest {
        private Long definitionId;
        private String businessKey;
        private String variables;
    }
}