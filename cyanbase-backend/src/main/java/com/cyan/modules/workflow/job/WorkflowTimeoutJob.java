package com.cyan.modules.workflow.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cyan.modules.workflow.entity.WorkflowApprove;
import com.cyan.modules.workflow.entity.WorkflowInstance;
import com.cyan.modules.workflow.service.WorkflowApproveService;
import com.cyan.modules.workflow.service.WorkflowInstanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * ⭐ Phase 3：工作流审批超时催办定时任务
 * <p>用法：在 sys_job 表配置一条任务，invoke_target 填 {@code workflowTimeoutJob.checkAndNotify}，cron 建议每 10 分钟一次。
 * 可选扩展：超时自动转办上级 / 超时自动 approve（通过 system config 开关控制）
 * </p>
 */
@Slf4j
@Component("workflowTimeoutJob")
@RequiredArgsConstructor
public class WorkflowTimeoutJob {

    private final WorkflowApproveService workflowApproveService;
    private final WorkflowInstanceService workflowInstanceService;

    /**
     * 检查所有超时的待办审批，发消息提醒审批人
     */
    public void checkAndNotify() {
        LocalDateTime now = LocalDateTime.now();

        // 1. 查所有 action IS NULL 且 deadline < now 的审批记录
        List<WorkflowApprove> overdue = workflowApproveService.list(new LambdaQueryWrapper<WorkflowApprove>()
                .isNull(WorkflowApprove::getAction)
                .lt(WorkflowApprove::getDeadline, now));

        if (overdue.isEmpty()) {
            log.debug("[WorkflowTimeoutJob] 本次扫描无超时待办");
            return;
        }

        log.info("[WorkflowTimeoutJob] 发现 {} 条超时待办，开始催办", overdue.size());

        // 2. 聚合一下：(instanceId, nodeId, approverId) → 消息内容
        // 这里先只打日志，后续接 SysMessageService / MessagePushService 推送
        // 如果项目里有消息推送，取消下面这段的注释即可
        for (WorkflowApprove app : overdue) {
            try {
                WorkflowInstance inst = workflowInstanceService.getById(app.getInstanceId());
                if (inst == null) continue;

                // ⭐ 推送内部消息（按需接入）：
                // sysMessageService.sendSysMessage(app.getApproverId(), "工作流审批超时提醒",
                //     String.format("您有一条审批待办已超时，流程「%s」当前节点「%s」，超时时长：%s",
                //     inst.getProcessName(), app.getNodeName(), java.time.Duration.between(app.getDeadline(), now).toMinutes() + " 分钟"));

                log.info("[WorkflowTimeoutJob] 超时提醒: approverId={}, instanceId={}, nodeName={}, processName={}, deadline={}",
                        app.getApproverId(), app.getInstanceId(), app.getNodeName(),
                        inst.getProcessName(), app.getDeadline());
            } catch (Exception e) {
                log.error("[WorkflowTimeoutJob] 处理超时待办异常 approveId={}, err={}", app.getId(), e.getMessage());
            }
        }
    }
}