package com.cyan.modules.form.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cyan.modules.form.entity.FormDataItem;
import com.cyan.modules.form.mapper.FormDataItemMapper;
import com.cyan.modules.form.service.FormDataItemService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 表单数据项Service实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FormDataItemServiceImpl extends ServiceImpl<FormDataItemMapper, FormDataItem> implements FormDataItemService {

    @Override
    public List<FormDataItem> listByDataId(Long dataId) {
        LambdaQueryWrapper<FormDataItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FormDataItem::getDataId, dataId);
        return this.list(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean batchSaveItems(Long dataId, Map<String, Object> fieldValues) {
        if (fieldValues == null || fieldValues.isEmpty()) {
            return true;
        }
        
        for (Map.Entry<String, Object> entry : fieldValues.entrySet()) {
            FormDataItem item = new FormDataItem();
            item.setDataId(dataId);
            item.setFieldKey(entry.getKey());
            item.setFieldName(entry.getKey()); // TODO: 需要从字段配置中获取字段名称
            item.setFieldValue(entry.getValue() != null ? entry.getValue().toString() : null);
            
            this.save(item);
        }
        
        log.info("批量保存表单数据项成功: dataId={}, count={}", dataId, fieldValues.size());
        return true;
    }
}