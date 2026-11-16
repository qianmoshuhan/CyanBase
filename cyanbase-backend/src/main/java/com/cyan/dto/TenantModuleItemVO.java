package com.cyan.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 租户详情弹窗「已开通模块」列表项
 */
@Data
public class TenantModuleItemVO {
    private String moduleCode;
    private String moduleName;
    private Integer enabled;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime validFrom;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime validTo;
    private String remark;
}
