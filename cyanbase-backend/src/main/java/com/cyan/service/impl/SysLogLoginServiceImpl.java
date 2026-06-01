package com.cyan.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cyan.entity.SysLogLogin;
import com.cyan.mapper.SysLogLoginMapper;
import com.cyan.service.SysLogLoginService;
import org.springframework.stereotype.Service;

@Service
public class SysLogLoginServiceImpl extends ServiceImpl<SysLogLoginMapper, SysLogLogin> implements SysLogLoginService {
}