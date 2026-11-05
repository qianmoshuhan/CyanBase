package com.cyan.modules.workflow.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.cyan.modules.workflow.entity.WfDefVersion;

import java.util.List;

public interface WfDefVersionService extends IService<WfDefVersion> {

    /** 查询某流程定义的全部版本，按 versionNo 倒序 */
    List<WfDefVersion> listByDefinitionId(Long definitionId);

    /** 查询某流程定义的最新版本 */
    WfDefVersion getLatestByDefinitionId(Long definitionId);

    /**
     * 懒初始化：如果版本表没有任何记录，从主表自动补一条 V1 快照
     * @return 最新版本(可能刚创建)
     */
    WfDefVersion ensureInitVersion(Long definitionId);

    /**
     * 创建新版本：复制源版本内容，is_latest 自动切换
     * @param sourceVersionId 源版本ID(从哪个版本复制)
     * @param newVersionName 新版本备注名(可空)
     * @return 新版本ID
     */
    Long createNewVersionFrom(Long sourceVersionId, String newVersionName);

    /**
     * 从主表 definition + nodes 数据直接创建 V1 快照(新流程首次创建时用)
     */
    Long createInitialVersion(Long definitionId, String processXml, List<com.cyan.modules.workflow.entity.WorkflowNode> nodes);

    /** 保存版本快照：更新 flow_json（不改变 is_latest） */
    void saveVersionSnapshot(Long versionId, String flowJson);
}
