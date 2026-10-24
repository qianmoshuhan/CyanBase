package com.cyan.modules.sydyd.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.cyan.modules.sydyd.entity.ThreeBigMeeting;
import com.cyan.modules.sydyd.entity.ThreeBigMeetingTopic;

import java.util.List;

/**
 * 三重一大会议 Service
 * <p>
 * 核心链路：新建会议 → 批量加入议题 → 会议召开 → 表决 → 会议纪要
 * </p>
 */
public interface ThreeBigMeetingService extends IService<ThreeBigMeeting> {

    /** 新建会议 */
    Long createMeeting(ThreeBigMeeting meeting);

    /**
     * 批量把议题加入会议
     * <p>
     * 前置校验：
     * 1. 会议必须存在 + 未结束（status ≤ 2 进行中）
     * 2. 议题必须存在 + 前置把关通过（topicStatus = 2）
     * 3. 议题未在本次会议里（唯一索引 uk_meeting_topic 兜底）
     * </p>
     *
     * @param meetingId 会议ID
     * @param topicIds  议题ID列表
     */
    void addTopicsToMeeting(Long meetingId, List<Long> topicIds);

    /**
     * 批量表决
     * <p>
     * 会议必须已结束（status = 3），只能表决本会议下的议题。
     * 表决后：
     * - voteResult=1 通过 → 事项状态 → 4（已上会），议题状态 → 4（已表决）
     * - voteResult=2 暂缓 → 议题状态 → 5（暂缓）
     * - voteResult=3 否决 → 事项状态 → 6（已驳回），议题状态 → 6（否决）
     */
    void batchVote(Long meetingId, List<ThreeBigMeetingTopic> votes);

    /** 会议状态流转：召开 / 结束 / 归档 */
    void start(Long meetingId);
    void finish(Long meetingId);
    void archive(Long meetingId);

    /** 查询会议下的全部议题（含表决结果） */
    List<ThreeBigMeetingTopic> listMeetingTopics(Long meetingId);

    /** 分页查询会议 */
    IPage<ThreeBigMeeting> queryPage(Integer pageNum, Integer pageSize,
                                     Integer meetingType, Integer meetingStatus, String meetingName);
}