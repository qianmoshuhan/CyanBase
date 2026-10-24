package com.cyan.modules.workflow.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.cyan.modules.workflow.entity.WorkflowDefinition;
import com.cyan.modules.workflow.entity.WorkflowNode;

import java.util.List;

/**
 * 流程定义Service
 */
public interface WorkflowDefinitionService extends IService<WorkflowDefinition> {

    /**
     * 分页查询流程定义列表
     */
    Page<WorkflowDefinition> queryPageList(Integer pageNum, Integer pageSize, String processName, String category, Integer status);

    /**
     * 发布流程
     */
    boolean publishProcess(Long id);

    /**
     * 停用流程
     */
    boolean disableProcess(Long id);

    /**
     * 复制流程（生成新版本，连同节点一起复制）
     */
    Long copyProcess(Long id);

    /**
     * 事务性保存流程定义 + 节点
     * 节点先全删再重建，definition 自动根据 nodes 列表回填 startNodeId
     *
     * @param definition 流程定义（id 为空则新增，非空则更新）
     * @param nodes      节点列表（可为空表示只改 definition 本身）
     */
    Long saveDefinitionAndNodes(WorkflowDefinition definition, List<WorkflowNode> nodes);

    /**
     * 查询所有已发布的流程定义（供发起流程时选择）
     */
    List<WorkflowDefinition> listPublished(String businessType);

    /**
     * 校验 processKey 是否已存在（排除指定 id 自身）
     */
    boolean existsProcessKey(String processKey, Long excludeId);

    /**
     * ⭐ 统一审批人解析：根据节点配置算出实际审批用户 id 列表
     * <p>四种 assigneeType 的解析规则：</p>
     * <ul>
     *   <li>{@code user}      → assigneeValue 解析成用户 id（逗号串）</li>
     *   <li>{@code role}      → assigneeValue 是 roleKey 或 roleId，查拥有该角色的用户</li>
     *   <li>{@code dept}      → assigneeValue 是 deptId（逗号串），查该部门下所有用户</li>
     *   <li>{@code initiator} → 直接返回发起人自己的 id（assigneeValue 留空即可）</li>
     * </ul>
     * 兜底：解析失败返回发起人自己
     */
    java.util.List<Long> resolveAssigneeIds(WorkflowNode node, Long initiatorId);
}