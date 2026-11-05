package com.cyan.modules.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cyan.common.exception.BusinessException;
import com.cyan.common.security.util.SecurityUtils;
import com.cyan.modules.workflow.entity.WfDefVersion;
import com.cyan.modules.workflow.entity.WorkflowDefinition;
import com.cyan.modules.workflow.entity.WorkflowNode;
import com.cyan.modules.workflow.mapper.WfDefVersionMapper;
import com.cyan.modules.workflow.service.WfDefVersionService;
import com.cyan.modules.workflow.service.WorkflowDefinitionService;
import com.cyan.modules.workflow.service.WorkflowNodeService;
import com.cyan.modules.workflow.util.BpmnXmlGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class WfDefVersionServiceImpl extends com.baomidou.mybatisplus.extension.service.impl.ServiceImpl<WfDefVersionMapper, WfDefVersion>
        implements WfDefVersionService {

    private final WorkflowDefinitionService workflowDefinitionService;
    private final WorkflowNodeService workflowNodeService;
    private final BpmnXmlGenerator bpmnXmlGenerator;

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Override
    public List<WfDefVersion> listByDefinitionId(Long definitionId) {
        LambdaQueryWrapper<WfDefVersion> w = new LambdaQueryWrapper<>();
        w.eq(WfDefVersion::getDefinitionId, definitionId)
         .orderByDesc(WfDefVersion::getVersionNo);
        return this.list(w);
    }

    @Override
    public WfDefVersion getLatestByDefinitionId(Long definitionId) {
        LambdaQueryWrapper<WfDefVersion> w = new LambdaQueryWrapper<>();
        w.eq(WfDefVersion::getDefinitionId, definitionId)
         .eq(WfDefVersion::getIsLatest, 1)
         .last("LIMIT 1");
        return this.getOne(w);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WfDefVersion ensureInitVersion(Long definitionId) {
        // 已有版本 → 直接返回最新
        WfDefVersion latest = getLatestByDefinitionId(definitionId);
        if (latest != null) return latest;

        // 没有版本 → 从主表补 V1
        WorkflowDefinition def = workflowDefinitionService.getById(definitionId);
        if (def == null) {
            throw new BusinessException("流程定义不存在");
        }
        List<WorkflowNode> nodes = workflowNodeService.listByDefinitionId(definitionId);
        Long newId = createInitialVersion(definitionId, def.getProcessXml(), nodes);
        return this.getById(newId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createNewVersionFrom(Long sourceVersionId, String newVersionName) {
        WfDefVersion source = this.getById(sourceVersionId);
        if (source == null) {
            throw new BusinessException("源版本不存在");
        }

        // 1. 把同 definition 下所有 is_latest 置 0
        LambdaQueryWrapper<WfDefVersion> resetLatest = new LambdaQueryWrapper<>();
        resetLatest.eq(WfDefVersion::getDefinitionId, source.getDefinitionId())
                   .eq(WfDefVersion::getIsLatest, 1);
        WfDefVersion resetEntity = new WfDefVersion();
        resetEntity.setIsLatest(0);
        this.update(resetEntity, resetLatest);

        // 2. 计算新版本号 = 该 definition 下最大 versionNo + 1
        LambdaQueryWrapper<WfDefVersion> maxNoW = new LambdaQueryWrapper<>();
        maxNoW.eq(WfDefVersion::getDefinitionId, source.getDefinitionId())
              .orderByDesc(WfDefVersion::getVersionNo)
              .last("LIMIT 1");
        WfDefVersion maxNoVer = this.getOne(maxNoW, false);
        int newVersionNo = (maxNoVer == null ? 0 : maxNoVer.getVersionNo()) + 1;

        // 3. 创建新版本快照(复制 flow_json)
        WfDefVersion newVer = new WfDefVersion();
        newVer.setDefinitionId(source.getDefinitionId());
        newVer.setVersionNo(newVersionNo);
        newVer.setVersionName(newVersionName);
        newVer.setFlowJson(source.getFlowJson());
        newVer.setIsLatest(1);
        newVer.setTenantId(source.getTenantId());
        this.save(newVer);

        log.debug("[WfDefVersion] 创建新版本成功: definitionId={}, 旧版本={}, 新版本={}",
                source.getDefinitionId(), source.getVersionNo(), newVersionNo);
        return newVer.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createInitialVersion(Long definitionId, String processXml, List<WorkflowNode> nodes) {
        WorkflowDefinition def = workflowDefinitionService.getById(definitionId);
        if (def == null) {
            throw new BusinessException("流程定义不存在");
        }

        // 如果已有版本，不重复创建
        WfDefVersion existing = getLatestByDefinitionId(definitionId);
        if (existing != null) {
            return existing.getId();
        }

        // 先把同 definition 下所有 is_latest 置 0（防御性）
        LambdaQueryWrapper<WfDefVersion> resetLatest = new LambdaQueryWrapper<>();
        resetLatest.eq(WfDefVersion::getDefinitionId, definitionId);
        WfDefVersion resetEntity = new WfDefVersion();
        resetEntity.setIsLatest(0);
        this.update(resetEntity, resetLatest);

        WfDefVersion ver = new WfDefVersion();
        ver.setDefinitionId(definitionId);
        ver.setVersionNo(1);
        ver.setVersionName(null);
        ver.setFlowJson(buildFlowJson(def, processXml, nodes));
        ver.setIsLatest(1);
        ver.setTenantId(def.getTenantId() != null ? def.getTenantId() : 0L);
        this.save(ver);

        log.debug("[WfDefVersion] 初始化 V1 版本: definitionId={}", definitionId);
        return ver.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveVersionSnapshot(Long versionId, String flowJson) {
        WfDefVersion ver = this.getById(versionId);
        if (ver == null) {
            throw new BusinessException("版本不存在");
        }

        // ⭐ 自动解析 flow_json，用 nodes 生成 BPMN XML，回填后再存库
        try {
            java.util.Map<String, Object> raw = MAPPER.readValue(flowJson, java.util.Map.class);

            // 1. 取 nodes → 反序列化为 List<WorkflowNode>
            @SuppressWarnings("unchecked")
            List<WorkflowNode> nodes = MAPPER.convertValue(
                    raw.get("nodes"),
                    MAPPER.getTypeFactory().constructCollectionType(List.class, WorkflowNode.class));

            // 2. 取 definition 里的 processKey 和 processName
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> defMap = (java.util.Map<String, Object>) raw.get("definition");
            String processId = (defMap != null && defMap.get("processKey") != null)
                    ? defMap.get("processKey").toString() : "P_" + ver.getDefinitionId();
            String processName = (defMap != null && defMap.get("processName") != null)
                    ? defMap.get("processName").toString() : "未命名";

            // 3. 有节点才生成 BPMN
            if (nodes != null && !nodes.isEmpty()) {
                String bpmnXml = bpmnXmlGenerator.generate(processId, processName, nodes);
                raw.put("processXml", bpmnXml);
                log.debug("[WfDefVersion] 版本 {} 自动生成 BPMN XML, processId={}, nodes={}", versionId, processId, nodes.size());
            }

            // 4. 回填后重新序列化存库
            String finalJson = MAPPER.writeValueAsString(raw);
            ver.setFlowJson(finalJson);
        } catch (Exception e) {
            log.warn("[WfDefVersion] 自动生成 BPMN 失败，原样保存 versionId={}, err={}", versionId, e.getMessage());
            ver.setFlowJson(flowJson);
        }

        this.updateById(ver);
    }

    /** 组装 flow_json 字符串 */
    public static String buildFlowJson(WorkflowDefinition def, String processXml, List<WorkflowNode> nodes) {
        try {
            java.util.Map<String, Object> snapshot = new java.util.HashMap<>();
            // definition 基础信息(只存设计相关字段，不含 id/tenantId/审计字段)
            java.util.Map<String, Object> defMap = new java.util.HashMap<>();
            defMap.put("processName", def.getProcessName());
            defMap.put("processKey", def.getProcessKey());
            defMap.put("category", def.getCategory());
            defMap.put("formDefinitionId", def.getFormDefinitionId());
            defMap.put("businessType", def.getBusinessType());
            defMap.put("description", def.getDescription());
            snapshot.put("definition", defMap);
            snapshot.put("processXml", processXml);
            // ⭐ 组装 nodes 时只保留设计字段，过滤掉 LocalDateTime / 审计字段
            java.util.List<java.util.Map<String, Object>> cleanNodes = new java.util.ArrayList<>();
            if (nodes != null) {
                for (WorkflowNode n : nodes) {
                    java.util.Map<String, Object> m = new java.util.HashMap<>();
                    m.put("id", n.getId());
                    m.put("nodeKey", n.getNodeKey());
                    m.put("nodeName", n.getNodeName());
                    m.put("nodeType", n.getNodeType());
                    m.put("assigneeType", n.getAssigneeType());
                    m.put("assigneeValue", n.getAssigneeValue());
                    m.put("approvalType", n.getApprovalType());
                    m.put("timeoutHours", n.getTimeoutHours());
                    m.put("nextNodeIds", n.getNextNodeIds());
                    m.put("conditionExpr", n.getConditionExpr());
                    m.put("showDesc", n.getShowDesc());
                    m.put("gatewayType", n.getGatewayType());
                    m.put("sortOrder", n.getSortOrder());
                    cleanNodes.add(m);
                }
            }
            snapshot.put("nodes", cleanNodes);
            return MAPPER.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            throw new BusinessException("流程快照JSON序列化失败: " + e.getMessage());
        }
    }
}