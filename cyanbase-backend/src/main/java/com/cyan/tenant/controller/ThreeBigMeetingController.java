package com.cyan.tenant.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cyan.common.R;
import com.cyan.modules.sydyd.entity.ThreeBigMeeting;
import com.cyan.modules.sydyd.entity.ThreeBigMeetingTopic;
import com.cyan.modules.sydyd.service.ThreeBigMeetingService;
import com.cyan.security.annotation.RequiresPermission;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 三重一大 - 会议管理（租户侧）
 */
@Slf4j
@RestController
@RequestMapping("/tenant/sydyd/meeting")
@RequiredArgsConstructor
public class ThreeBigMeetingController {

    private final ThreeBigMeetingService meetingService;

    /** 分页列表 */
    @GetMapping("/list")
    public R<IPage<ThreeBigMeeting>> list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Integer meetingType,
            @RequestParam(required = false) Integer meetingStatus,
            @RequestParam(required = false) String meetingName) {
        return R.ok(meetingService.queryPage(pageNum, pageSize, meetingType, meetingStatus, meetingName));
    }

    /** 详情 */
    @GetMapping("/{id}")
    public R<ThreeBigMeeting> getById(@PathVariable Long id) {
        return R.ok(meetingService.getById(id));
    }

    /** 查询会议下的全部议题 */
    @GetMapping("/{id}/topics")
    public R<List<ThreeBigMeetingTopic>> listTopics(@PathVariable Long id) {
        return R.ok(meetingService.listMeetingTopics(id));
    }

    /** 新建会议 */
    @PostMapping
    @RequiresPermission("sydyd:meeting:add")
    public R<Long> create(@RequestBody ThreeBigMeeting meeting) {
        meeting.setId(null);
        return R.ok(meetingService.createMeeting(meeting));
    }

    /** 更新会议信息 */
    @PutMapping
    @RequiresPermission("sydyd:meeting:edit")
    public R<Void> update(@RequestBody ThreeBigMeeting meeting) {
        return meetingService.updateById(meeting) ? R.ok() : R.fail("更新失败");
    }

    /** 删除会议（仅待召开允许删除） */
    @DeleteMapping("/{id}")
    @RequiresPermission("sydyd:meeting:remove")
    public R<Void> delete(@PathVariable Long id) {
        ThreeBigMeeting m = meetingService.getById(id);
        if (m == null) return R.fail("会议不存在");
        if (m.getMeetingStatus() != null && m.getMeetingStatus() != 1) {
            return R.fail("仅待召开状态允许删除");
        }
        return meetingService.removeById(id) ? R.ok() : R.fail("删除失败");
    }

    /** 批量把议题加入会议 */
    @PostMapping("/{id}/topics")
    @RequiresPermission("sydyd:meeting:add")
    public R<Void> addTopics(@PathVariable Long id, @RequestBody List<Long> topicIds) {
        meetingService.addTopicsToMeeting(id, topicIds);
        return R.ok();
    }

    /** 会议开始 */
    @PutMapping("/{id}/start")
    @RequiresPermission("sydyd:meeting:edit")
    public R<Void> start(@PathVariable Long id) {
        meetingService.start(id);
        return R.ok();
    }

    /** 会议结束 */
    @PutMapping("/{id}/finish")
    @RequiresPermission("sydyd:meeting:edit")
    public R<Void> finish(@PathVariable Long id) {
        meetingService.finish(id);
        return R.ok();
    }

    /** 批量表决（会议结束后调用） */
    @PostMapping("/{id}/vote")
    @RequiresPermission("sydyd:meeting:edit")
    public R<Void> vote(@PathVariable Long id, @RequestBody List<ThreeBigMeetingTopic> votes) {
        meetingService.batchVote(id, votes);
        return R.ok();
    }

    /** 归档 */
    @PutMapping("/{id}/archive")
    @RequiresPermission("sydyd:meeting:edit")
    public R<Void> archive(@PathVariable Long id) {
        meetingService.archive(id);
        return R.ok();
    }
}