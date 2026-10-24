package com.cyan.modules.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cyan.modules.workflow.entity.WorkflowApprove;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 流程审批记录Mapper
 */
@Mapper
public interface WorkflowApproveMapper extends BaseMapper<WorkflowApprove> {

    /**
     * 按 instanceId 列表物理删除（绕过 @TableLogic 逻辑删除）
     * 业务单据删除时级联清理工作流脏数据专用
     */
    @Delete("<script>DELETE FROM wf_process_approve WHERE instance_id IN " +
            "<foreach collection='instanceIds' item='iid' open='(' separator=',' close=')'>#{iid}</foreach></script>")
    int deletePhysicalByInstanceIds(@Param("instanceIds") List<Long> instanceIds);
}