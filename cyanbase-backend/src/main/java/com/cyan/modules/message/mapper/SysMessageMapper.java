package com.cyan.modules.message.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cyan.modules.message.entity.SysMessage;
import org.apache.ibatis.annotations.Mapper;

/**
 * 消息Mapper接口
 */
@Mapper
public interface SysMessageMapper extends BaseMapper<SysMessage> {
}
