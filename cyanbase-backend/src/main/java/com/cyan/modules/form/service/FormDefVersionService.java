package com.cyan.modules.form.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.cyan.modules.form.entity.FormDefVersion;

import java.util.List;

public interface FormDefVersionService extends IService<FormDefVersion> {

    /** 查询某表单定义的全部版本，按 versionNo 倒序 */
    List<FormDefVersion> listByDefinitionId(Long definitionId);

    /** 查询某表单定义的最新版本 */
    FormDefVersion getLatestByDefinitionId(Long definitionId);

    /**
     * 懒初始化：如果版本表没有任何记录，从主表自动补一条 V1 快照
     * @return 最新版本(可能刚创建)
     */
    FormDefVersion ensureInitVersion(Long definitionId);

    /**
     * 创建新版本：复制源版本内容，is_latest 自动切换
     * @param sourceVersionId 源版本ID(从哪个版本复制)
     * @param newVersionName 新版本备注名(可空)
     * @return 新版本ID
     */
    Long createNewVersionFrom(Long sourceVersionId, String newVersionName);

    /**
     * 从主表 definition 的 layoutConfig 直接创建 V1 快照(新表单首次创建时用)
     */
    Long createInitialVersion(Long definitionId, String formSchema);

    /** 保存版本快照：更新 form_schema（不改变 is_latest） */
    void saveVersionSnapshot(Long versionId, String formSchema);
}