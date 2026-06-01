package com.cyan.modules.workflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 流程实例实体
 */
@Data
@TableName("wf_process_instance")
public class WorkflowInstance implements Serializable {

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
     * 流程标识
     */
    private String processKey;

    /**
     * 流程名称
     */
    private String processName;

    /**
     * 业务关联键
     */
    private String businessKey;

    /**
     * 当前节点ID
     */
    private Long currentNodeId;

    /**
     * 当前节点名称
     */
    private String currentNodeName;

    /**
     * 状态：running-运行中 completed-已完成 cancelled-已取消 rejected-已驳回
     */
    private String status;

    /**
     * 发起人ID
     */
    private Long initiatorId;

    /**
     * 发起人姓名
     */
    private String initiatorName;

    /**
     * 开始时间
     */
    private LocalDateTime startTime;

    /**
     * 结束时间
     */
    private LocalDateTime endTime;

    /**
     * 耗时（毫秒）
     */
    private Long durationMs;

    /**
     * 流程变量（JSON格式）
     */
    private String variables;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}