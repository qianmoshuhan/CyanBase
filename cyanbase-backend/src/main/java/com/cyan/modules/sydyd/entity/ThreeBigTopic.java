package com.cyan.modules.sydyd.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.cyan.entity.BaseEntity;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 三重一大议题表
 * <p>由业务事项（待酝酿/已生成议题状态）一键生成，进入前置把关流程。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_three_big_topic")
public class ThreeBigTopic extends BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 租户ID */
    @TableField("tenant_id")
    private Long tenantId;

    /** 关联业务事项ID */
    @TableField("item_id")
    private Long itemId;

    /** 议题标题 */
    @TableField("topic_title")
    private String topicTitle;

    /** 冗余事项名称 */
    @TableField("item_name")
    private String itemName;

    /** 冗余事项类型 */
    @TableField("item_type")
    private Integer itemType;

    /** 前置研究意见 */
    @TableField("pre_research_opinion")
    private String preResearchOpinion;

    /** 是否完成前置把关 0未完成 1已完成 */
    @TableField("pre_check_completed")
    private Integer preCheckCompleted;

    /** 前置把关人 */
    @TableField("pre_check_user")
    private String preCheckUser;

    /** 前置把关时间 */
    @TableField("pre_check_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime preCheckTime;

    /** 预备上会时间 */
    @TableField("proposed_meeting_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime proposedMeetingTime;

    /** 议题状态 1待前置把关 2前置把关通过 3已上会 4已表决 5已暂缓 6已否决 */
    @TableField("topic_status")
    private Integer topicStatus;

    /** 删除标志 */
    @TableLogic
    private Integer delFlag;
}