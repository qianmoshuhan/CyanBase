package com.cyan.controller.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cyan.common.R;
import com.cyan.entity.SysPost;
import com.cyan.service.SysPostService;
import com.cyan.security.annotation.RequiresPermission;
import com.cyan.security.aspect.PermissionGuard;
import com.cyan.security.datascope.ResourceScope;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/system/post")
@RequiredArgsConstructor
public class SysPostController {

    private final SysPostService sysPostService;
    private final PermissionGuard permissionGuard;

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
        if (post == null) return R.fail("岗位不存在");
        // 读操作：L1 租户归属 + L2 DataScope
        permissionGuard.check(ResourceScope.of(post.getTenantId(), null, post.getCreateBy()));
        return R.ok(post);
    }

    @PostMapping
    @RequiresPermission("system:post:add")
    public R<?> add(@RequestBody SysPost post) {
        post.setPostId(null);
        sysPostService.save(post);
        return R.ok("新增成功");
    }

    @PutMapping
    @RequiresPermission("system:post:edit")
    public R<?> update(@RequestBody SysPost post) {
        SysPost exist = sysPostService.getById(post.getPostId());
        if (exist == null) return R.fail("岗位不存在");
        // 写操作：完整三层 L1 租户归属 + L2 DataScope + L3 system:post:edit
        permissionGuard.check(ResourceScope.of(exist.getTenantId(), null, exist.getCreateBy()),
                              "system:post:edit");
        sysPostService.updateById(post);
        return R.ok("更新成功");
    }

    @DeleteMapping("/{postId}")
    @RequiresPermission("system:post:remove")
    public R<?> delete(@PathVariable Long postId) {
        SysPost exist = sysPostService.getById(postId);
        if (exist == null) return R.fail("岗位不存在");
        // 写操作：完整三层校验
        permissionGuard.check(ResourceScope.of(exist.getTenantId(), null, exist.getCreateBy()),
                              "system:post:remove");
        sysPostService.removeById(postId);
        return R.ok("删除成功");
    }
}