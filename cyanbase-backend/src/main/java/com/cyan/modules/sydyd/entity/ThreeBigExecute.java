package com.cyan.modules.sydyd.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.cyan.entity.BaseEntity;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 三重一大实施落地表
 * <p>
 * 由会议表决通过（voteResult=1）后自动生成，跟踪执行进度，形成闭环台账。
 * 也支持给历史表决结果手动补充实施任务。
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_three_big_execute")
public class ThreeBigExecute extends BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 租户ID */
    @TableField("tenant_id")
    private Long tenantId;

    /** 会议-议题中间表ID */
    @TableField("meeting_topic_id")
    private Long meetingTopicId;

    /** 冗余会议ID */
    @TableField("meeting_id")
    private Long meetingId;

    /** 冗余议题ID */
    @TableField("topic_id")
    private Long topicId;

    /** 冗余业务事项ID */
    @TableField("item_id")
    private Long itemId;

    /** 冗余事项名称 */
    @TableField("item_name")
    private String itemName;

    /** 执行部门 */
    @TableField("execute_dept")
    private String executeDept;

    /** 责任人 */
    private String responsible;

    /** 计划完成时间 */
    @TableField("plan_finish_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime planFinishTime;

    /** 实施状态 1待执行 2执行中 3已完成 4已逾期 5已归档 */
    @TableField("execute_status")
    private Integer executeStatus;

    /** 执行进度百分比 0~100 */
    private Integer progress;

    /** 执行进展详情 */
    @TableField("progress_detail")
    private String progressDetail;

    /** 实际完成时间 */
    @TableField("actual_finish_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime actualFinishTime;

    /** 佐证材料附件 */
    @TableField("evidence_file")
    private String evidenceFile;

    /** 冗余会议名称 */
    @TableField("meeting_name")
    private String meetingName;

    /** 冗余表决结果 */
    @TableField("vote_result")
    private Integer voteResult;

    /** 关联工作流实例ID */
    @TableField("workflow_instance_id")
    private Long workflowInstanceId;

    /** 删除标志 */
    @TableLogic
    private Integer delFlag;
}