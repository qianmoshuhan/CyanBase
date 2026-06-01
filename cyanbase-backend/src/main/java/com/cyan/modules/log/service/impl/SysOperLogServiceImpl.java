package com.cyan.modules.log.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cyan.entity.SysOperLog;
import com.cyan.mapper.SysOperLogMapper;
import com.cyan.modules.log.service.SysOperLogService;
import org.springframework.stereotype.Service;

/**
 * 操作日志Service实现类
 */
@Service
public class SysOperLogServiceImpl extends ServiceImpl<SysOperLogMapper, SysOperLog> implements SysOperLogService {
}
