package com.cyan.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cyan.entity.SysUser;
import com.cyan.entity.SysUserRole;
import com.cyan.mapper.SysUserMapper;
import com.cyan.mapper.SysUserRoleMapper;
import com.cyan.service.SysUserRoleService;
import com.cyan.service.SysUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;

@Slf4j
@Service
@RequiredArgsConstructor
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements SysUserService {

    private final SysUserRoleMapper userRoleMapper;
    private final SysUserRoleService userRoleService;
    private final BCryptPasswordEncoder passwordEncoder;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean saveUserAndRole(SysUser user) {
        // ⭐ 新增：对传入的密码做 BCrypt 加密（如果前端传了的话）
        if (user.getPassword() != null && !user.getPassword().isEmpty()
                && !user.getPassword().startsWith("$2a$")) {
            user.setPassword(passwordEncoder.encode(user.getPassword()));
        }
        boolean save = save(user);
        if (!save) return false;
        saveUserRoleRel(user.getId(), user.getRoleIds());
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateUserAndRole(SysUser user) {
        // ⭐ 新增：编辑用户时如果传了新密码也要加密
        if (user.getPassword() != null && !user.getPassword().isEmpty()
                && !user.getPassword().startsWith("$2a$")) {
            user.setPassword(passwordEncoder.encode(user.getPassword()));
        }
        boolean update = updateById(user);
        if (!update) return false;
        LambdaQueryWrapper<SysUserRole> removeWrapper = new LambdaQueryWrapper<>();
        removeWrapper.eq(SysUserRole::getUserId, user.getId());
        userRoleMapper.delete(removeWrapper);
        saveUserRoleRel(user.getId(), user.getRoleIds());
        return true;
    }

    @Override
    public List<Long> getRoleIdsByUserId(Long userId) {
        LambdaQueryWrapper<SysUserRole> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUserRole::getUserId,userId);
        List<SysUserRole> list = userRoleMapper.selectList(wrapper);
        return list.stream().map(SysUserRole::getRoleId).collect(Collectors.toList());
    }

    /** 私有：批量保存用户角色关联 */
    private void saveUserRoleRel(Long userId,List<Long> roleIds){
        if(roleIds == null || roleIds.isEmpty()) return;
        List<SysUserRole> saveList = new ArrayList<>();
        for(Long rid:roleIds){
            SysUserRole ur = new SysUserRole();
            ur.setUserId(userId);
            ur.setRoleId(rid);
            saveList.add(ur);
        }
        userRoleService.saveBatch(saveList);
    }

    /**
     * ⭐ 统一密码校验（登录/改密共用入口）
     * <p>BCrypt hash 用标准 matches；旧明文数据直接 equals，通过后自动迁移成 BCrypt</p>
     */
    @Override
    public boolean verifyPassword(Long userId, String rawPwd) {
        SysUser user = getById(userId);
        if (user == null) return false;
        String dbPwd = user.getPassword();
        if (dbPwd == null) return false;

        boolean match;
        if (dbPwd.startsWith("$2a$")) {
            // 正常 BCrypt hash
            match = passwordEncoder.matches(rawPwd, dbPwd);
        } else {
            // 旧明文兼容 + 自动迁移
            match = rawPwd != null && rawPwd.equals(dbPwd);
            if (match) {
                SysUser update = new SysUser();
                update.setId(userId);
                update.setPassword(passwordEncoder.encode(rawPwd));
                updateById(update);
                log.info("[密码迁移] userId={} 明文密码已自动升级为 BCrypt hash", userId);
            }
        }
        return match;
    }

    /**
     * 修改当前登录用户的密码（公共方法，platform/tenant 两条线都能用）
     * @return null=成功；否则是错误提示
     */
    @Override
    public String changePassword(Long userId, String oldPwd, String newPwd) {
        if (oldPwd == null || newPwd == null || newPwd.length() < 6) {
            return "新密码长度不能少于6位";
        }
        if (!verifyPassword(userId, oldPwd)) {
            return "原密码错误";
        }
        SysUser update = new SysUser();
        update.setId(userId);
        update.setPassword(passwordEncoder.encode(newPwd));
        updateById(update);
        return null;
    }
}