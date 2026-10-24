package com.cyan.modules.sydyd.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.cyan.modules.sydyd.entity.ThreeBigTopic;

import java.util.List;

/**
 * 三重一大议题 Service
 * <p>
 * 核心链路：业务事项（状态=2 待酝酿） → 一键生成议题 → 事项状态自动改为 3（已生成议题）
 * </p>
 */
public interface ThreeBigTopicService extends IService<ThreeBigTopic> {

    /**
     * 从业务事项一键生成议题（核心入口）
     * <p>
     * 前置校验：
     * 1. 事项必须存在 + 属于当前租户
     * 2. 事项状态 = 2（待酝酿） —— 草稿不能直接生成议题
     * 3. 事项未被其他议题引用（防止重复生成）
     * </p>
     * 事务内：创建议题 + 更新事项状态 → 3
     *
     * @param itemId 业务事项ID
     * @return 新建的议题ID
     */
    Long generateFromItem(Long itemId);

    /** 批量生成（多个事项同时生成议题，常见场景） */
    List<Long> batchGenerateFromItems(List<Long> itemIds);

    /** 前置把关通过 */
    void preCheckPass(Long topicId, String opinion, String operator);

    /** 前置把关打回（回到待酝酿） */
    void preCheckReject(Long topicId, String opinion, String operator);

    /** 分页查询 */
    IPage<ThreeBigTopic> queryPage(Integer pageNum, Integer pageSize,
                                   Integer topicStatus, Integer itemType,
                                   String keyword, Boolean preCheckCompleted);
}