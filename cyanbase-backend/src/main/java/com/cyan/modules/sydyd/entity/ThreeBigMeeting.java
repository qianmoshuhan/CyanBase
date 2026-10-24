package com.cyan.modules.sydyd.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.cyan.entity.BaseEntity;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 三重一大会议主表
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_three_big_meeting")
public class ThreeBigMeeting extends BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 租户ID */
    @TableField("tenant_id")
    private Long tenantId;

    /** 会议名称 */
    @TableField("meeting_name")
    private String meetingName;

    /** 会议类型 1党委会 2总经理办公会 3董事会 */
    @TableField("meeting_type")
    private Integer meetingType;

    /** 开会时间 */
    @TableField("meeting_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime meetingTime;

    /** 主持人 */
    private String host;

    /** 参会人员（逗号分隔） */
    private String attendees;

    /** 列席人员（逗号分隔） */
    private String observers;

    /** 会议地点 */
    @TableField("meeting_place")
    private String meetingPlace;

    /** 会议纪要附件 */
    @TableField("minutes_file")
    private String minutesFile;

    /** 会议状态 1待召开 2进行中 3已结束 4已归档 */
    @TableField("meeting_status")
    private Integer meetingStatus;

    /** 关联工作流实例ID */
    @TableField("workflow_instance_id")
    private Long workflowInstanceId;

    /** 删除标志 */
    @TableLogic
    private Integer delFlag;
}