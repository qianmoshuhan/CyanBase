package com.cyan.service.impl;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cyan.mapper.SysLogininforMapper;
import com.cyan.service.SysLogininforService;
import com.cyan.entity.SysLogininfor;
import org.springframework.stereotype.Service;
@Service
public class SysLogininforServiceImpl extends ServiceImpl<SysLogininforMapper, SysLogininfor> implements SysLogininforService {}
