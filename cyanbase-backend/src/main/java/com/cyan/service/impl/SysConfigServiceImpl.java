package com.cyan.service.impl;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cyan.mapper.SysConfigMapper;
import com.cyan.service.SysConfigService;
import com.cyan.entity.SysConfig;
import org.springframework.stereotype.Service;
@Service
public class SysConfigServiceImpl extends ServiceImpl<SysConfigMapper, SysConfig> implements SysConfigService {
}
