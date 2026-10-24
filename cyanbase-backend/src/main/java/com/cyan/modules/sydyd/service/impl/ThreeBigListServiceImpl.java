package com.cyan.modules.sydyd.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cyan.common.exception.BusinessException;
import com.cyan.modules.sydyd.entity.ThreeBigItem;
import com.cyan.modules.sydyd.entity.ThreeBigList;
import com.cyan.modules.sydyd.entity.ThreeBigSystem;
import com.cyan.modules.sydyd.mapper.ThreeBigItemMapper;
import com.cyan.modules.sydyd.mapper.ThreeBigListMapper;
import com.cyan.modules.sydyd.mapper.ThreeBigSystemMapper;
import com.cyan.modules.sydyd.service.ThreeBigListService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.Serializable;
import java.util.List;

/**
 * 三重一大事项清单 Service 实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ThreeBigListServiceImpl
        extends ServiceImpl<ThreeBigListMapper, ThreeBigList>
        implements ThreeBigListService {

    private final ThreeBigSystemMapper systemMapper;
    private final ThreeBigItemMapper itemMapper;

    /**
     * 新增清单前置校验：所属制度必须存在 + 启用中
     */
    @Override
    public boolean save(ThreeBigList entity) {
        // 1. 制度必须存在
        ThreeBigSystem system = systemMapper.selectById(entity.getSystemId());
        if (system == null) throw new BusinessException("制度不存在（systemId=" + entity.getSystemId() + "）");

        // 2. 制度必须启用
        if (system.getStatus() != null && system.getStatus() == 1) {
            throw new BusinessException("制度已停用，不能在停用制度下新建清单");
        }

        // 3. 自动填充 sortNum 默认值
        if (entity.getSortNum() == null) {
            entity.setSortNum(0);
        }

        return super.save(entity);
    }

    /**
     * 删除清单前置校验：清单下不能还挂着事项
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeById(Serializable id) {
        long itemCount = itemMapper.selectCount(new LambdaQueryWrapper<ThreeBigItem>()
                .eq(ThreeBigItem::getListId, id));
        if (itemCount > 0) {
            throw new BusinessException("该清单下还有 " + itemCount + " 条业务事项，请先清理事项后再删除清单");
        }
        return super.removeById(id);
    }

    @Override
    public List<ThreeBigList> listBySystemId(Long systemId) {
        return list(new LambdaQueryWrapper<ThreeBigList>()
                .eq(ThreeBigList::getSystemId, systemId)
                .eq(ThreeBigList::getStatus, 0)
                .orderByAsc(ThreeBigList::getSortNum));
    }

    @Override
    public List<ThreeBigList> listByCategory(Integer category) {
        return list(new LambdaQueryWrapper<ThreeBigList>()
                .eq(ThreeBigList::getCategory, category)
                .eq(ThreeBigList::getStatus, 0)
                .orderByAsc(ThreeBigList::getSortNum));
    }
}