package com.cyan.modules.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cyan.common.exception.BusinessException;
import com.cyan.modules.workflow.entity.WfDefinitionTenant;
import com.cyan.modules.workflow.mapper.WfDefinitionTenantMapper;
import com.cyan.modules.workflow.service.WfDefinitionTenantService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 工作流定义租户分发 Service 实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WfDefinitionTenantServiceImpl extends ServiceImpl<WfDefinitionTenantMapper, WfDefinitionTenant>
        implements WfDefinitionTenantService {

    @Override
    public List<WfDefinitionTenant> listByDefinitionId(Long definitionId) {
        return this.list(new LambdaQueryWrapper<WfDefinitionTenant>()
                .eq(WfDefinitionTenant::getDefinitionId, definitionId)
                .eq(WfDefinitionTenant::getStatus, 1));
    }

    @Override
    public List<WfDefinitionTenant> listByTenantId(Long tenantId) {
        return this.list(new LambdaQueryWrapper<WfDefinitionTenant>()
                .eq(WfDefinitionTenant::getTenantId, tenantId)
                .eq(WfDefinitionTenant::getStatus, 1));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void distribute(Long definitionId, String definitionKey, String definitionName,
                           Set<Long> tenantIds, boolean allowEdit) {
        if (tenantIds == null || tenantIds.isEmpty()) {
            throw new BusinessException("请选择至少一个目标租户");
        }

        for (Long tid : tenantIds) {
            // 先查有没有已分发记录（存在则更新，不存在则新建）
            WfDefinitionTenant existing = this.getOne(new LambdaQueryWrapper<WfDefinitionTenant>()
                    .eq(WfDefinitionTenant::getDefinitionId, definitionId)
                    .eq(WfDefinitionTenant::getTenantId, tid));

            if (existing != null) {
                existing.setStatus(1);
                existing.setAllowEdit(allowEdit ? 1 : 0);
                existing.setReceiveTime(LocalDateTime.now());
                this.updateById(existing);
                log.info("[Workflow→Tenant] 更新分发: definitionId={}, tenantId={}, allowEdit={}",
                        definitionId, tid, allowEdit);
            } else {
                WfDefinitionTenant record = new WfDefinitionTenant();
                record.setDefinitionId(definitionId);
                record.setDefinitionKey(definitionKey);
                record.setDefinitionName(definitionName);
                record.setTenantId(tid);
                record.setAllowEdit(allowEdit ? 1 : 0);
                record.setStatus(1);
                record.setReceiveTime(LocalDateTime.now());
                this.save(record);
                log.info("[Workflow→Tenant] 新增分发: definitionId={}, tenantId={}, allowEdit={}",
                        definitionId, tid, allowEdit);
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void revoke(Long definitionId, Long tenantId) {
        this.update(null, new LambdaUpdateWrapper<WfDefinitionTenant>()
                .eq(WfDefinitionTenant::getDefinitionId, definitionId)
                .eq(WfDefinitionTenant::getTenantId, tenantId)
                .set(WfDefinitionTenant::getStatus, 0));
    }

    @Override
    public boolean exists(Long definitionId, Long tenantId, Integer status) {
        LambdaQueryWrapper<WfDefinitionTenant> w = new LambdaQueryWrapper<>();
        w.eq(WfDefinitionTenant::getDefinitionId, definitionId)
         .eq(WfDefinitionTenant::getTenantId, tenantId);
        if (status != null) w.eq(WfDefinitionTenant::getStatus, status);
        return this.count(w) > 0;
    }
}