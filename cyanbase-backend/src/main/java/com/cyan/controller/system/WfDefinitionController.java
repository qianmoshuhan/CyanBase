package com.cyan.controller.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cyan.common.R;
import com.cyan.entity.WfDefinition;
import com.cyan.service.WfDefinitionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/system/wf/definition")
@RequiredArgsConstructor
public class WfDefinitionController {
    private final WfDefinitionService wfDefinitionService;

    @GetMapping("/list")
    public R<?> list(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String flowName,
            @RequestParam(required = false) String status) {
        LambdaQueryWrapper<WfDefinition> wrapper = new LambdaQueryWrapper<>();
        if (flowName != null && !flowName.isEmpty()) wrapper.like(WfDefinition::getFlowName, flowName);
        if (status != null && !status.isEmpty()) wrapper.eq(WfDefinition::getStatus, status);
        wrapper.orderByDesc(WfDefinition::getId);
        return R.ok(wfDefinitionService.page(new Page<>(current, size), wrapper));
    }

    @GetMapping("/{id}")
    public R<?> getById(@PathVariable Long id) {
        return R.ok(wfDefinitionService.getById(id));
    }

    @PostMapping
    public R<?> add(@RequestBody WfDefinition definition) {
        wfDefinitionService.save(definition);
        return R.ok("新增成功");
    }

    @PutMapping
    public R<?> update(@RequestBody WfDefinition definition) {
        wfDefinitionService.updateById(definition);
        return R.ok("更新成功");
    }

    @DeleteMapping("/{id}")
    public R<?> delete(@PathVariable Long id) {
        wfDefinitionService.removeById(id);
        return R.ok("删除成功");
    }
}
