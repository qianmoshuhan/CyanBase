package com.cyan.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

/**
 * /system/tenant/{id}/detail 接口返回结构（独立聚合，不复用 list）
 */
@Data
public class TenantDetailVO {
    // ====== 基础信息 ======
    private Long id;
    private String tenantName;
    private String tenantCode;
    private String contactUser;
    private String contactPhone;
    private String address;
    /** 0正常 1冻结 2已过期 */
    private Integer status;
    /** 到期时间（即冻结日期，定时任务 expireTenantByDate 用它判定） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime expireDate;
    /** 配额总量（NULL = 不限） */
    private Integer maxUserNum;
    /** 已用配额（sys_user 中该租户的用户数） */
    private Integer usedUserNum;
    private String remark;
    private String createBy;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    // ====== 已开通模块 ======
    private List<TenantModuleItemVO> modules;
}
