package com.cyan.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cyan.entity.SysTenant;
import com.cyan.mapper.SysTenantMapper;
import com.cyan.service.SysTenantService;
import org.springframework.stereotype.Service;

@Service
public class SysTenantServiceImpl extends ServiceImpl<SysTenantMapper, SysTenant> implements SysTenantService {
}