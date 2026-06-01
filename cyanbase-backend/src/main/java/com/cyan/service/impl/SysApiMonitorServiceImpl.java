package com.cyan.service.impl;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cyan.mapper.SysApiMonitorMapper;
import com.cyan.service.SysApiMonitorService;
import com.cyan.entity.SysApiMonitor;
import org.springframework.stereotype.Service;
@Service
public class SysApiMonitorServiceImpl extends ServiceImpl<SysApiMonitorMapper, SysApiMonitor> implements SysApiMonitorService {}
