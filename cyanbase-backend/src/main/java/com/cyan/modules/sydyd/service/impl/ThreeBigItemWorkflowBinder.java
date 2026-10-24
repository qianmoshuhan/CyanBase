package com.cyan.modules.sydyd.service.impl;

import com.cyan.common.exception.BusinessException;
import com.cyan.modules.sydyd.entity.ThreeBigItem;
import com.cyan.modules.sydyd.service.ThreeBigItemService;
import com.cyan.modules.workflow.service.WorkflowBusinessBinder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * ⭐ 三重一大事项 ↔ 工作流 回调实现
 * <p>
 * 职责：监听工作流引擎的发起/完结事件，同步 ThreeBigItem 业务表状态。
 * 工作流引擎（workflow 模块）只依赖接口 WorkflowBusinessBinder，
 * 本类位于 sydyd 模块反向实现接口，不产生循环依赖。
 * <p>
 * businessKey 约定格式：threeBigItem:{itemId}（如 "threeBigItem:123"）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ThreeBigItemWorkflowBinder implements WorkflowBusinessBinder {

    public static final String PREFIX = "threeBigItem:";

    private final ThreeBigItemService threeBigItemService;

    @Override
    public boolean supports(String businessKey) {
        return businessKey != null && businessKey.startsWith(PREFIX);
    }

    /**
     * 解析 businessKey → itemId
     */
    private Long parseItemId(String businessKey) {
        try {
            return Long.parseLong(businessKey.substring(PREFIX.length()));
        } catch (NumberFormatException e) {
            throw new BusinessException("业务键格式错误：" + businessKey + "，预期：threeBigItem:{主键}");
        }
    }

    /**
     * 流程发起成功 → 业务表回写 workflowInstanceId
     */
    @Override
    public void bindBusiness(String businessKey, Long workflowInstanceId) {
        Long itemId = parseItemId(businessKey);
        ThreeBigItem item = threeBigItemService.getById(itemId);
        if (item == null) {
            throw new BusinessException("三重一大事项不存在：id=" + itemId);
        }
        item.setWorkflowInstanceId(workflowInstanceId);
        // 事项状态推进：草稿(1) → 待酝酿(2)，表示已进入审批流程
        if (item.getItemStatus() == null || item.getItemStatus() == 1) {
            item.setItemStatus(2);
        }
        threeBigItemService.updateById(item);
        log.info("[ThreeBigItem→Workflow] 回写 itemId={}, workflowInstanceId={}, itemStatus={}",
                itemId, workflowInstanceId, item.getItemStatus());
    }

    /**
     * 流程完结 → 同步业务表最终状态
     */
    @Override
    public void onWorkflowEnd(String businessKey, String instanceStatus) {
        Long itemId = parseItemId(businessKey);
        ThreeBigItem item = threeBigItemService.getById(itemId);
        if (item == null) {
            log.warn("[ThreeBigItem→Workflow] 事项不存在，跳过完结回调：itemId={}", itemId);
            return;
        }

        /**
         * 三重一大事项状态编码（来自 ThreeBigItem.itemStatus 注释）：
         *   1=草稿  2=待酝酿  3=已生成议题  4=已上会  5=已办结  6=已驳回
         *
         * 流程实例状态 → 业务状态 映射：
         *   completed → 已办结(5)
         *   rejected  → 已驳回(6)
         *   cancelled → 已驳回(6)（发起人主动取消也视为驳回）
         */
        switch (instanceStatus) {
            case "completed" -> item.setItemStatus(5);
            case "rejected", "cancelled" -> item.setItemStatus(6);
            default -> log.warn("[ThreeBigItem→Workflow] 未知流程状态：{}，不更新 itemStatus", instanceStatus);
        }
        threeBigItemService.updateById(item);
        log.info("[ThreeBigItem→Workflow] 完结回调 itemId={}, workflowStatus={}, itemStatus={}",
                itemId, instanceStatus, item.getItemStatus());
    }
}