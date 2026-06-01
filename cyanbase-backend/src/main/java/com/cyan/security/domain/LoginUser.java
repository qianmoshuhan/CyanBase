package com.cyan.security.domain;

import com.cyan.entity.SysUser;
import lombok.Data;
import java.io.Serializable;

@Data
public class LoginUser implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long userId;
    private String userName;
    private String nickName;
    private String roles;
    private SysUser sysUser;

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