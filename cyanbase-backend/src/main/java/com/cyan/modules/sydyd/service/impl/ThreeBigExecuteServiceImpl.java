package com.cyan.modules.sydyd.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cyan.common.exception.BusinessException;
import com.cyan.modules.sydyd.entity.*;
import com.cyan.modules.sydyd.mapper.*;
import com.cyan.modules.sydyd.service.ThreeBigExecuteService;
import com.cyan.modules.workflow.entity.WorkflowDefinition;
import com.cyan.modules.workflow.service.WorkflowDefinitionService;
import com.cyan.modules.workflow.service.WorkflowInstanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 三重一大实施 Service 实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ThreeBigExecuteServiceImpl
        extends ServiceImpl<ThreeBigExecuteMapper, ThreeBigExecute>
        implements ThreeBigExecuteService {

    private final ThreeBigMeetingTopicMapper meetingTopicMapper;
    private final ThreeBigMeetingMapper meetingMapper;
    private final ThreeBigTopicMapper topicMapper;
    private final ThreeBigItemMapper itemMapper;
    private final WorkflowInstanceService workflowInstanceService;
    private final WorkflowDefinitionService workflowDefinitionService;

    /** 固定 processKey：三重一大实施审批 */
    private static final String PROCESS_KEY_EXECUTE = "SYDYD_EXECUTE_APPROVAL";

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void autoGenerateFromVote(Long meetingTopicId) {
        // 1. 会议-议题中间表必须存在 + 表决通过
        ThreeBigMeetingTopic m2t = meetingTopicMapper.selectById(meetingTopicId);
        if (m2t == null) {
            log.warn("[三重一大] autoGenerateFromVote 跳过: meetingTopicId={} 不存在", meetingTopicId);
            return;
        }
        if (m2t.getVoteResult() == null || m2t.getVoteResult() != 1) {
            log.warn("[三重一大] autoGenerateFromVote 跳过: meetingTopicId={} voteResult={} 非通过",
                    meetingTopicId, m2t.getVoteResult());
            return;
        }

        // 2. 幂等：已有实施记录就跳过
        long existing = count(new LambdaQueryWrapper<ThreeBigExecute>()
                .eq(ThreeBigExecute::getMeetingTopicId, meetingTopicId));
        if (existing > 0) {
            log.warn("[三重一大] autoGenerateFromVote 跳过: meetingTopicId={} 已有实施记录", meetingTopicId);
            return;
        }

        // 3. 取冗余信息
        String meetingName = null;
        Integer voteResult = m2t.getVoteResult();
        ThreeBigMeeting meeting = meetingMapper.selectById(m2t.getMeetingId());
        if (meeting != null) meetingName = meeting.getMeetingName();

        String itemName = m2t.getTopicTitle(); // 先拿议题标题兜底
        ThreeBigTopic topic = topicMapper.selectById(m2t.getTopicId());
        if (topic != null) {
            itemName = topic.getItemName();
        }

        // 4. 创建实施记录
        ThreeBigExecute execute = new ThreeBigExecute();
        execute.setMeetingTopicId(meetingTopicId);
        execute.setMeetingId(m2t.getMeetingId());
        execute.setTopicId(m2t.getTopicId());
        execute.setItemId(m2t.getItemId());
        execute.setItemName(itemName);
        execute.setMeetingName(meetingName);
        execute.setVoteResult(voteResult);
        execute.setExecuteStatus(1); // 待执行
        execute.setProgress(0);
        save(execute);

        // 自动发起 workflow（实施审批）
        try {
            Long definitionId = resolvePublishedDefinitionId(PROCESS_KEY_EXECUTE);
            if (definitionId != null) {
                String businessKey = "SYDYD_EXECUTE_" + execute.getId();
                String variables = "{\"executeId\":" + execute.getId() + ",\"itemName\":\"" + execute.getItemName() + "\",\"meetingName\":\"" + execute.getMeetingName() + "\"}";
                Long instanceId = workflowInstanceService.startProcess(definitionId, businessKey, variables);
                execute.setWorkflowInstanceId(instanceId);
                updateById(execute);
                log.info("[三重一大] 实施自动生成 同步发起workflow executeId={}, workflowInstanceId={}", execute.getId(), instanceId);
            } else {
                log.warn("[三重一大] 未找到已发布的流程定义 processKey={}，跳过workflow发起 executeId={}", PROCESS_KEY_EXECUTE, execute.getId());
            }
        } catch (Exception e) {
            log.warn("[三重一大] workflow发起失败，不影响业务 executeId={}, err={}", execute.getId(), e.getMessage());
        }

        log.info("[三重一大] 实施自动生成 executeId={}, meetingTopicId={}, tenantId={}",
                execute.getId(), meetingTopicId, execute.getTenantId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createExecute(ThreeBigExecute execute) {
        // 1. meetingTopicId 必须存在
        Long m2tId = execute.getMeetingTopicId();
        if (m2tId == null) throw new BusinessException("必须指定关联的会议-议题（meetingTopicId）");

        ThreeBigMeetingTopic m2t = meetingTopicMapper.selectById(m2tId);
        if (m2t == null) throw new BusinessException("会议-议题不存在（meetingTopicId=" + m2tId + "）");

        // 2. 表决必须通过
        if (m2t.getVoteResult() == null || m2t.getVoteResult() != 1) {
            throw new BusinessException("该议题未表决通过，不能创建实施任务");
        }

        // 3. 幂等
        long existing = count(new LambdaQueryWrapper<ThreeBigExecute>()
                .eq(ThreeBigExecute::getMeetingTopicId, m2tId));
        if (existing > 0) {
            throw new BusinessException("该议题已存在实施任务，不能重复创建");
        }

        // 4. 补齐冗余字段
        execute.setMeetingId(m2t.getMeetingId());
        execute.setTopicId(m2t.getTopicId());
        execute.setItemId(m2t.getItemId());
        execute.setVoteResult(m2t.getVoteResult());
        if (execute.getItemName() == null) {
            ThreeBigTopic topic = topicMapper.selectById(m2t.getTopicId());
            execute.setItemName(topic != null ? topic.getItemName() : m2t.getTopicTitle());
        }
        if (execute.getExecuteStatus() == null) execute.setExecuteStatus(1);
        if (execute.getProgress() == null) execute.setProgress(0);

        save(execute);

        // 自动发起 workflow
        try {
            Long definitionId = resolvePublishedDefinitionId(PROCESS_KEY_EXECUTE);
            if (definitionId != null) {
                String businessKey = "SYDYD_EXECUTE_" + execute.getId();
                String variables = "{\"executeId\":" + execute.getId() + ",\"itemName\":\"" + execute.getItemName() + "\"}";
                Long instanceId = workflowInstanceService.startProcess(definitionId, businessKey, variables);
                execute.setWorkflowInstanceId(instanceId);
                updateById(execute);
                log.info("[三重一大] 手动创建实施 同步发起workflow executeId={}, workflowInstanceId={}", execute.getId(), instanceId);
            } else {
                log.warn("[三重一大] 未找到已发布的流程定义 processKey={}，跳过workflow发起 executeId={}", PROCESS_KEY_EXECUTE, execute.getId());
            }
        } catch (Exception e) {
            log.warn("[三重一大] workflow发起失败，不影响业务 executeId={}, err={}", execute.getId(), e.getMessage());
        }

        log.info("[三重一大] 手动创建实施 executeId={}", execute.getId());
        return execute.getId();
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
    public void updateProgress(Long id, Integer progress, String progressDetail) {
        ThreeBigExecute execute = getById(id);
        if (execute == null) throw new BusinessException("实施任务不存在");

        // 已完成/已归档 不允许更新进度
        Integer s = execute.getExecuteStatus();
        if (s != null && (s == 3 || s == 5)) {
            throw new BusinessException("当前状态（" + s + "）不允许更新进度");
        }

        if (progress != null) {
            if (progress < 0 || progress > 100) {
                throw new BusinessException("进度必须在 0~100 之间");
            }
            execute.setProgress(progress);
            if (progress == 100 && s != null && s < 3) {
                // 进度拉满自动标记完成
                execute.setExecuteStatus(3);
                execute.setActualFinishTime(LocalDateTime.now());
            }
        }
        if (progressDetail != null) {
            execute.setProgressDetail(progressDetail);
        }

        // 如果计划时间已过 + 状态未完成 → 标记逾期
        if (execute.getPlanFinishTime() != null
                && s != null && s < 3
                && LocalDateTime.now().isAfter(execute.getPlanFinishTime())
                && (execute.getExecuteStatus() == null || execute.getExecuteStatus() == 1 || execute.getExecuteStatus() == 2)) {
            execute.setExecuteStatus(4); // 已逾期
        }

        updateById(execute);
        log.info("[三重一大] 实施进度更新 executeId={}, progress={}, status={}", id, progress, execute.getExecuteStatus());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void start(Long id) {
        ThreeBigExecute execute = getById(id);
        if (execute == null) throw new BusinessException("实施任务不存在");
        Integer s = execute.getExecuteStatus();
        if (s != null && s != 1 && s != 4) {
            throw new BusinessException("当前状态（" + s + "）不允许开始执行（仅 待执行/已逾期 可开始）");
        }
        execute.setExecuteStatus(2);
        updateById(execute);
        log.info("[三重一大] 实施开始 executeId={}", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void finish(Long id, String progressDetail, String evidenceFile) {
        ThreeBigExecute execute = getById(id);
        if (execute == null) throw new BusinessException("实施任务不存在");
        Integer s = execute.getExecuteStatus();
        if (s != null && s != 1 && s != 2 && s != 4) {
            throw new BusinessException("当前状态（" + s + "）不允许完成");
        }
        execute.setExecuteStatus(3);
        execute.setProgress(100);
        execute.setActualFinishTime(LocalDateTime.now());
        if (progressDetail != null) execute.setProgressDetail(progressDetail);
        if (evidenceFile != null) execute.setEvidenceFile(evidenceFile);
        updateById(execute);
        log.info("[三重一大] 实施完成 executeId={}", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void archive(Long id) {
        ThreeBigExecute execute = getById(id);
        if (execute == null) throw new BusinessException("实施任务不存在");
        if (execute.getExecuteStatus() != null && execute.getExecuteStatus() != 3) {
            throw new BusinessException("只有已完成的实施能归档（当前状态=" + execute.getExecuteStatus() + "）");
        }
        execute.setExecuteStatus(5);
        updateById(execute);
        log.info("[三重一大] 实施归档 executeId={}", id);
    }

    @Override
    public IPage<ThreeBigExecute> queryPage(Integer pageNum, Integer pageSize,
                                            Integer executeStatus, Long meetingId,
                                            String itemName, String executeDept) {
        LambdaQueryWrapper<ThreeBigExecute> wrapper = new LambdaQueryWrapper<>();
        if (executeStatus != null) wrapper.eq(ThreeBigExecute::getExecuteStatus, executeStatus);
        if (meetingId != null)    wrapper.eq(ThreeBigExecute::getMeetingId, meetingId);
        if (executeDept != null && !executeDept.isEmpty()) {
            wrapper.eq(ThreeBigExecute::getExecuteDept, executeDept);
        }
        if (itemName != null && !itemName.isEmpty()) {
            wrapper.like(ThreeBigExecute::getItemName, itemName);
        }
        wrapper.orderByDesc(ThreeBigExecute::getCreateTime);
        Page<ThreeBigExecute> page = new Page<>(pageNum, pageSize);
        return page(page, wrapper);
    }
}