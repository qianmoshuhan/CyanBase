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
     * 分页查询（带部门权限过滤）
     *
     * @param pageNum     页码
     * @param pageSize    页大小
     * @param itemStatus  事项状态筛选（可为 null）
     * @param itemType    事项类型筛选（可为 null）
     * @param systemId    所属制度筛选（可为 null）
     * @param itemName    事项名称模糊搜索（可为 null）
     * @param userDeptId  当前登录人所属部门ID —— 填报员只能看本部门；null 表示管理员全量
     * @param isAdmin     是否管理员（管理员看全量，否则只看自己部门 + 自己填的）
     */
    IPage<ThreeBigItem> queryPage(Integer pageNum, Integer pageSize,
                                  Integer itemStatus, Integer itemType,
                                  Long systemId, String itemName,
                                  Long userDeptId, boolean isAdmin);

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