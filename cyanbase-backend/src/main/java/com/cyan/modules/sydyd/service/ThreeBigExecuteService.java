package com.cyan.modules.sydyd.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.cyan.modules.sydyd.entity.ThreeBigExecute;

/**
 * 三重一大实施 Service
 * <p>
 * 核心入口：
 * 1. MeetingService.batchVote 表决通过时 → autoGenerateFromVote 自动生成实施记录
 * 2. 也支持手动新增（给历史表决结果补充实施任务）
 * </p>
 */
public interface ThreeBigExecuteService extends IService<ThreeBigExecute> {

    /**
     * 从会议表决结果自动生成实施任务
     * <p>
     * 业务规则：
     * - 只有 voteResult=1（通过）才生成
     * - 一个 meetingTopicId 只生成一条（数据库唯一约束兜底：meeting_topic_id 上已有 execute 就跳过）
     * </p>
     *
     * @param meetingTopicId 会议-议题中间表ID
     */
    void autoGenerateFromVote(Long meetingTopicId);

    /**
     * 手动新增实施任务（前端填写执行部门/责任人/计划完成时间等）
     * <p>
     * 前置校验：meetingTopicId 必须存在且 voteResult=1（已通过）
     */
    Long createExecute(ThreeBigExecute execute);

    /**
     * 更新执行进度（核心操作：填报进度百分比 + 进展详情）
     */
    void updateProgress(Long id, Integer progress, String progressDetail);

    /**
     * 开始执行（状态 1→2）
     */
    void start(Long id);

    /**
     * 执行完成（状态 2→3，自动记录 actualFinishTime + progress=100）
     */
    void finish(Long id, String progressDetail, String evidenceFile);

    /**
     * 归档（状态 3→5）
     */
    void archive(Long id);

    /** 分页查询 */
    IPage<ThreeBigExecute> queryPage(Integer pageNum, Integer pageSize,
                                     Integer executeStatus, Long meetingId,
                                     String itemName, String executeDept);
}
