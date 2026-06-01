package com.cyan.service.impl;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cyan.mapper.GenTableMapper;
import com.cyan.service.GenTableService;
import com.cyan.entity.GenTable;
import org.springframework.stereotype.Service;
@Service
public class GenTableServiceImpl extends ServiceImpl<GenTableMapper, GenTable> implements GenTableService {}
