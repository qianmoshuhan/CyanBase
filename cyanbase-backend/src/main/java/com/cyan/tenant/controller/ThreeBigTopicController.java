package com.cyan.tenant.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cyan.common.R;
import com.cyan.common.security.util.SecurityUtils;
import com.cyan.modules.sydyd.entity.ThreeBigTopic;
import com.cyan.modules.sydyd.service.ThreeBigTopicService;
import com.cyan.security.annotation.RequiresPermission;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 三重一大 - 议题管理（租户侧）
 */
@Slf4j
@RestController
@RequestMapping("/tenant/sydyd/topic")
@RequiredArgsConstructor
public class ThreeBigTopicController {

    private final ThreeBigTopicService topicService;

    /** 从业务事项一键生成议题（核心入口） */
    @PostMapping("/generate/{itemId}")
    @RequiresPermission("sydyd:topic:add")
    public R<Long> generate(@PathVariable Long itemId) {
        return R.ok(topicService.generateFromItem(itemId));
    }

    /** 批量生成议题 */
    @PostMapping("/batchGenerate")
    @RequiresPermission("sydyd:topic:add")
    public R<List<Long>> batchGenerate(@RequestBody List<Long> itemIds) {
        return R.ok(topicService.batchGenerateFromItems(itemIds));
    }

    /** 分页查询 */
    @GetMapping("/list")
    public R<IPage<ThreeBigTopic>> list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Integer topicStatus,
            @RequestParam(required = false) Integer itemType,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean preCheckCompleted) {
        return R.ok(topicService.queryPage(pageNum, pageSize, topicStatus, itemType, keyword, preCheckCompleted));
    }

    /** 详情 */
    @GetMapping("/{id}")
    public R<ThreeBigTopic> getById(@PathVariable Long id) {
        return R.ok(topicService.getById(id));
    }

    /** 手动更新议题信息 */
    @PutMapping
    @RequiresPermission("sydyd:topic:edit")
    public R<Void> update(@RequestBody ThreeBigTopic topic) {
        return topicService.updateById(topic) ? R.ok() : R.fail("更新失败");
    }

    /** 前置把关通过 */
    @PutMapping("/{id}/preCheckPass")
    @RequiresPermission("sydyd:topic:edit")
    public R<Void> preCheckPass(@PathVariable Long id,
                                @RequestParam(required = false) String opinion) {
        String operator = SecurityUtils.getUsername();
        topicService.preCheckPass(id, opinion, operator);
        return R.ok();
    }

    /** 前置把关打回 */
    @PutMapping("/{id}/preCheckReject")
    public R<Void> preCheckReject(@PathVariable Long id,
                                  @RequestParam(required = false) String opinion) {
        String operator = SecurityUtils.getUsername();
        topicService.preCheckReject(id, opinion, operator);
        return R.ok();
    }

    /** 删除（仅待前置把关允许删除） */
    @DeleteMapping("/{id}")
    @RequiresPermission("sydyd:topic:remove")
    public R<Void> delete(@PathVariable Long id) {
        ThreeBigTopic topic = topicService.getById(id);
        if (topic == null) return R.fail("议题不存在");
        if (topic.getTopicStatus() != null && topic.getTopicStatus() != 1) {
            return R.fail("仅待前置把关状态允许删除");
        }
        return topicService.removeById(id) ? R.ok() : R.fail("删除失败");
    }
}