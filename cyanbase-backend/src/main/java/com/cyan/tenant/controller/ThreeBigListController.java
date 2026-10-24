package com.cyan.tenant.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cyan.common.R;
import com.cyan.modules.sydyd.entity.ThreeBigList;
import com.cyan.modules.sydyd.service.ThreeBigListService;
import com.cyan.modules.sydyd.service.ThreeBigSystemService;
import com.cyan.security.annotation.RequiresPermission;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 三重一大 - 事项清单管理（租户侧）
 * <p>
 * 业务约束：清单必须绑定一个存在且启用的制度（在 Service.save 里校验）
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/tenant/sydyd/list")
@RequiredArgsConstructor
public class ThreeBigListController {

    private final ThreeBigListService listService;
    private final ThreeBigSystemService systemService;

    /** 分页列表 */
    @GetMapping("/list")
    public R<Page<ThreeBigList>> list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Long systemId,
            @RequestParam(required = false) Integer category,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String listName) {

        LambdaQueryWrapper<ThreeBigList> wrapper = new LambdaQueryWrapper<>();
        if (systemId != null)      wrapper.eq(ThreeBigList::getSystemId, systemId);
        if (category != null)      wrapper.eq(ThreeBigList::getCategory, category);
        if (status != null)        wrapper.eq(ThreeBigList::getStatus, status);
        if (listName != null && !listName.isEmpty()) {
            wrapper.like(ThreeBigList::getListName, listName);
        }
        wrapper.orderByAsc(ThreeBigList::getSortNum);

        Page<ThreeBigList> page = new Page<>(pageNum, pageSize);
        listService.page(page, wrapper);
        return R.ok(page);
    }

    /** 按制度ID查清单（事项填报时下拉选） */
    @GetMapping("/bySystem/{systemId}")
    public R<List<ThreeBigList>> bySystem(@PathVariable Long systemId) {
        return R.ok(listService.listBySystemId(systemId));
    }

    /** 按分类查清单 */
    @GetMapping("/byCategory")
    public R<List<ThreeBigList>> byCategory(@RequestParam Integer category) {
        return R.ok(listService.listByCategory(category));
    }

    /** 详情 */
    @GetMapping("/{id}")
    public R<ThreeBigList> getById(@PathVariable Long id) {
        return R.ok(listService.getById(id));
    }

    /** 新增（制度存在性/启用校验在 Service.save 里做） */
    @PostMapping
    @RequiresPermission("sydyd:list:add")
    public R<Long> add(@RequestBody ThreeBigList list) {
        list.setId(null);
        list.setStatus(0);
        listService.save(list);
        return R.ok(list.getId());
    }

    /** 更新 */
    @PutMapping
    @RequiresPermission("sydyd:list:edit")
    public R<Void> update(@RequestBody ThreeBigList list) {
        return listService.updateById(list) ? R.ok() : R.fail("更新失败");
    }

    /** 删除（Service.removeById 会校验清单下是否还有事项） */
    @DeleteMapping("/{id}")
    @RequiresPermission("sydyd:list:remove")
    public R<Void> delete(@PathVariable Long id) {
        return listService.removeById(id) ? R.ok() : R.fail("删除失败");
    }
}