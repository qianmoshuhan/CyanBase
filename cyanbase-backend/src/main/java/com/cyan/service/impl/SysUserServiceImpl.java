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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements SysUserService {

   
    private final SysUserRoleMapper userRoleMapper;

    
    private final SysUserRoleService userRoleService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean saveUserAndRole(SysUser user) {
        //1.保存用户主数据
        boolean save = save(user);
        if(!save) return false;
        //2.保存用户角色关联
        saveUserRoleRel(user.getId(),user.getRoleIds());
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateUserAndRole(SysUser user) {
        //1.更新用户信息
        boolean update = updateById(user);
        if(!update) return false;
        //2.先删除原有角色关联，再新增
        LambdaQueryWrapper<SysUserRole> removeWrapper = new LambdaQueryWrapper<>();
        removeWrapper.eq(SysUserRole::getUserId,user.getId());
        userRoleMapper.delete(removeWrapper);
        saveUserRoleRel(user.getId(),user.getRoleIds());
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
}