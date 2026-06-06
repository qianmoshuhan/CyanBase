package com.cyan.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.cyan.entity.SysMenu;
import java.util.List;

public interface SysMenuService extends IService<SysMenu> {
    //新增：根据用户ID查询可用菜单集合（平铺JSON，暂不组装树形）
    List<SysMenu> selectMenuByUserId(Long userId);
}