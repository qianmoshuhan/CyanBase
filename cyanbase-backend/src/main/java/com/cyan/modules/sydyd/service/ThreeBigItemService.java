package com.cyan.modules.sydyd.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.cyan.modules.sydyd.entity.ThreeBigItem;

/**
 * 三重一大业务事项 Service
 * <p>
 * 阶段 1：基础 CRUD + 状态流转（草稿→待酝酿）
 * 阶段 2：从事项生成议题（关联 TopicService）
 * </p>
 */
public interface ThreeBigItemService extends IService<ThreeBigItem> {

    /**
     * 分页查询 — 业务筛选条件，数据权限由 L2 DataScope Handler 自动管控
     * <p>
     * Handler 通过 DEPT_COLUMN_MAP 识别 t_three_big_item 表的部门列是 submit_dept_id，
     * 按当前用户角色的 dataScope 配置（ALL/DEPT/DEPT_AND_CHILD/SELF/CUSTOM）自动追加过滤条件。
     * Service 层不再手动做部门过滤，避免与 Handler 双重追加。
     * </p>
     *
     * @param pageNum    页码
     * @param pageSize   页大小
     * @param itemStatus 事项状态筛选（可为 null）
     * @param itemType   事项类型筛选（可为 null）
     * @param systemId   所属制度筛选（可为 null）
     * @param itemName   事项名称模糊搜索（可为 null）
     */
    IPage<ThreeBigItem> queryPage(Integer pageNum, Integer pageSize,
                                  Integer itemStatus, Integer itemType,
                                  Long systemId, String itemName);

    /** 新增事项（草稿态） */
    Long createDraft(ThreeBigItem item);

    /**
     * 草稿提交 → 待酝酿 + 自动发起 workflow
     * @param id            事项主键
     * @param definitionId  可选：指定流程定义；不传则按 businessType=threeBigItem 自动匹配
     */
    void submit(Long id, Long definitionId);

    /** 事项回退 → 草稿 */
    void withdraw(Long id);

    /**
     * 删除事项（级联清理关联的工作流实例 + 审批记录，防止脏数据残留）
     */
    boolean deleteWithWorkflowClean(Long id);
}