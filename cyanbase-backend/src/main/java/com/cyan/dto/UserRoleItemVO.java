package com.cyan.dto;

import lombok.Data;

/**
 * 用户详情弹窗「已分配角色」列表项
 */
@Data
public class UserRoleItemVO {
    /** 角色ID */
    private Long roleId;
    /** 角色名称 */
    private String roleName;
    /** 角色权限字符（如 admin） */
    private String roleKey;
    /** 归属类型：PLATFORM=平台角色，TENANT=租户角色，DEPT=部门私有角色 */
    private String roleScope;
    /** 租户名称（租户角色时显示） */
    private String tenantName;
    /** 数据范围（1全部/2本部门/3本部门及下属/4仅本人/5自定义） */
    private Integer dataScope;
    /** 角色状态（0正常/1停用） */
    private Integer status;
}
