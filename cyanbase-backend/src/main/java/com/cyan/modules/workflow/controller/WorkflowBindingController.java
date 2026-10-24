package com.cyan.modules.workflow.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cyan.common.R;
import com.cyan.common.exception.BusinessException;
import com.cyan.common.log.annotation.Log;
import com.cyan.common.log.enums.BusinessType;
import com.cyan.security.annotation.RequiresPermission;
import com.cyan.common.security.util.SecurityUtils;
import com.cyan.modules.workflow.entity.FormProcessBinding;
import com.cyan.modules.workflow.service.FormProcessBindingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 表单 ↔ 流程定义 多对多绑定 Controller
 */
@Slf4j
@RestController
@RequestMapping("/workflow/binding")
@RequiredArgsConstructor
public class WorkflowBindingController {

    private final FormProcessBindingService bindingService;

    // ═══════════════════════════════════════════════════════════
    // 标准 CRUD
    // ═══════════════════════════════════════════════════════════

    /**
     * 分页查询绑定列表
     */
    @GetMapping("/list")
    @RequiresPermission("workflow:binding:query")
    public R<Page<FormProcessBinding>> list(
            @RequestParam(defaultValue = "1")  Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Long definitionId,
            @RequestParam(required = false) Long processDefinitionId,
            @RequestParam(required = false) String businessType,
            @RequestParam(required = false) Integer status) {
        return R.ok(bindingService.pageList(pageNum, pageSize, definitionId, processDefinitionId, businessType, status));
    }

    /**
     * 查询某个表单的所有启用绑定
     */
    @GetMapping("/by-form/{definitionId}")
    public R<List<FormProcessBinding>> listByForm(@PathVariable Long definitionId) {
        return R.ok(bindingService.listByDefinitionId(definitionId));
    }

    /**
     * 绑定详情
     */
    @GetMapping("/{id}")
    @RequiresPermission("workflow:binding:query")
    public R<FormProcessBinding> getById(@PathVariable Long id) {
        return R.ok(bindingService.getById(id));
    }

    /**
     * 新增绑定
     */
    @PostMapping
    @RequiresPermission("workflow:binding:add")
    @Log(title = "表单流程绑定", businessType = BusinessType.INSERT)
    public R<?> add(@RequestBody FormProcessBinding binding) {
        // 防御：主键冲突（遵守全局约束 #1）
        binding.setId(null);
        // 校验：同一表单 + 同一流程不能重复绑定
        if (bindingService.existsBinding(binding.getDefinitionId(), binding.getProcessDefinitionId(), null)) {
            throw new BusinessException("该表单与该流程已绑定，勿重复");
        }
        bindingService.save(binding);
        return R.ok(binding.getId());
    }

    /**
     * 更新绑定（只允许改路由规则、状态、优先级）
     */
    @PutMapping
    @RequiresPermission("workflow:binding:edit")
    @Log(title = "表单流程绑定", businessType = BusinessType.UPDATE)
    public R<?> update(@RequestBody FormProcessBinding binding) {
        if (binding.getId() == null) throw new BusinessException("id 不能为空");
        FormProcessBinding exists = bindingService.getById(binding.getId());
        if (exists == null) throw new BusinessException("绑定不存在 id=" + binding.getId());

        // 防御：如果改了 definitionId / processDefinitionId，也要校验重复
        if (!binding.getDefinitionId().equals(exists.getDefinitionId())
                || !binding.getProcessDefinitionId().equals(exists.getProcessDefinitionId())) {
            if (bindingService.existsBinding(binding.getDefinitionId(), binding.getProcessDefinitionId(), binding.getId())) {
                throw new BusinessException("该表单与该流程已绑定，勿重复");
            }
        }
        bindingService.updateById(binding);
        return R.ok("更新成功");
    }

    /**
     * 删除绑定
     */
    @DeleteMapping("/{id}")
    @RequiresPermission("workflow:binding:delete")
    @Log(title = "表单流程绑定", businessType = BusinessType.DELETE)
    public R<?> delete(@PathVariable Long id) {
        if (!bindingService.removeById(id)) throw new BusinessException("删除失败");
        return R.ok("删除成功");
    }

    // ═══════════════════════════════════════════════════════════
    // ⭐ Phase 1.6 路由匹配接口（核心！后续 Phase 3.3 用）
    // ═══════════════════════════════════════════════════════════

    /**
     * 路由匹配：根据表单定义 ID + 当前用户，匹配出应该启动的流程定义 ID
     * <p>
     * 前端调 / 后端 WorkflowStartServiceImpl 调都用这个。
     * 返回格式：{ matched: true, processDefinitionId: xxx, bindingId: yyy, reason: "dept_path命中" }
     * </p>
     */
    @GetMapping("/match")
    public R<Object> match(
            @RequestParam Long definitionId,
            @RequestParam(required = false) Long userId) {
        Long actualUserId = (userId != null) ? userId : SecurityUtils.getUserId();
        Long processDefId = bindingService.matchProcess(definitionId, actualUserId);

        java.util.Map<String, Object> result = new java.util.HashMap<>();
        if (processDefId != null) {
            result.put("matched", true);
            result.put("processDefinitionId", processDefId);
        } else {
            result.put("matched", false);
            result.put("processDefinitionId", null);
        }
        return R.ok(result);
    }
}