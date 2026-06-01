package com.cyan.modules.message.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cyan.modules.message.entity.SysMessageUser;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户消息关联Mapper接口
 */
@Mapper
public interface SysMessageUserMapper extends BaseMapper<SysMessageUser> {
}
