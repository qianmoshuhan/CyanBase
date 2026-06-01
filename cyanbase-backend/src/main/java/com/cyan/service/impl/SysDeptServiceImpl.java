package com.cyan.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cyan.entity.SysDept;
import com.cyan.mapper.SysDeptMapper;
import com.cyan.service.SysDeptService;
import org.springframework.stereotype.Service;

@Service
public class SysDeptServiceImpl extends ServiceImpl<SysDeptMapper, SysDept> implements SysDeptService {
}