package com.cyan.tenantauth.dto;

import lombok.Data;

/**
 * 租户下拉列表项VO
 * 用于前端租户选择下拉框展示
 */
@Data
public class TenantItemVO {

    /** 租户ID */
    private Long id;

    /** 租户名称 */
    private String tenantName;

    /** 状态 0正常 1停用 */
    private Integer status;
}