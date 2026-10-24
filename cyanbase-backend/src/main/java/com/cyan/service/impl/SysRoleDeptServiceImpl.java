package com.cyan.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cyan.entity.SysRoleDept;
import com.cyan.mapper.SysRoleDeptMapper;
import com.cyan.service.SysRoleDeptService;
import org.springframework.stereotype.Service;

@Service
public class SysRoleDeptServiceImpl extends ServiceImpl<SysRoleDeptMapper, SysRoleDept>
        implements SysRoleDeptService {
}
