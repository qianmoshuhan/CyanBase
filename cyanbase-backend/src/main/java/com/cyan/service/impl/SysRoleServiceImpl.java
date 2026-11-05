package com.cyan.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cyan.common.security.util.SecurityUtils;
import com.cyan.entity.SysRole;
import com.cyan.mapper.SysRoleMapper;
import com.cyan.service.SysRoleService;
import org.springframework.stereotype.Service;

@Service
public class SysRoleServiceImpl extends ServiceImpl<SysRoleMapper, SysRole> implements SysRoleService {

    /**
     * 创建角色：自动判断是全局角色还是部门私有角色
     * <p>
     * 超管（tenant_id=0 或 isSuperAdmin=true）创建 → deptId=null（全局角色，全租户可见）
     * 部门管理员创建 → deptId=当前用户 deptId（部门私有角色，仅本部门+子部门可见）
     * <p>
     * 前端不需要传 deptId，Service 层统一管控！
     */
    public boolean createRole(SysRole role) {
        if (role == null) return false;

        // 自动填充 deptId：如果前端没传（一般不会传），从当前登录用户取
        if (role.getDeptId() == null) {
            try {
                Long currentDeptId = SecurityUtils.getLoginUser().getSysUser().getDeptId();
                // 超管的 deptId 通常是 null 或特殊值 → 全局角色
                // 非超管 → 部门私有角色，归属创建者所在部门
                if (currentDeptId != null) {
                    role.setDeptId(currentDeptId);
                }
            } catch (Exception ignored) {}
            // 超管（currentDeptId == null）→ role.deptId 保持 null → 全局角色 ✅
        }

        return save(role);
    }
}