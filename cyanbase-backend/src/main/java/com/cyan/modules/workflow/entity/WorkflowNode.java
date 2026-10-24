package com.cyan.modules.workflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.cyan.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * 流程节点实体
 * <p>nodeType: start / end / userTask / serviceTask / gateway</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wf_process_node")
public class WorkflowNode extends BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("tenant_id")
    private Long tenantId;

    @TableField("definition_id")
    private Long definitionId;

    @TableField("node_key")
    private String nodeKey;

    @TableField("node_name")
    private String nodeName;

    /** start/end/userTask/serviceTask/gateway */
    @TableField("node_type")
    private String nodeType;

    /** user/role/dept */
    @TableField("assignee_type")
    private String assigneeType;

    @TableField("assignee_value")
    private String assigneeValue;

    /** or/and */
    @TableField("approval_type")
    private String approvalType;

    @TableField("timeout_hours")
    private Integer timeoutHours;

    @TableField("next_node_ids")
    private String nextNodeIds;

    /**
     * 排他网关分支条件表达式（SpEL）。
     * <p>
     * ⭐ 关键：条件<strong>配在网关出边指向的「下一个节点」上</strong>，不是配在网关自身！
     * 引擎解析网关时会遍历 nextNodeIds，对每个目标节点的 conditionExpr 求值。
     * 留空 = 默认分支（所有有条件的分支都不满足时走这条）。
     * </p>
     *
     * <h4>可用变量（SpEL 上下文）</h4>
     * <pre>
     *   业务变量：instance.variables JSON Map 里的所有 key
     *           例：submit 时传 {"amount": 50000, "dept": "finance"}
     *           → 可直接写 amount / dept
     *   注入变量：initiatorId（发起人ID）、processKey（流程标识）
     * </pre>
     *
     * <h4>写法示例</h4>
     * <pre>
     *   amount > 10000
     *   #variables.amount > 10000
     *   dept == 'finance'
     *   initiatorId == 10086
     *   amount > 10000 and dept != 'hr'
     * </pre>
     */
    @TableField("condition_expr")
    private String conditionExpr;

    /**
     * 网关类型：exclusive=排他网关（条件分支，只走一条） / parallel=并行网关（所有分支都走）。
     * <p>并行网关下 conditionExpr 全部忽略，所有 nextNodeIds 指向的节点都会被激活。</p>
     */
    @TableField("gateway_type")
    private String gatewayType;

    @TableField("sort_order")
    private Integer sortOrder;

    @TableLogic
    private Integer delFlag;
}