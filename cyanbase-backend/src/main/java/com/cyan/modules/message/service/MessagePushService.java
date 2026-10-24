package com.cyan.modules.message.service;

import com.cyan.modules.message.service.SysMessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * 消息自动推送服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessagePushService {

    private final SysMessageService sysMessageService;

    /**
     * 用户登录成功通知
     */
    public void pushLoginSuccess(Long userId, String userName) {
        String title = "登录通知";
        String content = String.format("用户 %s 登录成功", userName);
        
        sysMessageService.sendToUsers(title, content, 2, Collections.singletonList(userId));
        log.info("推送登录成功通知: userId={}, userName={}", userId, userName);
    }

    /**
     * 密码错误预警
     */
    public void pushPasswordError(Long userId, String userName, String ip) {
        String title = "安全预警";
        String content = String.format("用户 %s 在IP %s 登录时密码错误", userName, ip);
        
        sysMessageService.sendToUsers(title, content, 3, Collections.singletonList(userId));
        log.warn("推送密码错误预警: userId={}, userName={}, ip={}", userId, userName, ip);
    }

    /**
     * 文件上传完成通知
     */
    public void pushFileUploadComplete(Long userId, String fileName) {
        String title = "文件上传通知";
        String content = String.format("文件 %s 上传成功", fileName);
        
        sysMessageService.sendToUsers(title, content, 2, Collections.singletonList(userId));
        log.info("推送文件上传通知: userId={}, fileName={}", userId, fileName);
    }

    /**
     * 定时任务执行失败通知
     */
    public void pushJobExecuteFailed(String jobName, String errorMsg) {
        String title = "任务执行失败";
        String content = String.format("定时任务 %s 执行失败: %s", jobName, errorMsg);
        
        // 发送给所有管理员（这里简化为全员，实际应该发送给管理员角色）
        sysMessageService.sendToAll(title, content, 3);
        log.error("推送任务失败通知: jobName={}, error={}", jobName, errorMsg);
    }

    /**
     * 新用户注册欢迎消息
     */
    public void pushWelcomeMessage(Long userId, String userName) {
        String title = "欢迎加入";
        String content = String.format("欢迎 %s 加入CyanBase系统！", userName);
        
        sysMessageService.sendToUsers(title, content, 4, Collections.singletonList(userId));
        log.info("推送欢迎消息: userId={}, userName={}", userId, userName);
    }

    // ================== 工作流专用推送 ==================

    /**
     * 流程发起人提交后推送："已提交等待审批"
     * 给自己一个确认，同时也会推送给下一节点审批人走 pushWorkflowTodo
     */
    public void pushWorkflowStarted(Long initiatorId, String initiatorName, String processName, String businessKey) {
        String title = "流程已提交";
        String content = String.format("您发起的「%s」（%s）已提交，等待审批。",
                processName, businessKey != null && businessKey.length() > 30 ? businessKey.substring(0, 30) + "..." : businessKey);
        sysMessageService.sendToUsers(title, content, 5, Collections.singletonList(initiatorId));
        log.info("推送流程发起通知: initiator={}, process={}, businessKey={}", initiatorName, processName, businessKey);
    }

    /**
     * 推送待办给下一节点审批人（多人会签/或签都覆盖）
     */
    public void pushWorkflowTodo(String processName, String nodeName, String initiatorName,
                                 String businessKey, List<Long> assigneeIds) {
        if (assigneeIds == null || assigneeIds.isEmpty()) return;
        String title = "您有新的待办审批";
        String content = String.format("流程「%s」已流转到节点「%s」，发起人：%s，请及时处理。",
                processName, nodeName, initiatorName);
        sysMessageService.sendToUsers(title, content, 5, assigneeIds);
        log.info("推送待办通知: process={}, node={}, assignees={}", processName, nodeName, assigneeIds.size());
    }

    /**
     * 驳回时推送发起人
     */
    public void pushWorkflowRejected(Long initiatorId, String initiatorName,
                                      String processName, String nodeName, String comment) {
        String title = "流程被驳回";
        String content = String.format("您发起的「%s」在节点「%s」被驳回，审批意见：%s",
                processName, nodeName, comment != null ? comment : "无");
        sysMessageService.sendToUsers(title, content, 5, Collections.singletonList(initiatorId));
        log.info("推送驳回通知: initiator={}, process={}, node={}", initiatorName, processName, nodeName);
    }

    /**
     * 全部审批通过推送发起人
     */
    public void pushWorkflowCompleted(Long initiatorId, String initiatorName,
                                      String processName, String businessKey) {
        String title = "流程审批通过";
        String content = String.format("您发起的「%s」已全部审批通过，流程结束。",
                processName);
        sysMessageService.sendToUsers(title, content, 5, Collections.singletonList(initiatorId));
        log.info("推送流程完结通知: initiator={}, process={}", initiatorName, processName);
    }
}