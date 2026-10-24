package com.cyan.modules.workflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.cyan.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 流程实例实体
 * <p>status: running/completed/rejected/cancelled</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wf_process_instance")
public class WorkflowInstance extends BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("tenant_id")
    private Long tenantId;

    @TableField("definition_id")
    private Long definitionId;

    @TableField("process_key")
    private String processKey;

    @TableField("process_name")
    private String processName;

    @TableField("business_key")
    private String businessKey;

    @TableField("current_node_id")
    private Long currentNodeId;

    @TableField("current_node_name")
    private String currentNodeName;

    /** 当前节点审批截止时间（用于超时催办） */
    @TableField("current_node_deadline")
    private LocalDateTime currentNodeDeadline;

    /** running / completed / rejected / cancelled */
    private String status;

    @TableField("initiator_id")
    private Long initiatorId;

    @TableField("initiator_name")
    private String initiatorName;

    @TableField("start_time")
    private LocalDateTime startTime;

    @TableField("end_time")
    private LocalDateTime endTime;

    @TableField("duration_ms")
    private Long durationMs;

    private String variables;

    /**
     * 业务事项名称（非数据库字段，查询列表时根据 businessKey 关联业务表回填）
     * 例：businessKey=threeBigItem:123 → 查 t_three_big_item.item_name
     */
    @TableField(exist = false)
    private String itemName;

    @TableLogic
    private Integer delFlag;
}