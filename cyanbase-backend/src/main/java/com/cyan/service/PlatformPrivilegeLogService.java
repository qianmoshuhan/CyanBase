package com.cyan.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.cyan.entity.PlatformPrivilegeLog;

/**
 * 平台主体特权操作独立审计日志 Service。
 * <p>
 * 所有平台主体的跨租户操作（切换活跃租户、scope 分配/撤销、强制下线等）统一写此日志表，
 * 与 sys_oper_log / sys_log_login 完全隔离，便于独立审计检索。
 * </p>
 */
public interface PlatformPrivilegeLogService extends IService<PlatformPrivilegeLog> {

    /**
     * 快速记录一条审计日志（静态语义：不需要提前 new 对象）
     */
    void audit(Long principalUserId, String principalUserName, String actionType,
               Long targetTenantId, String targetTenantName, String detail, String ipaddr);
}
