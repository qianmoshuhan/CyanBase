package com.cyan.modules.sydyd.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cyan.modules.sydyd.entity.ThreeBigSystem;
import org.apache.ibatis.annotations.Mapper;

/**
 * 三重一大制度 Mapper
 * <p>MP 自动追加 tenant_id 拦截，无需任何自定义</p>
 */
@Mapper
public interface ThreeBigSystemMapper extends BaseMapper<ThreeBigSystem> {
}