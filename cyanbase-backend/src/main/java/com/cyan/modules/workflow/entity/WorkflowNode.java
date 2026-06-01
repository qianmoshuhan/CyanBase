package com.cyan.modules.workflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 流程节点实体
 */
@Data
@TableName("wf_process_node")
public class WorkflowNode implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 流程定义ID
     */
    private Long definitionId;

    /**
     * 节点标识
     */
    private String nodeKey;

    /**
     * 节点名称
     */
    private String nodeName;

    /**
     * 节点类型：start-开始 end-结束 userTask-用户任务 serviceTask-服务任务 gateway-网关
     */
    private String nodeType;

    /**
     * 审批人类型：user-指定用户 role-指定角色 dept-指定部门
     */
    private String assigneeType;

    /**
     * 审批人值（用户ID/角色ID/部门ID，多个用逗号分隔）
     */
    private String assigneeValue;

    /**
     * 审批类型：or-或签 and-会签
     */
    private String approvalType;

    /**
     * 超时小时数
     */
    private Integer timeoutHours;

    /**
     * 下一节点ID列表（多个用逗号分隔）
     */
    private String nextNodeIds;

    /**
     * 排序号
     */
    private Integer sortOrder;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}