package com.cyan.security.domain;

import com.cyan.entity.SysUser;
import lombok.Data;
import java.io.Serializable;
import java.util.Set;

@Data
public class LoginUser implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long userId;
    private String userName;
    private String nickName;
    private String roles;
    private SysUser sysUser;
    /**
     * 用户已分配的权限码集合（system:user:add 这种）。
     * 后端鉴权用 authorities（Spring Security），本字段主要回传给前端渲染按钮显隐。
     */
    private Set<String> permissions;

    /**
     * 租户ID（SysUser.tenantId 的值）。
     * <p>
     * 三种取值语义：
     * <ul>
     *   <li>NULL  → 平台控制面主体（platformPrincipal=true），账号存放在平台控制面</li>
     *   <li>0     → 平台超管（admin），天然跳过全部租户拦截</li>
     *   <li>>0    → 租户用户，走正常租户隔离</li>
     * </ul>
     * </p>
     */
    private Long tenantId;

    /**
     * ⭐ 平台控制面主体标记（true=平台主体，false=普通租户用户或平台超管）
     * <p>
     * 平台主体登录后 tenantId 为 NULL，但可以通过 activeTenantId 选择一个活跃租户进入系统。
     * 选中后 activeTenantId 被写入 JWT，CustomTenantHandler / TenantMetaObjectHandler
     * 等底层组件会优先取 activeTenantId 而不是 tenantId=NULL。
     * </p>
     */
    private Boolean platformPrincipal;

    /**
     * ⭐ 平台主体当前活跃租户 ID（仅 platformPrincipal=true 时有意义）。
     */
    private Long activeTenantId;

    /**
     * ⭐ 租户内当前激活经营单元 ID（仅普通租户用户有意义，可空）。
     * <p>
     * 与 activeTenantId 互斥——平台主体账号完全禁用此能力，普通租户用户通过切换经营单元
     * 实现数据视图切换 + 追加绑定角色。底层租户拦截不使用此字段（红线约束）。
     * </p>
     */
    private Long activeBizUnitId;

    public LoginUser() {
    }

    public LoginUser(SysUser sysUser, String roles) {
        this.userId = sysUser.getId();
        this.userName = sysUser.getUserName();
        this.nickName = sysUser.getNickName();
        this.roles = roles;
        this.sysUser = sysUser;
    }
}