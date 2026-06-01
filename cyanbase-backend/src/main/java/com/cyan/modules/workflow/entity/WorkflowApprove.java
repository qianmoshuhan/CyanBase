package com.cyan.modules.workflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 流程审批记录实体
 */
@Data
@TableName("wf_process_approve")
public class WorkflowApprove implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 流程实例ID
     */
    private Long instanceId;

    /**
     * 节点ID
     */
    private Long nodeId;

    /**
     * 节点名称
     */
    private String nodeName;

    /**
     * 审批人ID
     */
    private Long approverId;

    /**
     * 审批人姓名
     */
    private String approverName;

    /**
     * 操作：approve-通过 reject-驳回 transfer-转办
     */
    private String action;

    /**
     * 审批意见
     */
    private String comment;

    /**
     * 审批时间
     */
    private LocalDateTime approvalTime;

    /**
     * 审批耗时（毫秒）
     */
    private Long durationMs;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}