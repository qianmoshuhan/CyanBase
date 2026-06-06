package com.cyan.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cyan.common.R;
import com.cyan.entity.SysUser;
import com.cyan.security.domain.LoginUser;
import com.cyan.security.util.JwtUtil;
import com.cyan.service.SysUserService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final SysUserService sysUserService;
    private final JwtUtil jwtUtil;

    /**
     * 用户登录
     */
@PostMapping("/login")
public R<?> login(@RequestBody SysUserLoginRequest loginRequest) {
    // 查询用户
    LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(SysUser::getUserName, loginRequest.getUserName());
    SysUser sysUser = sysUserService.getOne(wrapper);
    if (sysUser == null) {
        return R.fail("账号不存在");
    }
    // 账号禁用判断
    if (sysUser.getStatus() == 1) {
        return R.fail("账号已被禁用");
    }
    // 密码校验
    if (!sysUser.getPassword().equals(loginRequest.getPassword())) {
        return R.fail("密码错误");
    }

    // ========== 核心修复：获取用户真实角色（无任何语法错误） ==========
    List<Long> roleIdList = sysUserService.getRoleIdsByUserId(sysUser.getId());
    String roleStr = "";
    if (roleIdList != null && !roleIdList.isEmpty()) {
        roleStr = String.join(",", roleIdList.stream().map(String::valueOf).toList());
    }

    // 生成 Token（动态角色，不再写死 admin）
    String token = jwtUtil.generateToken(sysUser.getId(), sysUser.getUserName(), roleStr);
    LoginUser loginUser = new LoginUser(sysUser, roleStr);
    loginUser.setRoles(roleStr);

    return R.ok(new LoginResponse(token, loginUser));
}

    /**
     * 获取当前登录用户信息
     */
    @GetMapping("/getUserInfo")
    public R<?> getUserInfo(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        // 检查是否有token
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return R.fail(401, "未登录");
        }

        String token = authHeader.substring(7);
        try {
            // 验证token并获取用户ID
            if (!jwtUtil.validateToken(token)) {
                return R.fail(401, "令牌无效或已过期");
            }

            Long userId = jwtUtil.getUserIdFromToken(token);

            // 获取用户信息
            SysUser sysUser = sysUserService.getById(userId);
            if (sysUser == null) {
                return R.fail("用户不存在");
            }

            String userName = jwtUtil.getUserNameFromToken(token);
            String roles = jwtUtil.getRolesFromToken(token);

            LoginUser loginUser = new LoginUser(sysUser, roles);
            loginUser.setUserId(userId);
            loginUser.setUserName(userName);

            return R.ok(loginUser);
        } catch (Exception e) {
            log.error("获取用户信息失败：{}", e.getMessage());
            return R.fail(401, "令牌解析失败");
        }
    }

    /**
     * 退出登录
     */
    @PostMapping("/logout")
    public R<?> logout(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        // 退出时清除上下文
        // 由于是JWT无状态认证，实际无需服务端清除，只需前端删除token即可
        return R.ok("退出成功");
    }

    /**
     * 登录请求封装
     */
    @Data
    public static class SysUserLoginRequest {
        private String userName;
        private String password;
    }

    /**
     * 登录响应封装
     */
    @Data
    public static class LoginResponse {
        private String token;
        private LoginUser userInfo;

        public LoginResponse(String token, LoginUser userInfo) {
            this.token = token;
            this.userInfo = userInfo;
        }
    }
}