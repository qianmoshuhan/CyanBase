package com.cyan.controller.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cyan.common.R;
import com.cyan.entity.SysFile;
import com.cyan.service.SysFileService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/system/file")
@RequiredArgsConstructor
public class SysFileController {
    private final SysFileService sysFileService;

    @GetMapping("/list")
    public R<?> list(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String fileName) {
        LambdaQueryWrapper<SysFile> wrapper = new LambdaQueryWrapper<>();
        if (fileName != null && !fileName.isEmpty()) wrapper.like(SysFile::getFileName, fileName);
        wrapper.orderByDesc(SysFile::getFileId);
        return R.ok(sysFileService.page(new Page<>(current, size), wrapper));
    }

    @GetMapping("/{fileId}")
    public R<?> getById(@PathVariable Long fileId) {
        return R.ok(sysFileService.getById(fileId));
    }

    @PostMapping
    public R<?> add(@RequestBody SysFile file) {
        sysFileService.save(file);
        return R.ok("上传成功");
    }

    @DeleteMapping("/{fileId}")
    public R<?> delete(@PathVariable Long fileId) {
        sysFileService.removeById(fileId);
        return R.ok("删除成功");
    }
}
