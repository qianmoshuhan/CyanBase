package com.cyan.modules.workflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.cyan.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 流程审批/待办记录表
 * <p>action: null=待办未处理 / approve=通过 / reject=驳回 / transfer=转办</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wf_process_approve")
public class WorkflowApprove extends BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("tenant_id")
    private Long tenantId;

    @TableField("instance_id")
    private Long instanceId;

    @TableField("node_id")
    private Long nodeId;

    @TableField("node_name")
    private String nodeName;

    @TableField("approver_id")
    private Long approverId;

    @TableField("approver_name")
    private String approverName;

    /** null=待办 / approve / reject / transfer / skip */
    private String action;

    /** 审批类型：NORMAL=正常审批 ADD=加签 */
    @TableField("sign_type")
    private String signType;

    /** 加签的来源审批人ID（从谁那里加出来的） */
    @TableField("parent_approve_id")
    private Long parentApproveId;

    /** 该条审批的截止时间（用于超时催办） */
    @TableField("deadline")
    private LocalDateTime deadline;

    private String comment;

    @TableField("approval_time")
    private LocalDateTime approvalTime;

    @TableField("duration_ms")
    private Long durationMs;

    @TableLogic
    private Integer delFlag;
}