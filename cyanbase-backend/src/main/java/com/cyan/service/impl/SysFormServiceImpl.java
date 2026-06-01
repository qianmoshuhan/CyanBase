package com.cyan.service.impl;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cyan.mapper.SysFormMapper;
import com.cyan.service.SysFormService;
import com.cyan.entity.SysForm;
import org.springframework.stereotype.Service;
@Service
public class SysFormServiceImpl extends ServiceImpl<SysFormMapper, SysForm> implements SysFormService {}