package com.cyan.modules.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cyan.modules.workflow.entity.WorkflowNode;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 流程节点Mapper
 */
@Mapper
public interface WorkflowNodeMapper extends BaseMapper<WorkflowNode> {

    /**
     * ⭐ 物理删除（绕开 @TableLogic 的软删）
     * 批量保存节点前必须先物理清掉旧数据，否则软删记录占着主键 id 会冲突
     */
    @Delete("DELETE FROM wf_process_node WHERE definition_id = #{definitionId}")
    int deleteByDefinitionIdPhysical(@Param("definitionId") Long definitionId);
}