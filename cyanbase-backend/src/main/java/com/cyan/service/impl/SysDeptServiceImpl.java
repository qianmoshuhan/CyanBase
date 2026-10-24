package com.cyan.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cyan.entity.SysDept;
import com.cyan.mapper.SysDeptMapper;
import com.cyan.service.SysDeptService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class SysDeptServiceImpl extends ServiceImpl<SysDeptMapper, SysDept> implements SysDeptService {

    @Override
    public List<Long> getChildDeptIds(Long parentId) {
        List<Long> result = new ArrayList<>();
        if (parentId == null) return result;
        result.add(parentId);
        collect(parentId, result);
        return result;
    }

    private void collect(Long parentId, List<Long> bucket) {
        List<SysDept> children = list(new LambdaQueryWrapper<SysDept>()
                .eq(SysDept::getParentId, parentId));
        if (children == null || children.isEmpty()) return;
        for (SysDept c : children) {
            bucket.add(c.getId());
            collect(c.getId(), bucket);
        }
    }
}