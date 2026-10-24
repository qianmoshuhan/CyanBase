package com.cyan.modules.sydyd.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.cyan.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * 三重一大会议-议题中间表
 * <p>一个议题在同一场会议里只能出现一次（数据库有唯一索引 uk_meeting_topic）。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_three_big_meeting_topic")
public class ThreeBigMeetingTopic extends BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 租户ID */
    @TableField("tenant_id")
    private Long tenantId;

    /** 会议ID */
    @TableField("meeting_id")
    private Long meetingId;

    /** 议题ID */
    @TableField("topic_id")
    private Long topicId;

    /** 冗余事项ID */
    @TableField("item_id")
    private Long itemId;

    /** 议题在会议中的排序 */
    @TableField("topic_order")
    private Integer topicOrder;

    /** 冗余议题标题 */
    @TableField("topic_title")
    private String topicTitle;

    /** 表决结果 1通过 2暂缓 3否决 */
    @TableField("vote_result")
    private Integer voteResult;

    /** 班子成员意见记录 */
    @TableField("vote_detail")
    private String voteDetail;

    /** 删除标志 */
    @TableLogic
    private Integer delFlag;
}