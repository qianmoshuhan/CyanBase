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
     * 租户ID。
     * <p>
     * 从 JWT claims 中解析得到。
     * 当 JwtAuthenticationFilter 未从数据库查询完整 SysUser（即 sysUser 为 null）时，
     * 本字段是 CustomTenantHandler 获取 tenantId 的唯一来源。
     * </p>
     */
    private Long tenantId;

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