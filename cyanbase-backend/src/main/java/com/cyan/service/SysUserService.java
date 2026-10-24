package com.cyan.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.cyan.entity.SysUser;
import java.util.List;

public interface SysUserService extends IService<SysUser> {
    //新增：保存用户+绑定角色
    boolean saveUserAndRole(SysUser user);
    //新增：更新用户+更新角色关联
    boolean updateUserAndRole(SysUser user);
    //新增：根据用户id查询绑定角色ID
    List<Long> getRoleIdsByUserId(Long userId);
    /** 修改密码，返回 null=成功；否则是错误提示 */
    String changePassword(Long userId, String oldPwd, String newPwd);

    /**
     * ⭐ 统一密码校验（登录/改密共用）
     * <p>同时做明文 → BCrypt 自动迁移：
     * DB 密码如果不是 $2a$ 开头，直接比明文，通过后立即 BCrypt hash 存回</p>
     */
    boolean verifyPassword(Long userId, String rawPwd);
}