package com.cyan.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cyan.entity.SysTenantMenu;
import com.cyan.mapper.SysTenantMenuMapper;
import com.cyan.service.SysTenantMenuService;
import org.springframework.stereotype.Service;

@Service
public class SysTenantMenuServiceImpl extends ServiceImpl<SysTenantMenuMapper, SysTenantMenu>
        implements SysTenantMenuService {
}
