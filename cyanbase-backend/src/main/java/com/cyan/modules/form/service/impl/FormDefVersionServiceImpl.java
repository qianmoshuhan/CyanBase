package com.cyan.modules.form.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cyan.common.exception.BusinessException;
import com.cyan.modules.form.entity.FormDefinition;
import com.cyan.modules.form.entity.FormDefVersion;
import com.cyan.modules.form.mapper.FormDefVersionMapper;
import com.cyan.modules.form.service.FormDefVersionService;
import com.cyan.modules.form.service.FormDefinitionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FormDefVersionServiceImpl extends com.baomidou.mybatisplus.extension.service.impl.ServiceImpl<FormDefVersionMapper, FormDefVersion>
        implements FormDefVersionService {

    private final FormDefinitionService formDefinitionService;

    @Override
    public List<FormDefVersion> listByDefinitionId(Long definitionId) {
        LambdaQueryWrapper<FormDefVersion> w = new LambdaQueryWrapper<>();
        w.eq(FormDefVersion::getDefinitionId, definitionId)
         .orderByDesc(FormDefVersion::getVersionNo);
        return this.list(w);
    }

    @Override
    public FormDefVersion getLatestByDefinitionId(Long definitionId) {
        LambdaQueryWrapper<FormDefVersion> w = new LambdaQueryWrapper<>();
        w.eq(FormDefVersion::getDefinitionId, definitionId)
         .eq(FormDefVersion::getIsLatest, 1)
         .last("LIMIT 1");
        return this.getOne(w);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FormDefVersion ensureInitVersion(Long definitionId) {
        // 已有版本 → 直接返回最新
        FormDefVersion latest = getLatestByDefinitionId(definitionId);
        if (latest != null) return latest;

        // 没有版本 → 从主表补 V1
        FormDefinition def = formDefinitionService.getById(definitionId);
        if (def == null) {
            throw new BusinessException("表单定义不存在");
        }
        Long newId = createInitialVersion(definitionId, def.getLayoutConfig());
        return this.getById(newId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createNewVersionFrom(Long sourceVersionId, String newVersionName) {
        FormDefVersion source = this.getById(sourceVersionId);
        if (source == null) {
            throw new BusinessException("源版本不存在");
        }

        // 1. 把同 definition 下所有 is_latest 置 0
        LambdaQueryWrapper<FormDefVersion> resetLatest = new LambdaQueryWrapper<>();
        resetLatest.eq(FormDefVersion::getDefinitionId, source.getDefinitionId())
                   .eq(FormDefVersion::getIsLatest, 1);
        FormDefVersion resetEntity = new FormDefVersion();
        resetEntity.setIsLatest(0);
        this.update(resetEntity, resetLatest);

        // 2. 计算新版本号 = 该 definition 下最大 versionNo + 1
        LambdaQueryWrapper<FormDefVersion> maxNoW = new LambdaQueryWrapper<>();
        maxNoW.eq(FormDefVersion::getDefinitionId, source.getDefinitionId())
              .orderByDesc(FormDefVersion::getVersionNo)
              .last("LIMIT 1");
        FormDefVersion maxNoVer = this.getOne(maxNoW, false);
        int newVersionNo = (maxNoVer == null ? 0 : maxNoVer.getVersionNo()) + 1;

        // 3. 创建新版本快照（复制 form_schema，null 兜底为 "[]"）
        FormDefVersion newVer = new FormDefVersion();
        newVer.setDefinitionId(source.getDefinitionId());
        newVer.setVersionNo(newVersionNo);
        newVer.setVersionName(newVersionName);
        newVer.setFormSchema(source.getFormSchema() != null ? source.getFormSchema() : "[]");
        newVer.setIsLatest(1);
        newVer.setTenantId(source.getTenantId());
        this.save(newVer);

        log.debug("[FormDefVersion] 创建新版本成功: definitionId={}, 旧版本=v{}, 新版本=v{}",
                source.getDefinitionId(), source.getVersionNo(), newVersionNo);
        return newVer.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createInitialVersion(Long definitionId, String formSchema) {
        FormDefinition def = formDefinitionService.getById(definitionId);
        if (def == null) {
            throw new BusinessException("表单定义不存在");
        }

        // 如果已有版本，不重复创建
        FormDefVersion existing = getLatestByDefinitionId(definitionId);
        if (existing != null) {
            return existing.getId();
        }

        // 先把同 definition 下所有 is_latest 置 0（防御性）
        LambdaQueryWrapper<FormDefVersion> resetLatest = new LambdaQueryWrapper<>();
        resetLatest.eq(FormDefVersion::getDefinitionId, definitionId);
        FormDefVersion resetEntity = new FormDefVersion();
        resetEntity.setIsLatest(0);
        this.update(resetEntity, resetLatest);

        FormDefVersion ver = new FormDefVersion();
        ver.setDefinitionId(definitionId);
        ver.setVersionNo(1);
        ver.setVersionName(null);
        // formSchema 可能为 null（新表单还没设计过），兜底为 "[]"
        ver.setFormSchema(formSchema != null ? formSchema : "[]");
        ver.setIsLatest(1);
        ver.setTenantId(def.getTenantId() != null ? def.getTenantId() : 0L);
        this.save(ver);

        log.debug("[FormDefVersion] 初始化 V1 版本: definitionId={}", definitionId);
        return ver.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveVersionSnapshot(Long versionId, String formSchema) {
        FormDefVersion ver = this.getById(versionId);
        if (ver == null) {
            throw new BusinessException("版本不存在");
        }
        ver.setFormSchema(formSchema);
        this.updateById(ver);

        log.debug("[FormDefVersion] 保存版本快照: versionId={}", versionId);
    }
}