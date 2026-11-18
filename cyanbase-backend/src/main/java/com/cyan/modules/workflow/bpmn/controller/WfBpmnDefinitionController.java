package com.cyan.modules.workflow.bpmn.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cyan.common.R;
import com.cyan.common.exception.BusinessException;
import com.cyan.common.log.annotation.Log;
import com.cyan.common.log.enums.BusinessType;
import com.cyan.security.annotation.RequiresPermission;
import com.cyan.modules.workflow.bpmn.entity.WfBpmnDefinition;
import com.cyan.modules.workflow.bpmn.service.WfBpmnDefinitionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * BPMN 可视化流程定义 Controller
 * <p>第一阶段：只做编辑器的增删改查 + 发布标记，不接运行引擎</p>
 * <p>路径：/workflow/bpmn/definition（归属 workflow 子域，与现有 /workflow/definition 区分）</p>
 */
@RestController
@RequestMapping("/workflow/bpmn/definition")
@RequiredArgsConstructor
@Slf4j
public class WfBpmnDefinitionController {

    private final WfBpmnDefinitionService wfBpmnDefinitionService;

    /**
     * 分页列表
     */
    @GetMapping("/list")
    @RequiresPermission("workflow:bpmn:list")
    public R<Page<WfBpmnDefinition>> list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String processName,
            @RequestParam(required = false) String processKey,
            @RequestParam(required = false) Integer status) {
        Page<WfBpmnDefinition> page = wfBpmnDefinitionService.queryPageList(
                pageNum, pageSize, processName, processKey, status);
        return R.ok(page);
    }

    /**
     * 详情（含 bpmn_xml 完整内容）
     */
    @GetMapping("/{id}")
    @RequiresPermission("workflow:bpmn:list")
    public R<WfBpmnDefinition> getInfo(@PathVariable Long id) {
        WfBpmnDefinition def = wfBpmnDefinitionService.getById(id);
        if (def == null) return R.fail("BPMN 流程定义不存在");
        return R.ok(def);
    }

    /**
     * 仅拉取 BPMN XML（画布恢复专用，前端编辑模式用）
     */
    @GetMapping("/{id}/xml")
    @RequiresPermission("workflow:bpmn:list")
    public R<String> getXml(@PathVariable Long id) {
        WfBpmnDefinition def = wfBpmnDefinitionService.getById(id);
        if (def == null) return R.fail("BPMN 流程定义不存在");
        return R.ok(def.getBpmnXml());
    }

    /**
     * 新增
     */
    @PostMapping
    @Log(title = "BPMN流程定义", businessType = BusinessType.INSERT)
    @RequiresPermission("workflow:bpmn:edit")
    public R<Long> add(@RequestBody WfBpmnDefinition definition) {
        // 1. 防御：前端带了 id/processKey 时清掉，由后端自动生成
        definition.setId(null);
        definition.setProcessKey(null);
        // 2. 必填校验
        if (definition.getProcessName() == null || definition.getProcessName().trim().isEmpty()) {
            return R.fail("流程名称不能为空");
        }
        // 3. 自动生成流程 Key
        String generatedKey = wfBpmnDefinitionService.generateProcessKey();
        definition.setProcessKey(generatedKey);
        // 4. 默认值
        if (definition.getVersion() == null) definition.setVersion(1);
        if (definition.getStatus() == null) definition.setStatus(0); // 默认草稿
        wfBpmnDefinitionService.save(definition);
        return R.ok(definition.getId());
    }

    /**
     * 更新（含 bpmn_xml）
     */
    @PutMapping
    @Log(title = "BPMN流程定义", businessType = BusinessType.UPDATE)
    @RequiresPermission("workflow:bpmn:edit")
    public R<Void> edit(@RequestBody WfBpmnDefinition definition) {
        if (definition.getId() == null) {
            return R.fail("缺少 ID，无法更新");
        }
        WfBpmnDefinition exist = wfBpmnDefinitionService.getById(definition.getId());
        if (exist == null) {
            return R.fail("BPMN 流程定义不存在");
        }
        // 唯一性校验（排除自身）
        if (definition.getProcessKey() != null && definition.getVersion() != null) {
            if (wfBpmnDefinitionService.existsProcessKeyAndVersion(
                    definition.getProcessKey(), definition.getVersion(), definition.getId())) {
                return R.fail("流程 Key + 版本号已存在，请修改后重试");
            }
        }
        wfBpmnDefinitionService.updateById(definition);
        return R.ok();
    }

    /**
     * 逻辑删除
     */
    @DeleteMapping("/{id}")
    @Log(title = "BPMN流程定义", businessType = BusinessType.DELETE)
    @RequiresPermission("workflow:bpmn:delete")
    public R<Void> remove(@PathVariable Long id) {
        return wfBpmnDefinitionService.removeById(id) ? R.ok() : R.fail("删除失败");
    }

    /**
     * 发布（状态 0 → 1，本期只标记状态，不做运行引擎接入）
     */
    @PutMapping("/publish/{id}")
    @Log(title = "BPMN流程定义", businessType = BusinessType.UPDATE)
    @RequiresPermission("workflow:bpmn:publish")
    public R<Void> publish(@PathVariable Long id) {
        return wfBpmnDefinitionService.publish(id) ? R.ok() : R.fail("发布失败");
    }
}