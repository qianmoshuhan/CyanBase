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
}