package com.cyan.service.impl;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cyan.mapper.SysStorageConfigMapper;
import com.cyan.service.SysStorageConfigService;
import com.cyan.entity.SysStorageConfig;
import org.springframework.stereotype.Service;
@Service
public class SysStorageConfigServiceImpl extends ServiceImpl<SysStorageConfigMapper, SysStorageConfig> implements SysStorageConfigService {}
