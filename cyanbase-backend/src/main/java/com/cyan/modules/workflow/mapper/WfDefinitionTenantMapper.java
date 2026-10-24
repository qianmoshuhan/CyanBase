package com.cyan.modules.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cyan.modules.workflow.entity.WfDefinitionTenant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 工作流定义→租户分发 Mapper
 */
@Mapper
public interface WfDefinitionTenantMapper extends BaseMapper<WfDefinitionTenant> {

    /** 根据 definitionId 查询所有有效分发记录（status=1） */
    @Select("SELECT * FROM wf_definition_tenant WHERE definition_id = #{defId} AND status = 1")
    List<WfDefinitionTenant> selectActiveByDefinitionId(@Param("defId") Long defId);
}
