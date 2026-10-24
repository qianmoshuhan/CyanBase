package com.cyan.modules.workflow.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.cyan.modules.workflow.entity.FormProcessBinding;

import java.util.List;
import java.util.Collection;
import java.util.Map;

/**
 * 表单 ↔ 流程定义 多对多绑定 Service
 */
public interface FormProcessBindingService extends IService<FormProcessBinding> {

    /**
     * 分页查询绑定列表（带 formName / processName 关联显示）
     */
    Page<FormProcessBinding> pageList(Integer pageNum, Integer pageSize,
                                      Long definitionId, Long processDefinitionId,
                                      String businessType, Integer status);

    /**
     * 查询某个表单的所有启用绑定（按 priority DESC）
     */
    List<FormProcessBinding> listByDefinitionId(Long definitionId);

    /**
     * ⭐ 路由匹配：根据表单 + 用户信息，匹配出应该启动的流程定义 ID
     * <p>
     * 匹配优先级（从高到低）：
     * <pre>
     * 1. dept_path 包含当前用户部门（含子部门路径匹配）
     * 2. dept_id 精确匹配当前用户部门
     * 3. business_type 匹配
     * 4. 以上都空（兜底默认分支）
     * </pre>
     * 多个命中时按 priority DESC 取最高优先级的那条。
     * </p>
     *
     * @param definitionId 表单定义 ID（必填）
     * @param userId       当前用户 ID（选填，null 时用 SecurityUtils 取）
     * @return 匹配到的 processDefinitionId；没有匹配返回 null
     */
    Long matchProcess(Long definitionId, Long userId);

    /**
     * 校验同一表单 + 同一流程是否已绑定（排除指定 id 自身）
     */
    boolean existsBinding(Long definitionId, Long processDefinitionId, Long excludeId);

    /**
     * 批量判断一组表单 ID 是否有至少一个 status=1 的启用 binding
     * <p>
     * 给 Phase 3.2 /form/definition/my-list 用——一次 DB 查询搞定，避免 N+1。
     * </p>
     *
     * @param definitionIds 表单定义 ID 集合
     * @return Map<definitionId, true/false>
     */
    Map<Long, Boolean> existsBindingForFormIds(Collection<Long> definitionIds);
}