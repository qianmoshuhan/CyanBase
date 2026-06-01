package com.cyan.service.impl;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cyan.mapper.WfDefinitionMapper;
import com.cyan.service.WfDefinitionService;
import com.cyan.entity.WfDefinition;
import org.springframework.stereotype.Service;
@Service
public class WfDefinitionServiceImpl extends ServiceImpl<WfDefinitionMapper, WfDefinition> implements WfDefinitionService {}
