package com.cyan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cyan.entity.SysRoleDept;
import org.apache.ibatis.annotations.Mapper;

/**
 * 角色-部门关联 Mapper（数据范围 "自定义" 场景使用）
 */
@Mapper
public interface SysRoleDeptMapper extends BaseMapper<SysRoleDept> {
}