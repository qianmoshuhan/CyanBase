package com.cyan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cyan.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {
}