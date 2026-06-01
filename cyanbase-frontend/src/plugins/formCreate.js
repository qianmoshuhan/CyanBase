// src/plugins/formCreate.js
import formCreate from '@form-create/element-ui'
import FcDesigner from '@form-create/designer'

/**
 * 安全解析 layoutConfig JSON 字符串
 * 兼容旧格式（简单fields数组）和新格式（form-create v3 标准rule）
 */
export function parseLayoutConfig(configStr) {
  if (!configStr) return []
  try {
    const parsed = JSON.parse(configStr)

    // ✅ 新格式：已经是 form-create v3 标准 rule
    if (Array.isArray(parsed) && parsed.length > 0 && (parsed[0].type || parsed[0].component)) {
      return parsed
    }

    // ✅ 兼容旧格式：{ fields: [{ fieldKey, fieldName, fieldType, required, sortOrder }] }
    if (parsed.fields && Array.isArray(parsed.fields)) {
      return parsed.fields
        .sort((a, b) => (a.sortOrder || 0) - (b.sortOrder || 0))
        .map(field => ({
          type: field.fieldType === 'textarea' ? 'input' : field.fieldType,
          field: field.fieldKey,
          title: field.fieldName,
          props: {
            ...(field.fieldType === 'textarea' ? { type: 'textarea', rows: 4 } : {}),
            ...(field.fieldType === 'number' ? { type: 'number' } : {})
          },
          validate: field.required === 1
            ? [{ required: true, message: `${field.fieldName}不能为空`, trigger: 'blur' }]
            : []
        }))
    }

    return []
  } catch (e) {
    console.error('[FormPreview] layoutConfig 解析失败:', e)
    return []
  }
}

export default {
  install(app) {
    app.use(formCreate)
    app.use(FcDesigner)
  }
}