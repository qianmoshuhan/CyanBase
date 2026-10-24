package com.cyan.tenantauth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cyan.entity.SysTenant;
import com.cyan.entity.SysUser;
import com.cyan.entity.SysUserRole;
import com.cyan.mapper.SysTenantMapper;
import com.cyan.mapper.SysUserMapper;
import com.cyan.mapper.SysUserRoleMapper;
import com.cyan.security.dto.LoginResponse;
import com.cyan.security.util.JwtUtil;
import com.cyan.security.service.RefreshTokenService;
import com.cyan.service.SysUserService;
import com.cyan.tenantauth.dto.TenantItemVO;
import com.cyan.tenantauth.dto.TenantLoginReq;
import com.cyan.tenantauth.service.TenantAuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap; // 👉本次新增
import java.util.List;
import java.util.Map;     // 👉本次新增
import java.util.stream.Collectors;

/**
 * 租户认证Service实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantAuthServiceImpl implements TenantAuthService {

    private final SysTenantMapper sysTenantMapper;
    private final SysUserMapper sysUserMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final JwtUtil jwtUtil;
    private final RefreshTokenService refreshTokenService;
    private final SysUserService sysUserService;

    @Override
    public List<TenantItemVO> getEnableTenantList() {
        LambdaQueryWrapper<SysTenant> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysTenant::getStatus, 0);
        // 额外过滤已过期租户（避免登录页下拉里出现）
        wrapper.and(w -> w.isNull(SysTenant::getExpireDate)
                        .or().ge(SysTenant::getExpireDate, java.time.LocalDateTime.now()));
        List<SysTenant> tenantList = sysTenantMapper.selectList(wrapper);

        return tenantList.stream().map(t -> {
            TenantItemVO vo = new TenantItemVO();
            vo.setId(t.getId());
            vo.setTenantName(t.getTenantName());
            vo.setStatus(t.getStatus());
            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    public LoginResponse tenantLogin(TenantLoginReq req) {
        // 1. 校验租户
        SysTenant tenant = sysTenantMapper.selectById(req.getTenantId());
        if (tenant == null) throw new RuntimeException("租户不存在");
        // status=1(冻结) 或 status=2(过期) 一律拒绝登录
        if (tenant.getStatus() != null && tenant.getStatus() != 0) {
            throw new RuntimeException("租户已过期或已冻结，请联系平台管理员");
        }

        // 2. 校验用户（多租户联合查询）
        LambdaQueryWrapper<SysUser> userWrapper = new LambdaQueryWrapper<>();
        userWrapper.eq(SysUser::getUserName, req.getUsername())
                   .eq(SysUser::getTenantId, req.getTenantId());
        SysUser sysUser = sysUserMapper.selectOne(userWrapper);
        if (sysUser == null) throw new RuntimeException("账号不存在");
        if (sysUser.getStatus() == 1) throw new RuntimeException("账号已被禁用");
        // ⭐ 统一密码校验（BCrypt + 明文兼容 + 自动迁移）
        if (!sysUserService.verifyPassword(sysUser.getId(), req.getPassword()))
            throw new RuntimeException("密码错误");

        // 3. 角色
        LambdaQueryWrapper<SysUserRole> roleWrapper = new LambdaQueryWrapper<>();
        roleWrapper.eq(SysUserRole::getUserId, sysUser.getId());
        List<SysUserRole> userRoles = sysUserRoleMapper.selectList(roleWrapper);
        String roleStr = userRoles == null || userRoles.isEmpty() ? ""
                : userRoles.stream().map(SysUserRole::getRoleId).map(String::valueOf).collect(Collectors.joining(","));

        // 4. 生成 Access Token（claims 带 tenantId）
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", sysUser.getId());
        claims.put("userName", sysUser.getUserName());
        claims.put("roles", roleStr);
        jwtUtil.putTenantId(claims, req.getTenantId());
        String accessToken = jwtUtil.createToken(claims);

        // 5. 生成 Refresh Token
        String refreshToken = refreshTokenService.createRefreshToken(sysUser.getId());

        log.info("租户登录成功，tenantId={}, userId={}", req.getTenantId(), sysUser.getId());

        long expiresIn = jwtUtil.getAccessExpireMs() / 1000;
        return new LoginResponse(accessToken, refreshToken, expiresIn);
    }
}