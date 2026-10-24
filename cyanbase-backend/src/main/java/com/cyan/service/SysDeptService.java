package com.cyan.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.cyan.entity.SysDept;
import java.util.List;

public interface SysDeptService extends IService<SysDept> {
    /** 递归查询指定部门（含）及其所有子孙部门 ID */
    List<Long> getChildDeptIds(Long parentId);
}