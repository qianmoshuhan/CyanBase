package com.cyan.modules.workflow.bpmn.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cyan.common.exception.BusinessException;
import com.cyan.modules.workflow.bpmn.entity.WfBpmnDefinition;
import com.cyan.modules.workflow.bpmn.mapper.WfBpmnDefinitionMapper;
import com.cyan.modules.workflow.bpmn.service.WfBpmnDefinitionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * BPMN 流程定义 Service 实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WfBpmnDefinitionServiceImpl
        extends ServiceImpl<WfBpmnDefinitionMapper, WfBpmnDefinition>
        implements WfBpmnDefinitionService {

    @Override
    public Page<WfBpmnDefinition> queryPageList(Integer pageNum, Integer pageSize,
                                                 String processName, String processKey,
                                                 Integer status) {
        Page<WfBpmnDefinition> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<WfBpmnDefinition> w = new LambdaQueryWrapper<>();
        // 排序：版本倒序、创建时间倒序
        w.orderByDesc(WfBpmnDefinition::getVersion)
         .orderByDesc(WfBpmnDefinition::getCreateTime);
        // 条件筛选
        if (processName != null && !processName.isEmpty()) {
            w.like(WfBpmnDefinition::getProcessName, processName);
        }
        if (processKey != null && !processKey.isEmpty()) {
            w.like(WfBpmnDefinition::getProcessKey, processKey);
        }
        if (status != null) {
            w.eq(WfBpmnDefinition::getStatus, status);
        }
        return this.page(page, w);
    }

    @Override
    public boolean existsProcessKeyAndVersion(String processKey, Integer version, Long excludeId) {
        LambdaQueryWrapper<WfBpmnDefinition> w = new LambdaQueryWrapper<>();
        w.eq(WfBpmnDefinition::getProcessKey, processKey)
         .eq(WfBpmnDefinition::getVersion, version);
        if (excludeId != null) {
            w.ne(WfBpmnDefinition::getId, excludeId);
        }
        return this.count(w) > 0;
    }

    /**
     * 自动生成流程 Key
     * 格式：bpmn_ + UUID去横线前16位，如 bpmn_a1b2c3d4e5f6g7h8
     * 若生成的 Key 与已有记录冲突，则重试最多 5 次
     */
    public String generateProcessKey() {
        for (int i = 0; i < 5; i++) {
            String key = "bpmn_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            if (!existsProcessKeyAndVersion(key, 1, null)) {
                return key;
            }
            log.warn("[BPMN] 生成的 processKey 冲突，重试第 {} 次：{}", i + 1, key);
        }
        throw new BusinessException("生成流程 Key 失败，请稍后重试");
    }

    @Override
    public boolean publish(Long id) {
        WfBpmnDefinition def = this.getById(id);
        if (def == null) {
            throw new BusinessException("BPMN 流程定义不存在");
        }
        if (def.getStatus() != null && def.getStatus() == 1) {
            throw new BusinessException("该流程已发布，无需重复操作");
        }
        // 简单校验：bpmn_xml 不能为空
        if (def.getBpmnXml() == null || def.getBpmnXml().trim().isEmpty()) {
            throw new BusinessException("画布内容为空，无法发布");
        }
        def.setStatus(1);
        return this.updateById(def);
    }
}