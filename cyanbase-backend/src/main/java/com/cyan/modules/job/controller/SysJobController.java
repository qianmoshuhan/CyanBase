package com.cyan.modules.job.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cyan.common.R;
import com.cyan.common.log.annotation.Log;
import com.cyan.common.log.enums.BusinessType;
import com.cyan.modules.job.entity.SysJob;
import com.cyan.modules.job.entity.SysJobLog;
import com.cyan.modules.job.service.SysJobService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 定时任务Controller
 */
@RestController
@RequestMapping("/system/job")
@RequiredArgsConstructor
public class SysJobController {

    private final SysJobService sysJobService;

    /**
     * 查询任务列表
     */
    @GetMapping("/list")
    public R<Page<SysJob>> list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            SysJob sysJob) {
        Page<SysJob> page = sysJobService.queryPageList(pageNum, pageSize, sysJob);
        return R.ok(page);
    }

    /**
     * 获取任务详细信息
     */
    @GetMapping("/{id}")
    public R<SysJob> getInfo(@PathVariable Long id) {
        SysJob job = sysJobService.getById(id);
        return job != null ? R.ok(job) : R.fail("任务不存在");
    }

    /**
     * 新增任务
     */
    @PostMapping
    @Log(title = "定时任务", businessType = BusinessType.INSERT)
    public R<Void> add(@Validated @RequestBody SysJob sysJob) {
        return sysJobService.addJob(sysJob) ? R.ok() : R.fail("新增失败");
    }

    /**
     * 修改任务
     */
    @PutMapping
    @Log(title = "定时任务", businessType = BusinessType.UPDATE)
    public R<Void> update(@Validated @RequestBody SysJob sysJob) {
        return sysJobService.updateJob(sysJob) ? R.ok() : R.fail("修改失败");
    }

    /**
     * 删除任务
     */
    @DeleteMapping("/{id}")
    @Log(title = "定时任务", businessType = BusinessType.DELETE)
    public R<Void> delete(@PathVariable Long id) {
        return sysJobService.deleteJob(id) ? R.ok() : R.fail("删除失败");
    }

    /**
     * 暂停任务
     */
    @PutMapping("/pause/{id}")
    @Log(title = "定时任务", businessType = BusinessType.UPDATE)
    public R<Void> pause(@PathVariable Long id) {
        return sysJobService.pauseJob(id) ? R.ok() : R.fail("暂停失败");
    }

    /**
     * 恢复任务
     */
    @PutMapping("/resume/{id}")
    @Log(title = "定时任务", businessType = BusinessType.UPDATE)
    public R<Void> resume(@PathVariable Long id) {
        return sysJobService.resumeJob(id) ? R.ok() : R.fail("恢复失败");
    }

    /**
     * 立即执行一次任务
     */
    @PutMapping("/execute/{id}")
    @Log(title = "定时任务", businessType = BusinessType.OTHER)
    public R<Void> executeOnce(@PathVariable Long id) {
        return sysJobService.executeOnce(id) ? R.ok() : R.fail("执行失败");
    }

    /**
     * 查询任务执行日志
     */
    @GetMapping("/log/list")
    public R<Page<SysJobLog>> logList(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Long jobId) {
        Page<SysJobLog> page = sysJobService.queryJobLogList(pageNum, pageSize, jobId);
        return R.ok(page);
    }
}
