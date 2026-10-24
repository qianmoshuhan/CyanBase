package com.cyan.modules.sydyd.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.cyan.modules.sydyd.entity.ThreeBigList;

import java.util.List;

/**
 * 三重一大事项清单 Service
 */
public interface ThreeBigListService extends IService<ThreeBigList> {

    /** 根据制度ID查启用中的清单列表（事项填报时下拉选） */
    List<ThreeBigList> listBySystemId(Long systemId);

    /** 根据分类查启用中的清单 */
    List<ThreeBigList> listByCategory(Integer category);
}