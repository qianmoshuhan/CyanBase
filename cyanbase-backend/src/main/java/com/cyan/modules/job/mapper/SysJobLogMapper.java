package com.cyan.modules.job.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cyan.modules.job.entity.SysJobLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 定时任务日志Mapper接口
 */
@Mapper
public interface SysJobLogMapper extends BaseMapper<SysJobLog> {
}
