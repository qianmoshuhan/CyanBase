package com.cyan.service.impl;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cyan.mapper.SysFileMapper;
import com.cyan.service.SysFileService;
import com.cyan.entity.SysFile;
import org.springframework.stereotype.Service;
@Service
public class SysFileServiceImpl extends ServiceImpl<SysFileMapper, SysFile> implements SysFileService {}
