package com.cyan.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cyan.entity.PlatformPrivilegeLog;
import com.cyan.mapper.PlatformPrivilegeLogMapper;
import com.cyan.service.PlatformPrivilegeLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
public class PlatformPrivilegeLogServiceImpl
        extends ServiceImpl<PlatformPrivilegeLogMapper, PlatformPrivilegeLog>
        implements PlatformPrivilegeLogService {

    @Override
    public void audit(Long principalUserId, String principalUserName, String actionType,
                      Long targetTenantId, String targetTenantName, String detail, String ipaddr) {
        try {
            PlatformPrivilegeLog logEntry = new PlatformPrivilegeLog();
            logEntry.setPrincipalUserId(principalUserId);
            logEntry.setPrincipalUserName(principalUserName != null ? principalUserName : "");
            logEntry.setActionType(actionType != null ? actionType : "UNKNOWN");
            logEntry.setTargetTenantId(targetTenantId);
            logEntry.setTargetTenantName(targetTenantName != null ? targetTenantName : "");
            logEntry.setDetail(detail != null ? detail : "");
            logEntry.setIpaddr(ipaddr != null ? ipaddr : "");
            logEntry.setCreateTime(LocalDateTime.now());
            save(logEntry);
        } catch (Exception e) {
            log.warn("[PlatformPrivilegeLog] 审计日志写入失败: {}", e.getMessage());
        }
    }
}
