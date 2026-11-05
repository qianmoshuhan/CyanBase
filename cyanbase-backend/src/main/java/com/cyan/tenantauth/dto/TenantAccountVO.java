package com.cyan.tenantauth.dto;

import lombok.Data;

/**
 * 手机号匹配到的用户账号项 VO
 * 多租户场景下前端弹窗选择租户使用
 */
@Data
public class TenantAccountVO {
    /** 用户ID */
    private Long userId;
    /** 租户ID */
    private Long tenantId;
    /** 租户名称 */
    private String tenantName;
    /** 用户昵称 */
    private String nickName;
}