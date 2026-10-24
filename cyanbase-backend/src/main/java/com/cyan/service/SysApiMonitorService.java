package com.cyan.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.cyan.entity.SysApiMonitor;

public interface SysApiMonitorService extends IService<SysApiMonitor> {

    /**
     * 记录一次 API 调用 —— 按 (apiUrl + 日期) 聚合更新
     *
     * @param apiUrl   请求 URI
     * @param apiName  接口名称（可空，用 URI 兜底）
     * @param costMs   本次耗时（毫秒）
     * @param isError  是否错误（status >= 400 或有异常）
     */
    void recordApiCall(String apiUrl, String apiName, long costMs, boolean isError);
}