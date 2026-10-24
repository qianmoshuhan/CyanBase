package com.cyan.modules.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.IService;
import com.cyan.modules.workflow.entity.WfNodeRuntime;

import java.util.List;

/**
 * 节点运行时状态 Service
 */
public interface WfNodeRuntimeService extends IService<WfNodeRuntime> {

    /** 实例的某个节点的运行时 */
    WfNodeRuntime getByInstanceAndNode(Long instanceId, Long nodeId);

    /** 实例当前节点运行时（status=WAITING） */
    List<WfNodeRuntime> getCurrentRuntimes(Long instanceId);

    /** 实例所有运行时（按 start_time 排序） */
    List<WfNodeRuntime> listByInstanceId(Long instanceId);
}
