package com.cyan.modules.sydyd.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.cyan.modules.sydyd.entity.ThreeBigSystem;

/**
 * 三重一大制度 Service
 */
public interface ThreeBigSystemService extends IService<ThreeBigSystem> {

    /** 启用制度 */
    void enable(Long id);

    /** 停用制度（停用后，该制度下的清单和事项对外不可见） */
    void disable(Long id);
}