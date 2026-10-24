package com.cyan.modules.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cyan.modules.workflow.entity.WfNodeRuntime;
import com.cyan.modules.workflow.mapper.WfNodeRuntimeMapper;
import com.cyan.modules.workflow.service.WfNodeRuntimeService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 节点运行时状态 Service 实现
 */
@Service
public class WfNodeRuntimeServiceImpl extends ServiceImpl<WfNodeRuntimeMapper, WfNodeRuntime> implements WfNodeRuntimeService {

    @Override
    public WfNodeRuntime getByInstanceAndNode(Long instanceId, Long nodeId) {
        return this.getOne(new LambdaQueryWrapper<WfNodeRuntime>()
                .eq(WfNodeRuntime::getInstanceId, instanceId)
                .eq(WfNodeRuntime::getNodeId, nodeId));
    }

    @Override
    public List<WfNodeRuntime> getCurrentRuntimes(Long instanceId) {
        return this.list(new LambdaQueryWrapper<WfNodeRuntime>()
                .eq(WfNodeRuntime::getInstanceId, instanceId)
                .eq(WfNodeRuntime::getStatus, "WAITING"));
    }

    @Override
    public List<WfNodeRuntime> listByInstanceId(Long instanceId) {
        return this.list(new LambdaQueryWrapper<WfNodeRuntime>()
                .eq(WfNodeRuntime::getInstanceId, instanceId)
                .orderByAsc(WfNodeRuntime::getStartTime));
    }
}
