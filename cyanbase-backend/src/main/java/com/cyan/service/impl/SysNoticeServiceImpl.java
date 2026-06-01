package com.cyan.service.impl;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cyan.mapper.SysNoticeMapper;
import com.cyan.service.SysNoticeService;
import com.cyan.entity.SysNotice;
import org.springframework.stereotype.Service;
@Service
public class SysNoticeServiceImpl extends ServiceImpl<SysNoticeMapper, SysNotice> implements SysNoticeService {
}
