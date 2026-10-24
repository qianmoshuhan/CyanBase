package com.cyan.modules.sydyd.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cyan.common.exception.BusinessException;
import com.cyan.modules.sydyd.entity.*;
import com.cyan.modules.sydyd.mapper.*;
import com.cyan.modules.sydyd.service.ThreeBigExecuteService;
import com.cyan.modules.sydyd.service.ThreeBigMeetingService;
import com.cyan.modules.workflow.entity.WorkflowDefinition;
import com.cyan.modules.workflow.service.WorkflowDefinitionService;
import com.cyan.modules.workflow.service.WorkflowInstanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 三重一大会议 Service 实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ThreeBigMeetingServiceImpl
        extends ServiceImpl<ThreeBigMeetingMapper, ThreeBigMeeting>
        implements ThreeBigMeetingService {

    private final ThreeBigMeetingTopicMapper meetingTopicMapper;
    private final ThreeBigTopicMapper topicMapper;
    private final ThreeBigItemMapper itemMapper;
    private final ThreeBigExecuteService executeService;
    private final WorkflowInstanceService workflowInstanceService;
    private final WorkflowDefinitionService workflowDefinitionService;

    /** 固定 processKey：三重一大会议审批 */
    private static final String PROCESS_KEY_MEETING = "SYDYD_MEETING_APPROVAL";

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createMeeting(ThreeBigMeeting meeting) {
        meeting.setMeetingStatus(1); // 待召开
        if (meeting.getMeetingType() == null) {
            meeting.setMeetingType(1);
        }
        save(meeting);

        // 自动发起 workflow（会议审批）
        try {
            Long definitionId = resolvePublishedDefinitionId(PROCESS_KEY_MEETING);
            if (definitionId != null) {
                String businessKey = "SYDYD_MEETING_" + meeting.getId();
                String variables = "{\"meetingId\":" + meeting.getId() + ",\"meetingName\":\"" + meeting.getMeetingName() + "\",\"meetingType\":" + meeting.getMeetingType() + "}";
                Long instanceId = workflowInstanceService.startProcess(definitionId, businessKey, variables);
                meeting.setWorkflowInstanceId(instanceId);
                updateById(meeting);
                log.info("[三重一大] 新建会议 同步发起workflow meetingId={}, workflowInstanceId={}", meeting.getId(), instanceId);
            } else {
                log.warn("[三重一大] 未找到已发布的流程定义 processKey={}，跳过workflow发起 meetingId={}", PROCESS_KEY_MEETING, meeting.getId());
            }
        } catch (Exception e) {
            log.warn("[三重一大] workflow发起失败，不影响业务 meetingId={}, err={}", meeting.getId(), e.getMessage());
        }

        log.info("[三重一大] 新建会议 meetingId={}, tenantId={}", meeting.getId(), meeting.getTenantId());
        return meeting.getId();
    }

    /** 按 processKey 查已发布的流程定义，找不到返回 null */
    private Long resolvePublishedDefinitionId(String processKey) {
        WorkflowDefinition def = workflowDefinitionService.getOne(
                new LambdaQueryWrapper<WorkflowDefinition>()
                        .eq(WorkflowDefinition::getProcessKey, processKey)
                        .eq(WorkflowDefinition::getStatus, 1)
                        .last("LIMIT 1"));
        return def != null ? def.getId() : null;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addTopicsToMeeting(Long meetingId, List<Long> topicIds) {
        if (topicIds == null || topicIds.isEmpty()) {
            throw new BusinessException("请至少选择一个议题");
        }

        // 1. 会议必须存在 + 未结束
        ThreeBigMeeting meeting = getById(meetingId);
        if (meeting == null) throw new BusinessException("会议不存在");
        Integer ms = meeting.getMeetingStatus();
        if (ms != null && ms >= 3) {
            throw new BusinessException("会议已结束/已归档，不能再添加议题（当前状态=" + ms + "）");
        }

        // 2. 找出会议里已有的议题ID，避免重复加入
        List<Long> existingTopicIds = meetingTopicMapper.selectList(
                new LambdaQueryWrapper<ThreeBigMeetingTopic>()
                        .eq(ThreeBigMeetingTopic::getMeetingId, meetingId)
                        .select(ThreeBigMeetingTopic::getTopicId)
        ).stream().map(ThreeBigMeetingTopic::getTopicId).toList();

        // 3. 计算已有最大排序号，新加入的往后排
        Integer maxOrder = meetingTopicMapper.selectList(
                new LambdaQueryWrapper<ThreeBigMeetingTopic>()
                        .eq(ThreeBigMeetingTopic::getMeetingId, meetingId)
                        .select(ThreeBigMeetingTopic::getTopicOrder)
                        .orderByDesc(ThreeBigMeetingTopic::getTopicOrder)
                        .last("LIMIT 1")
        ).stream().findFirst().map(ThreeBigMeetingTopic::getTopicOrder).orElse(0);

        int order = maxOrder;
        List<String> skipReasons = new ArrayList<>();

        for (Long topicId : topicIds) {
            // 已在会议里 → 跳过
            if (existingTopicIds.contains(topicId)) {
                skipReasons.add("topicId=" + topicId + ": 已在会议中");
                continue;
            }

            // 议题必须存在 + 前置把关通过（status=2）
            ThreeBigTopic topic = topicMapper.selectById(topicId);
            if (topic == null) {
                skipReasons.add("topicId=" + topicId + ": 议题不存在");
                continue;
            }
            if (topic.getTopicStatus() == null || topic.getTopicStatus() != 2) {
                skipReasons.add("topicId=" + topicId + ": 前置把关未通过（status=" + topic.getTopicStatus() + "）");
                continue;
            }
            // 议题已在其他会议里（已上会）→ 不允许
            long inOtherMeeting = meetingTopicMapper.selectCount(new LambdaQueryWrapper<ThreeBigMeetingTopic>()
                    .eq(ThreeBigMeetingTopic::getTopicId, topicId)
                    .ne(ThreeBigMeetingTopic::getMeetingId, meetingId));
            if (inOtherMeeting > 0) {
                skipReasons.add("topicId=" + topicId + ": 已在其他会议中");
                continue;
            }

            // 4. 加入会议
            ThreeBigMeetingTopic m2t = new ThreeBigMeetingTopic();
            m2t.setMeetingId(meetingId);
            m2t.setTopicId(topicId);
            m2t.setItemId(topic.getItemId());
            m2t.setTopicTitle(topic.getTopicTitle());
            m2t.setTopicOrder(++order);
            meetingTopicMapper.insert(m2t);

            // 5. 更新议题状态 → 已上会
            topic.setTopicStatus(3);
            topicMapper.updateById(topic);

            // 6. 更新事项状态 → 已上会
            ThreeBigItem item = itemMapper.selectById(topic.getItemId());
            if (item != null) {
                item.setItemStatus(4);
                itemMapper.updateById(item);
            }
        }

        if (!skipReasons.isEmpty()) {
            log.warn("[三重一大] addTopicsToMeeting 部分跳过: {}", skipReasons);
        }
        log.info("[三重一大] 议题加入会议 meetingId={}, 成功={}, 跳过={}",
                meetingId, topicIds.size() - skipReasons.size(), skipReasons.size());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchVote(Long meetingId, List<ThreeBigMeetingTopic> votes) {
        if (votes == null || votes.isEmpty()) {
            throw new BusinessException("请至少录入一条表决结果");
        }

        // 1. 会议必须存在 + 进行中/已结束 才能表决（status=2 或 3）
        ThreeBigMeeting meeting = getById(meetingId);
        if (meeting == null) throw new BusinessException("会议不存在");
        Integer ms = meeting.getMeetingStatus();
        if (ms != null && ms != 2 && ms != 3) {
            throw new BusinessException("会议未进入讨论阶段（当前状态=" + ms + "），仅进行中/已结束可表决");
        }

        for (ThreeBigMeetingTopic vote : votes) {
            // 必须在本会议下
            ThreeBigMeetingTopic m2t = meetingTopicMapper.selectById(vote.getId());
            if (m2t == null || !m2t.getMeetingId().equals(meetingId)) {
                continue;
            }
            if (vote.getVoteResult() == null) {
                continue; // 没填结果跳过
            }

            // 写表决
            m2t.setVoteResult(vote.getVoteResult());
            m2t.setVoteDetail(vote.getVoteDetail());
            meetingTopicMapper.updateById(m2t);

            // 级联更新议题 + 事项状态
            ThreeBigTopic topic = topicMapper.selectById(m2t.getTopicId());
            ThreeBigItem item = m2t.getItemId() != null ? itemMapper.selectById(m2t.getItemId()) : null;

            switch (vote.getVoteResult()) {
                case 1 -> { // 通过
                    if (topic != null) topic.setTopicStatus(4);
                    if (item != null) item.setItemStatus(5); // 已办结
                    // 阶段 3：表决通过后自动生成实施任务
                    executeService.autoGenerateFromVote(m2t.getId());
                }
                case 2 -> { // 暂缓
                    if (topic != null) topic.setTopicStatus(5);
                    // 事项不动，维持已上会
                }
                case 3 -> { // 否决
                    if (topic != null) topic.setTopicStatus(6);
                    if (item != null) item.setItemStatus(6); // 已驳回
                }
                default -> log.warn("未知 voteResult={}，跳过级联更新", vote.getVoteResult());
            }
            if (topic != null) topicMapper.updateById(topic);
            if (item != null) itemMapper.updateById(item);
        }
        log.info("[三重一大] 会议表决 meetingId={}, votes={}", meetingId, votes.size());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void start(Long meetingId) {
        ThreeBigMeeting m = getById(meetingId);
        if (m == null) throw new BusinessException("会议不存在");
        if (m.getMeetingStatus() != null && m.getMeetingStatus() != 1) {
            throw new BusinessException("当前状态（" + m.getMeetingStatus() + "）不允许召开");
        }
        m.setMeetingStatus(2);
        updateById(m);
        log.info("[三重一大] 会议开始 meetingId={}", meetingId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void finish(Long meetingId) {
        ThreeBigMeeting m = getById(meetingId);
        if (m == null) throw new BusinessException("会议不存在");
        if (m.getMeetingStatus() != null && m.getMeetingStatus() != 2) {
            throw new BusinessException("会议未在进行中（当前状态=" + m.getMeetingStatus() + "）");
        }
        m.setMeetingStatus(3);
        updateById(m);
        log.info("[三重一大] 会议结束 meetingId={}", meetingId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void archive(Long meetingId) {
        ThreeBigMeeting m = getById(meetingId);
        if (m == null) throw new BusinessException("会议不存在");
        if (m.getMeetingStatus() != null && m.getMeetingStatus() != 3) {
            throw new BusinessException("只有已结束的会议能归档（当前状态=" + m.getMeetingStatus() + "）");
        }
        m.setMeetingStatus(4);
        updateById(m);
        log.info("[三重一大] 会议归档 meetingId={}", meetingId);
    }

    @Override
    public List<ThreeBigMeetingTopic> listMeetingTopics(Long meetingId) {
        return meetingTopicMapper.selectList(new LambdaQueryWrapper<ThreeBigMeetingTopic>()
                .eq(ThreeBigMeetingTopic::getMeetingId, meetingId)
                .orderByAsc(ThreeBigMeetingTopic::getTopicOrder));
    }

    @Override
    public IPage<ThreeBigMeeting> queryPage(Integer pageNum, Integer pageSize,
                                            Integer meetingType, Integer meetingStatus, String meetingName) {
        LambdaQueryWrapper<ThreeBigMeeting> wrapper = new LambdaQueryWrapper<>();
        if (meetingType != null)   wrapper.eq(ThreeBigMeeting::getMeetingType, meetingType);
        if (meetingStatus != null) wrapper.eq(ThreeBigMeeting::getMeetingStatus, meetingStatus);
        if (meetingName != null && !meetingName.isEmpty()) {
            wrapper.like(ThreeBigMeeting::getMeetingName, meetingName);
        }
        wrapper.orderByDesc(ThreeBigMeeting::getMeetingTime);
        Page<ThreeBigMeeting> page = new Page<>(pageNum, pageSize);
        return page(page, wrapper);
    }
}