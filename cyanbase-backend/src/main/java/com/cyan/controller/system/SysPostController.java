package com.cyan.controller.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cyan.common.R;
import com.cyan.entity.SysPost;
import com.cyan.service.SysPostService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/system/post")
@RequiredArgsConstructor
public class SysPostController {

    private final SysPostService sysPostService;

    @GetMapping("/list")
    public R<?> list(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String postCode,
            @RequestParam(required = false) String postName,
            @RequestParam(required = false) String status) {
        LambdaQueryWrapper<SysPost> wrapper = new LambdaQueryWrapper<>();
        if (postCode != null && !postCode.isEmpty()) {
            wrapper.like(SysPost::getPostCode, postCode);
        }
        if (postName != null && !postName.isEmpty()) {
            wrapper.like(SysPost::getPostName, postName);
        }
        if (status != null && !status.isEmpty()) {
            wrapper.eq(SysPost::getStatus, status);
        }
        wrapper.orderByAsc(SysPost::getPostSort);
        Page<SysPost> page = new Page<>(current, size);
        Page<SysPost> result = sysPostService.page(page, wrapper);
        return R.ok(result);
    }

    @GetMapping("/{postId}")
    public R<?> getById(@PathVariable Long postId) {
        SysPost post = sysPostService.getById(postId);
        return R.ok(post);
    }

    @PostMapping
    public R<?> add(@RequestBody SysPost post) {
        sysPostService.save(post);
        return R.ok("新增成功");
    }

    @PutMapping
    public R<?> update(@RequestBody SysPost post) {
        sysPostService.updateById(post);
        return R.ok("更新成功");
    }

    @DeleteMapping("/{postId}")
    public R<?> delete(@PathVariable Long postId) {
        sysPostService.removeById(postId);
        return R.ok("删除成功");
    }
}