package com.cyan.modules.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cyan.modules.workflow.entity.WorkflowInstance;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 流程实例Mapper
 */
@Mapper
public interface WorkflowInstanceMapper extends BaseMapper<WorkflowInstance> {

    /**
     * 物理删除（绕过 @TableLogic 逻辑删除）
     * 业务单据删除时级联清理工作流脏数据专用
     */
    @Delete("<script>DELETE FROM wf_process_instance WHERE id IN " +
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>")
    int deletePhysicalByIds(@Param("ids") List<Long> ids);

    /**
     * 按 businessKey 物理删除（级联清理用）
     */
    @Delete("DELETE FROM wf_process_instance WHERE business_key = #{businessKey}")
    int deletePhysicalByBusinessKey(@Param("businessKey") String businessKey);
}