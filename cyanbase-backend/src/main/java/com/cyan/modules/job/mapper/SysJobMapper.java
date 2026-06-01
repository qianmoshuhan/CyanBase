package com.cyan.modules.job.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cyan.modules.job.entity.SysJob;
import org.apache.ibatis.annotations.Mapper;

/**
 * 定时任务Mapper接口
 */
@Mapper
public interface SysJobMapper extends BaseMapper<SysJob> {
}
