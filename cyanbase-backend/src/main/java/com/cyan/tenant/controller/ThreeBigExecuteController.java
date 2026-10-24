package com.cyan.tenant.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cyan.common.R;
import com.cyan.modules.sydyd.entity.ThreeBigExecute;
import com.cyan.modules.sydyd.service.ThreeBigExecuteService;
import com.cyan.security.annotation.RequiresPermission;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 三重一大 - 实施落地管理（租户侧）
 */
@Slf4j
@RestController
@RequestMapping("/tenant/sydyd/execute")
@RequiredArgsConstructor
public class ThreeBigExecuteController {

    private final ThreeBigExecuteService executeService;

    /** 分页列表 */
    @GetMapping("/list")
    public R<IPage<ThreeBigExecute>> list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Integer executeStatus,
            @RequestParam(required = false) Long meetingId,
            @RequestParam(required = false) String itemName,
            @RequestParam(required = false) String executeDept) {
        return R.ok(executeService.queryPage(pageNum, pageSize, executeStatus, meetingId, itemName, executeDept));
    }

    /** 详情 */
    @GetMapping("/{id}")
    public R<ThreeBigExecute> getById(@PathVariable Long id) {
        return R.ok(executeService.getById(id));
    }

    /** 手动新增实施任务（给历史表决结果补录） */
    @PostMapping
    @RequiresPermission("sydyd:execute:add")
    public R<Long> create(@RequestBody ThreeBigExecute execute) {
        execute.setId(null);
        return R.ok(executeService.createExecute(execute));
    }

    /** 更新基本信息（执行部门、责任人、计划时间等） */
    @PutMapping
    @RequiresPermission("sydyd:execute:edit")
    public R<Void> update(@RequestBody ThreeBigExecute execute) {
        return executeService.updateById(execute) ? R.ok() : R.fail("更新失败");
    }

    /** 开始执行（状态 1→2） */
    @PutMapping("/{id}/start")
    @RequiresPermission("sydyd:execute:edit")
    public R<Void> start(@PathVariable Long id) {
        executeService.start(id);
        return R.ok();
    }

    /** 更新执行进度（核心填报动作） */
    @PutMapping("/{id}/progress")
    @RequiresPermission("sydyd:execute:edit")
    public R<Void> updateProgress(@PathVariable Long id,
                                  @RequestParam(required = false) Integer progress,
                                  @RequestParam(required = false) String progressDetail) {
        executeService.updateProgress(id, progress, progressDetail);
        return R.ok();
    }

    /** 完成执行（进度自动=100，记录实际完成时间） */
    @PutMapping("/{id}/finish")
    @RequiresPermission("sydyd:execute:edit")
    public R<Void> finish(@PathVariable Long id,
                          @RequestParam(required = false) String progressDetail,
                          @RequestParam(required = false) String evidenceFile) {
        executeService.finish(id, progressDetail, evidenceFile);
        return R.ok();
    }

    /** 归档（状态 3→5） */
    @PutMapping("/{id}/archive")
    @RequiresPermission("sydyd:execute:edit")
    public R<Void> archive(@PathVariable Long id) {
        executeService.archive(id);
        return R.ok();
    }

    /** 删除（仅待执行允许删除） */
    @DeleteMapping("/{id}")
    @RequiresPermission("sydyd:execute:remove")
    public R<Void> delete(@PathVariable Long id) {
        ThreeBigExecute e = executeService.getById(id);
        if (e == null) return R.fail("实施任务不存在");
        if (e.getExecuteStatus() != null && e.getExecuteStatus() != 1) {
            return R.fail("仅待执行状态允许删除");
        }
        return executeService.removeById(id) ? R.ok() : R.fail("删除失败");
    }
}