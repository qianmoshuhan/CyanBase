package com.cyan.tenant.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cyan.common.R;
import com.cyan.common.security.util.SecurityUtils;
import com.cyan.entity.SysUser;
import com.cyan.modules.sydyd.entity.ThreeBigItem;
import com.cyan.modules.sydyd.service.ThreeBigItemService;
import com.cyan.security.annotation.RequiresPermission;
import com.cyan.service.SysUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 三重一大 - 业务事项填报（租户侧）
 * <p>
 * 权限控制（阶段 1 简化版，后续阶段再细化角色粒度）：
 * - 默认：所有租户用户都能填报 + 看本部门的事项（submitDeptId = 当前登录人 deptId）
 * - 管理员角色：通过 isAdmin 参数绕过部门过滤，全量可见
 * - 部门领导：同理，通过部门归属控制
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/tenant/sydyd/item")
@RequiredArgsConstructor
public class ThreeBigItemController {

    private final ThreeBigItemService itemService;
    private final SysUserService sysUserService;

    /**
     * 分页列表
     *
     * @param isAdmin   是否管理员（true 则全量可见，默认 false）
     */
    @GetMapping("/list")
    public R<IPage<ThreeBigItem>> list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Integer itemStatus,
            @RequestParam(required = false) Integer itemType,
            @RequestParam(required = false) Long systemId,
            @RequestParam(required = false) String itemName,
            @RequestParam(defaultValue = "false") Boolean isAdmin) {

        // 取当前登录人信息（用于部门过滤）
        Long userId = SecurityUtils.getUserId();
        SysUser user = sysUserService.getById(userId);
        Long userDeptId = (user != null) ? user.getDeptId() : null;

        IPage<ThreeBigItem> page = itemService.queryPage(
                pageNum, pageSize, itemStatus, itemType, systemId, itemName,
                userDeptId, Boolean.TRUE.equals(isAdmin));
        return R.ok(page);
    }

    /** 详情 */
    @GetMapping("/{id}")
    public R<ThreeBigItem> getById(@PathVariable Long id) {
        return R.ok(itemService.getById(id));
    }

    /**
     * 新增事项（草稿）
     * <p>
     * 前端只需要传：listId / itemName / fundAmount / hostDept / ...
     * 其他（systemId / itemType / itemStatus / submitDeptId / applicant）Service 层自动补齐
     * </p>
     */
    @PostMapping
    @RequiresPermission("sydyd:item:add")
    public R<Long> createDraft(@RequestBody ThreeBigItem item) {
        item.setId(null);
        // 自动补填报人、填报部门
        Long userId = SecurityUtils.getUserId();
        SysUser user = sysUserService.getById(userId);
        if (user != null) {
            item.setSubmitDeptId(user.getDeptId());
            item.setApplicant(user.getNickName());
        }
        Long id = itemService.createDraft(item);
        return R.ok(id);
    }

    /** 保存草稿（编辑已存在的草稿） */
    @PutMapping
    @RequiresPermission("sydyd:item:edit")
    public R<Void> updateDraft(@RequestBody ThreeBigItem item) {
        return itemService.updateById(item) ? R.ok() : R.fail("更新失败");
    }

    /**
     * 提交（草稿 → 待酝酿 + 自动发起 workflow）
     * @param id            事项主键
     * @param definitionId  可选：指定用哪个流程定义；不传则按 businessType=threeBigItem 自动找已发布的
     */
    @PutMapping("/{id}/submit")
    @RequiresPermission("sydyd:item:edit")
    public R<Void> submit(@PathVariable Long id,
                          @RequestParam(required = false) Long definitionId) {
        itemService.submit(id, definitionId);
        return R.ok();
    }

    /** 回退（待酝酿/已驳回 → 草稿） */
    @PutMapping("/{id}/withdraw")
    @RequiresPermission("sydyd:item:edit")
    public R<Void> withdraw(@PathVariable Long id) {
        itemService.withdraw(id);
        return R.ok();
    }

    /**
     * 删除事项（级联清理关联的工作流实例 + 审批记录）
     */
    @DeleteMapping("/{id}")
    @RequiresPermission("sydyd:item:remove")
    public R<Void> delete(@PathVariable Long id) {
        // 直接调 deleteWithWorkflowClean，内部会先物理清 workflow 脏数据，再逻辑删业务
        return itemService.deleteWithWorkflowClean(id) ? R.ok() : R.fail("删除失败");
    }
}