package com.cyan.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cyan.entity.SysApiMonitor;
import com.cyan.mapper.SysApiMonitorMapper;
import com.cyan.service.SysApiMonitorService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Slf4j
@Service
public class SysApiMonitorServiceImpl extends ServiceImpl<SysApiMonitorMapper, SysApiMonitor>
        implements SysApiMonitorService {

    @Override
    public void recordApiCall(String apiUrl, String apiName, long costMs, boolean isError) {
        try {
            LocalDate today = LocalDate.now();
            // ① 查今天这条 API 的聚合记录
            LambdaQueryWrapper<SysApiMonitor> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(SysApiMonitor::getApiUrl, apiUrl)
                   .eq(SysApiMonitor::getCreateDate, today);
            SysApiMonitor existing = this.getOne(wrapper);

            if (existing == null) {
                // ② 今天第一次出现 → 插入
                SysApiMonitor monitor = new SysApiMonitor();
                monitor.setApiName(apiName != null ? apiName : apiUrl);
                monitor.setApiUrl(apiUrl);
                monitor.setReqCount(1L);
                monitor.setMaxTime(costMs);
                monitor.setAvgTime(costMs);
                monitor.setErrorCount(isError ? 1L : 0L);
                monitor.setCreateDate(today);
                this.save(monitor);
            } else {
                // ③ 已存在 → 聚合更新
                long oldCount = existing.getReqCount();
                long newCount = oldCount + 1;
                long oldAvg = existing.getAvgTime() != null ? existing.getAvgTime() : 0L;
                // 加权平均：(oldAvg * oldCount + costMs) / newCount
                long newAvg = (oldAvg * oldCount + costMs) / newCount;
                long newMax = Math.max(existing.getMaxTime() != null ? existing.getMaxTime() : 0L, costMs);
                long newErr = (existing.getErrorCount() != null ? existing.getErrorCount() : 0L)
                        + (isError ? 1L : 0L);

                existing.setReqCount(newCount);
                existing.setAvgTime(newAvg);
                existing.setMaxTime(newMax);
                existing.setErrorCount(newErr);
                this.updateById(existing);
            }
        } catch (Exception e) {
            log.warn("API 监控记录失败: url={}, err={}", apiUrl, e.getMessage());
        }
    }
}