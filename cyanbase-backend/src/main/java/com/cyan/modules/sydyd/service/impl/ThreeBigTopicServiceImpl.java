package com.cyan.modules.sydyd.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cyan.common.exception.BusinessException;
import com.cyan.modules.sydyd.entity.ThreeBigItem;
import com.cyan.modules.sydyd.entity.ThreeBigTopic;
import com.cyan.modules.sydyd.mapper.ThreeBigItemMapper;
import com.cyan.modules.sydyd.mapper.ThreeBigTopicMapper;
import com.cyan.modules.sydyd.service.ThreeBigTopicService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 三重一大议题 Service 实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ThreeBigTopicServiceImpl
        extends ServiceImpl<ThreeBigTopicMapper, ThreeBigTopic>
        implements ThreeBigTopicService {

    private final ThreeBigItemMapper itemMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long generateFromItem(Long itemId) {
        // 1. 业务事项必须存在
        ThreeBigItem item = itemMapper.selectById(itemId);
        if (item == null) throw new BusinessException("业务事项不存在（itemId=" + itemId + "）");

        // 2. 事项状态必须是"待酝酿"（status=2）或"已驳回"（status=6），其他状态不允许生成
        Integer s = item.getItemStatus();
        if (s != null && s != 2 && s != 6) {
            throw new BusinessException("当前事项状态（" + s + "）不允许生成议题，仅 待酝酿/已驳回 可生成");
        }

        // 3. 该事项不能已被其他议题引用
        long existing = count(new LambdaQueryWrapper<ThreeBigTopic>()
                .eq(ThreeBigTopic::getItemId, itemId));
        if (existing > 0) {
            throw new BusinessException("该事项已生成过议题，不能重复生成（itemId=" + itemId + "）");
        }

        // 4. 创建议题
        ThreeBigTopic topic = new ThreeBigTopic();
        topic.setItemId(itemId);
        topic.setTopicTitle(item.getItemName()); // 默认取事项名称
        topic.setItemName(item.getItemName());
        topic.setItemType(item.getItemType());
        topic.setTopicStatus(1); // 待前置把关
        topic.setPreCheckCompleted(0);
        save(topic);

        // 5. 更新事项状态为"已生成议题"
        item.setItemStatus(3);
        itemMapper.updateById(item);

        log.info("[三重一大] 议题生成 topicId={}, itemId={}, tenantId={}",
                topic.getId(), itemId, topic.getTenantId());
        return topic.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<Long> batchGenerateFromItems(List<Long> itemIds) {
        if (itemIds == null || itemIds.isEmpty()) {
            throw new BusinessException("请至少选择一个业务事项");
        }
        if (itemIds.size() > 50) {
            throw new BusinessException("单次最多批量生成 50 条议题");
        }
        List<Long> topicIds = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        for (Long itemId : itemIds) {
            try {
                topicIds.add(generateFromItem(itemId));
            } catch (BusinessException e) {
                // 单个失败不影响其他，但记录下来方便前端提示
                errors.add("itemId=" + itemId + ": " + e.getMessage());
            }
        }
        if (!errors.isEmpty()) {
            log.warn("[三重一大] 批量生成议题部分失败: {}", errors);
        }
        return topicIds;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void preCheckPass(Long topicId, String opinion, String operator) {
        ThreeBigTopic topic = getById(topicId);
        if (topic == null) throw new BusinessException("议题不存在");
        if (topic.getTopicStatus() != null && topic.getTopicStatus() != 1) {
            throw new BusinessException("当前议题状态（" + topic.getTopicStatus() + "）不允许前置把关");
        }
        topic.setTopicStatus(2); // 前置把关通过
        topic.setPreCheckCompleted(1);
        topic.setPreCheckUser(operator);
        topic.setPreCheckTime(LocalDateTime.now());
        if (opinion != null && !opinion.isEmpty()) {
            topic.setPreResearchOpinion(opinion);
        }
        updateById(topic);
        log.info("[三重一大] 议题前置把关通过 topicId={}, operator={}", topicId, operator);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void preCheckReject(Long topicId, String opinion, String operator) {
        ThreeBigTopic topic = getById(topicId);
        if (topic == null) throw new BusinessException("议题不存在");
        if (topic.getTopicStatus() != null && topic.getTopicStatus() != 1) {
            throw new BusinessException("当前议题状态（" + topic.getTopicStatus() + "）不允许打回");
        }
        topic.setTopicStatus(1); // 回到待前置把关
        if (opinion != null && !opinion.isEmpty()) {
            topic.setPreResearchOpinion(opinion);
        }
        updateById(topic);
        log.info("[三重一大] 议题前置把关打回 topicId={}, operator={}", topicId, operator);
    }

    @Override
    public IPage<ThreeBigTopic> queryPage(Integer pageNum, Integer pageSize,
                                          Integer topicStatus, Integer itemType,
                                          String keyword, Boolean preCheckCompleted) {
        LambdaQueryWrapper<ThreeBigTopic> wrapper = new LambdaQueryWrapper<>();
        if (topicStatus != null)         wrapper.eq(ThreeBigTopic::getTopicStatus, topicStatus);
        if (itemType != null)           wrapper.eq(ThreeBigTopic::getItemType, itemType);
        if (preCheckCompleted != null)  wrapper.eq(ThreeBigTopic::getPreCheckCompleted, preCheckCompleted ? 1 : 0);
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.and(w -> w.like(ThreeBigTopic::getTopicTitle, keyword)
                    .or().like(ThreeBigTopic::getItemName, keyword));
        }
        wrapper.orderByDesc(ThreeBigTopic::getCreateTime);
        Page<ThreeBigTopic> page = new Page<>(pageNum, pageSize);
        return page(page, wrapper);
    }
}