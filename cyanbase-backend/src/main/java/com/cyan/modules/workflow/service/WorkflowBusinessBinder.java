package com.cyan.modules.workflow.service;

/**
 * ⭐ 工作流 ↔ 业务表单 回调接口
 * <p>
 * 工作流模块（workflow）不直接依赖业务模块（sydyd），避免循环依赖。
 * 工作流模块只定义本接口，业务模块实现接口来接收回调。
 * <p>
 * businessKey 约定格式：{@code 业务类型小写:主键id}（如 {@code "threeBigItem:123"}）
 */
public interface WorkflowBusinessBinder {

    /** 标识自己处理哪种业务类型（按 businessKey 前缀判断） */
    boolean supports(String businessKey);

    /** 流程发起成功 → 业务表回写 workflowInstanceId */
    void bindBusiness(String businessKey, Long workflowInstanceId);

    /** 流程完结（通过/驳回/撤销）→ 同步业务表状态 */
    void onWorkflowEnd(String businessKey, String instanceStatus);
}