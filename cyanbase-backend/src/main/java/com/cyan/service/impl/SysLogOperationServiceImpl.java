package com.cyan.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cyan.entity.SysLogOperation;
import com.cyan.mapper.SysLogOperationMapper;
import com.cyan.service.SysLogOperationService;
import org.springframework.stereotype.Service;

@Service
public class SysLogOperationServiceImpl extends ServiceImpl<SysLogOperationMapper, SysLogOperation> implements SysLogOperationService {
}