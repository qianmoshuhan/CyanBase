package com.cyan.modules.workflow.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.cyan.modules.workflow.entity.WfDefinitionTenant;

import java.util.List;
import java.util.Set;

/**
 * 工作流定义租户分发 Service
 */
public interface WfDefinitionTenantService extends IService<WfDefinitionTenant> {

    /** 查询 definitionId 已分发给哪些租户 */
    List<WfDefinitionTenant> listByDefinitionId(Long definitionId);

    /** 查询 tenantId 收到的所有有效分发 */
    List<WfDefinitionTenant> listByTenantId(Long tenantId);

    /**
     * 批量分发工作流定义到多个租户
     *
     * @param definitionId  工作流定义ID
     * @param tenantIds     目标租户ID列表
     * @param allowEdit     租户是否允许编辑（0=只读 1=可复制）
     */
    void distribute(Long definitionId, String definitionKey, String definitionName, Set<Long> tenantIds, boolean allowEdit);

    /** 撤回分发 */
    void revoke(Long definitionId, Long tenantId);

    /** ⭐ 查询指定 definition 是否已分发给指定租户且有效 */
    boolean exists(Long definitionId, Long tenantId, Integer status);
}