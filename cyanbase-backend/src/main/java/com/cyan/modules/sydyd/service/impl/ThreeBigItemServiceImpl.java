package com.cyan.modules.sydyd.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cyan.common.exception.BusinessException;
import com.cyan.modules.sydyd.entity.ThreeBigItem;
import com.cyan.modules.sydyd.entity.ThreeBigList;
import com.cyan.modules.sydyd.entity.ThreeBigSystem;
import com.cyan.modules.sydyd.mapper.ThreeBigItemMapper;
import com.cyan.modules.sydyd.mapper.ThreeBigListMapper;
import com.cyan.modules.sydyd.mapper.ThreeBigSystemMapper;
import com.cyan.modules.sydyd.service.ThreeBigItemService;
import com.cyan.modules.workflow.entity.WorkflowDefinition;
import com.cyan.modules.workflow.service.WorkflowDefinitionService;
import com.cyan.modules.workflow.service.WorkflowInstanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 三重一大业务事项 Service 实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ThreeBigItemServiceImpl
        extends ServiceImpl<ThreeBigItemMapper, ThreeBigItem>
        implements ThreeBigItemService {

    private final ThreeBigListMapper listMapper;
    private final ThreeBigSystemMapper systemMapper;
    private final WorkflowInstanceService workflowInstanceService;
    private final WorkflowDefinitionService workflowDefinitionService;

    /** 固定业务类型：三重一大事项（与前端 designer 下拉 + WorkflowBusinessBinder.supports 对应） */
    private static final String BUSINESS_TYPE = "threeBigItem";

    @Override
    public IPage<ThreeBigItem> queryPage(Integer pageNum, Integer pageSize,
                                          Integer itemStatus, Integer itemType,
                                          Long systemId, String itemName,
                                          Long userDeptId, boolean isAdmin) {

        LambdaQueryWrapper<ThreeBigItem> wrapper = new LambdaQueryWrapper<>();

        // === 筛选条件 ===
        if (itemStatus != null)  wrapper.eq(ThreeBigItem::getItemStatus, itemStatus);
        if (itemType != null)    wrapper.eq(ThreeBigItem::getItemType, itemType);
        if (systemId != null)    wrapper.eq(ThreeBigItem::getSystemId, systemId);
        if (itemName != null && !itemName.isEmpty()) {
            wrapper.like(ThreeBigItem::getItemName, itemName);
        }

        // === 权限过滤 ===
        if (!isAdmin && userDeptId != null) {
            // 填报员/部门领导：只能看本部门提交的事项
            wrapper.eq(ThreeBigItem::getSubmitDeptId, userDeptId);
        }
        // 管理员：不加过滤，MP 自动隔离 + 租户隔离后全量可见

        wrapper.orderByDesc(ThreeBigItem::getCreateTime);

        Page<ThreeBigItem> page = new Page<>(pageNum, pageSize);
        return page(page, wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createDraft(ThreeBigItem item) {
        // 1. 清单必须存在 + 启用
        ThreeBigList list = listMapper.selectById(item.getListId());
        if (list == null) throw new BusinessException("清单不存在（listId=" + item.getListId() + "）");
        if (list.getStatus() != null && list.getStatus() == 1) {
            throw new BusinessException("清单已停用，不能在停用清单下填报");
        }

        // 2. 清单所属制度必须存在 + 启用
        ThreeBigSystem system = systemMapper.selectById(list.getSystemId());
        if (system == null) throw new BusinessException("制度不存在（systemId=" + list.getSystemId() + "）");
        if (system.getStatus() != null && system.getStatus() == 1) {
            throw new BusinessException("所属制度已停用，无法填报");
        }

        // 3. 自动补齐冗余字段
        item.setSystemId(list.getSystemId());
        // 事项类型默认从清单 category 继承
        if (item.getItemType() == null) {
            item.setItemType(list.getCategory());
        }
        // 状态默认草稿
        if (item.getItemStatus() == null) {
            item.setItemStatus(1);
        }
        // 申请时间默认现在
        if (item.getApplyTime() == null) {
            item.setApplyTime(LocalDateTime.now());
        }

        save(item);
        log.info("[三重一大] 新建事项 itemId={}, tenantId={}", item.getId(), item.getTenantId());
        return item.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long id, Long definitionId) {
        ThreeBigItem item = getById(id);
        if (item == null) throw new BusinessException("事项不存在");
        if (item.getItemStatus() != null && item.getItemStatus() != 1) {
            throw new BusinessException("只有草稿状态可以提交，当前状态=" + item.getItemStatus());
        }

        // 1. 自动发起 workflow
        try {
            Long defId = definitionId != null ? definitionId : resolvePublishedDefinitionId();
            if (defId != null) {
                // ⭐ businessKey 格式必须匹配 ThreeBigItemWorkflowBinder.PREFIX = "threeBigItem:"
                String businessKey = BUSINESS_TYPE + ":" + id;
                String variables = "{\"itemId\":" + id + ",\"itemName\":\"" + item.getItemName() + "\",\"fundAmount\":" + item.getFundAmount() + "}";
                Long instanceId = workflowInstanceService.startProcess(defId, businessKey, variables);
                log.info("[三重一大] 事项提交 同步发起workflow itemId={}, definitionId={}, instanceId={}, businessKey={}",
                        id, defId, instanceId, businessKey);
            } else {
                log.warn("[三重一大] 未找到已发布的流程定义 businessType={}，跳过workflow发起 itemId={}", BUSINESS_TYPE, id);
            }
        } catch (Exception e) {
            // workflow 异常不阻塞业务提交，只记 warn
            log.warn("[三重一大] workflow发起失败，不影响业务 itemId={}, err={}", id, e.getMessage());
        }

        // 2. 状态推进（注意：ThreeBigItemWorkflowBinder.bindBusiness 里也会把 itemStatus 设为 2
        //    这里是兜底，防止 workflow 异常时业务表状态不推进）
        if (item.getItemStatus() == null || item.getItemStatus() == 1) {
            item.setItemStatus(2); // 待酝酿
            updateById(item);
        }
        log.info("[三重一大] 事项提交 itemId={}, itemStatus={}", id, item.getItemStatus());
    }

    /**
     * 按 businessType 查已发布(status=1)的流程定义。找不到返回 null（非阻塞，业务继续）。
     * <p>
     * 多个 businessType=threeBigItem 的已发布流程时，取 update_time 最新的那一个
     * （用户也可以在前端显式传 definitionId 来指定）。
     */
    private Long resolvePublishedDefinitionId() {
        WorkflowDefinition def = workflowDefinitionService.getOne(
                new LambdaQueryWrapper<WorkflowDefinition>()
                        .eq(WorkflowDefinition::getBusinessType, BUSINESS_TYPE)
                        .eq(WorkflowDefinition::getStatus, 1)
                        .orderByDesc(WorkflowDefinition::getUpdateTime)
                        .last("LIMIT 1"));
        if (def == null) {
            // 降级：如果还没配 businessType，也按 processKey 兜底查一下（老流程兼容）
            def = workflowDefinitionService.getOne(
                    new LambdaQueryWrapper<WorkflowDefinition>()
                            .eq(WorkflowDefinition::getStatus, 1)
                            .likeRight(WorkflowDefinition::getProcessKey, "SYDYD")
                            .last("LIMIT 1"));
        }
        return def != null ? def.getId() : null;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void withdraw(Long id) {
        ThreeBigItem item = getById(id);
        if (item == null) throw new BusinessException("事项不存在");
        // 允许回退的状态：待酝酿 / 已驳回
        Integer s = item.getItemStatus();
        if (s != null && s != 2 && s != 6) {
            throw new BusinessException("当前状态不允许回退，状态=" + s);
        }
        item.setItemStatus(1); // 草稿
        updateById(item);
        log.info("[三重一大] 事项回退草稿 itemId={}", id);
    }

    /**
     * 删除事项 + 级联清理工作流脏数据。
     * <p>
     * 场景：用户删了业务事项（MP 逻辑删除 delFlag=1），
     * 但工作流 wf_process_instance / wf_approve 表里的记录还在，
     * 审批人登录后"我的待办"仍能看到这条脏数据，且前台无法删除。
     * <p>
     * 解法：删除业务前，先按 businessKey=threeBigItem:{id} 物理删掉关联的工作流实例 + 审批记录，
     * 再执行业务表的逻辑删除。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteWithWorkflowClean(Long id) {
        ThreeBigItem item = getById(id);
        if (item == null) {
            log.warn("[三重一大] 待删事项不存在 id={}", id);
            return false;
        }

        // 1. 先物理清理工作流脏数据（无论草稿还是已提交，都清）
        String businessKey = BUSINESS_TYPE + ":" + id;
        try {
            workflowInstanceService.deleteByBusinessKey(businessKey);
        } catch (Exception e) {
            // 清理失败只记日志，不阻塞业务删除
            log.error("[三重一大] 级联清理工作流失败 itemId={}, businessKey={}", id, businessKey, e);
        }

        // 2. 清理成功后，业务表逻辑删除
        boolean ok = this.removeById(id);
        log.info("[三重一大] 删除事项 itemId={}, ok={}", id, ok);
        return ok;
    }
}