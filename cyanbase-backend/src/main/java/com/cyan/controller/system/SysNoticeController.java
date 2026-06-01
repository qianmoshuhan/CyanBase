package com.cyan.controller.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cyan.common.R;
import com.cyan.entity.SysNotice;
import com.cyan.service.SysNoticeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/system/notice")
@RequiredArgsConstructor
public class SysNoticeController {
    private final SysNoticeService sysNoticeService;

    @GetMapping("/list")
    public R<?> list(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String noticeTitle,
            @RequestParam(required = false) String noticeType,
            @RequestParam(required = false) String status) {
        LambdaQueryWrapper<SysNotice> wrapper = new LambdaQueryWrapper<>();
        if (noticeTitle != null && !noticeTitle.isEmpty()) wrapper.like(SysNotice::getNoticeTitle, noticeTitle);
        if (noticeType != null && !noticeType.isEmpty()) wrapper.eq(SysNotice::getNoticeType, noticeType);
        if (status != null && !status.isEmpty()) wrapper.eq(SysNotice::getStatus, status);
        wrapper.orderByDesc(SysNotice::getNoticeId);
        return R.ok(sysNoticeService.page(new Page<>(current, size), wrapper));
    }

    @GetMapping("/{noticeId}")
    public R<?> getById(@PathVariable Long noticeId) {
        return R.ok(sysNoticeService.getById(noticeId));
    }

    @PostMapping
    public R<?> add(@RequestBody SysNotice notice) {
        sysNoticeService.save(notice);
        return R.ok("新增成功");
    }

    @PutMapping
    public R<?> update(@RequestBody SysNotice notice) {
        sysNoticeService.updateById(notice);
        return R.ok("更新成功");
    }

    @DeleteMapping("/{noticeId}")
    public R<?> delete(@PathVariable Long noticeId) {
        sysNoticeService.removeById(noticeId);
        return R.ok("删除成功");
    }
}
