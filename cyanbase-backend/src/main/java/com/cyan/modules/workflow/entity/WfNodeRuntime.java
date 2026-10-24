package com.cyan.modules.workflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.cyan.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 流程节点运行时状态
 * <p>会签/或签/加签的核心运行态记录。每个节点流转到 userTask 时新建一条，记录总审批人数、已签数、驳回数等。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wf_node_runtime")
public class WfNodeRuntime extends BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("instance_id")
    private Long instanceId;

    @TableField("node_id")
    private Long nodeId;

    @TableField("node_key")
    private String nodeKey;

    @TableField("node_name")
    private String nodeName;

    /** OR=或签 AND=会签 */
    @TableField("approval_type")
    private String approvalType;

    /** user/role/dept/initiator */
    @TableField("assignee_type")
    private String assigneeType;

    @TableField("total_count")
    private Integer totalCount;

    @TableField("approved_count")
    private Integer approvedCount;

    @TableField("rejected_count")
    private Integer rejectedCount;

    /** 动态加签人ID列表(逗号串) */
    @TableField("add_signers")
    private String addSigners;

    /** WAITING/APPROVED/REJECTED/SKIPPED */
    private String status;

    @TableField("deadline")
    private LocalDateTime deadline;

    @TableField("start_time")
    private LocalDateTime startTime;

    @TableField("end_time")
    private LocalDateTime endTime;

    @TableField("tenant_id")
    private Long tenantId;

    @TableLogic
    private Integer delFlag;
}
