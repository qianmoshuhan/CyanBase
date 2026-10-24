package com.cyan.modules.sydyd.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cyan.common.exception.BusinessException;
import com.cyan.modules.sydyd.entity.ThreeBigList;
import com.cyan.modules.sydyd.entity.ThreeBigSystem;
import com.cyan.modules.sydyd.mapper.ThreeBigListMapper;
import com.cyan.modules.sydyd.mapper.ThreeBigSystemMapper;
import com.cyan.modules.sydyd.service.ThreeBigSystemService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 三重一大制度 Service 实现
 * <p>
 * 核心逻辑：
 * - 租户隔离由 MP + CustomTenantHandler 自动完成
 * - 停用制度时，校验制度下是否还有清单/事项（可选：改为级联停用）
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ThreeBigSystemServiceImpl
        extends ServiceImpl<ThreeBigSystemMapper, ThreeBigSystem>
        implements ThreeBigSystemService {

    private final ThreeBigListMapper listMapper;

    @Override
    public void enable(Long id) {
        ThreeBigSystem system = getById(id);
        if (system == null) throw new BusinessException("制度不存在");
        system.setStatus(0);
        updateById(system);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void disable(Long id) {
        ThreeBigSystem system = getById(id);
        if (system == null) throw new BusinessException("制度不存在");

        // 校验：制度下是否还挂着清单
        long listCount = listMapper.selectCount(new LambdaQueryWrapper<ThreeBigList>()
                .eq(ThreeBigList::getSystemId, id)
                .eq(ThreeBigList::getStatus, 0)); // 只卡启用中的清单
        if (listCount > 0) {
            throw new BusinessException("该制度下还有 " + listCount + " 条启用中的清单，请先停用或删除清单后再停用制度");
        }

        system.setStatus(1);
        updateById(system);
        log.info("[三重一大] 制度停用 systemId={}", id);
    }
}