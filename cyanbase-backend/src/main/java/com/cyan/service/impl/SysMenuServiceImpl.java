package com.cyan.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cyan.entity.SysMenu;
import com.cyan.mapper.SysMenuMapper;
import com.cyan.service.SysMenuService;
import org.springframework.stereotype.Service;

@Service
public class SysMenuServiceImpl extends ServiceImpl<SysMenuMapper, SysMenu> implements SysMenuService {
}