package com.cyan.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cyan.entity.SysRole;
import com.cyan.mapper.SysRoleMapper;
import com.cyan.service.SysRoleService;
import org.springframework.stereotype.Service;

@Service
public class SysRoleServiceImpl extends ServiceImpl<SysRoleMapper, SysRole> implements SysRoleService {
}