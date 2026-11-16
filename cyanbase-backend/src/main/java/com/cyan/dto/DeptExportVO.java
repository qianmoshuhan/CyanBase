package com.cyan.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import com.alibaba.excel.annotation.write.style.ContentRowHeight;
import com.alibaba.excel.annotation.write.style.HeadRowHeight;
import lombok.Data;

/**
 * 部门导出 VO —— 后续复用为部门导入模板（表头保持可扩展）
 * <p>
 * 设计原则：
 * <ul>
 *   <li>单一 VO 同时供平台侧和租户侧使用 —— tenantId 列保留（租户侧值自然一致）</li>
 *   <li>层级路径用 / 分隔展示树归属（例：总部/技术部/后端组）</li>
 *   <li>deptCode 列必须有 —— 后续导入模板用它做父子引用，不依赖自增 ID</li>
 *   <li>枚举值导出为中文（状态：正常/停用；经营单元：是/否）</li>
 * </ul>
 * </p>
 *
 * <h3>列序 & 表头</h3>
 * <pre>
 *  index | 表头名         | 字段      | 说明
 *  ----- | -------------- | --------- | -------------------------------
 *   0    | 部门层级路径   | path      | / 分隔，如：总部/技术部/后端组
 *   1    | 部门编码       | deptCode  | 租户内唯一，导入时关联上级用
 *   2    | 部门名称       | deptName  |
 *   3    | 排序           | sortNum   |
 *   4    | 状态           | statusText| 正常 / 停用
 *   5    | 是否经营单元   | bizUnitText| 是 / 否
 *   6    | 所属租户       | tenantId  | 平台侧能看到所有租户 ID
 *   7    | 备注           | remark    |
 * </pre>
 */
@Data
@HeadRowHeight(25)
@ContentRowHeight(20)
public class DeptExportVO {

    /** 部门层级路径（/ 分隔，如：总部/技术部/后端组） */
    @ExcelProperty(value = "部门层级路径", index = 0)
    @ColumnWidth(40)
    private String path;

    /** 部门编码（租户内唯一，导入模板关联上级用） */
    @ExcelProperty(value = "部门编码", index = 1)
    @ColumnWidth(20)
    private String deptCode;

    @ExcelProperty(value = "部门名称", index = 2)
    @ColumnWidth(25)
    private String deptName;

    @ExcelProperty(value = "排序", index = 3)
    @ColumnWidth(10)
    private Integer sortNum;

    /** 状态：正常 / 停用（枚举翻译） */
    @ExcelProperty(value = "状态", index = 4)
    @ColumnWidth(10)
    private String statusText;

    /** 是否经营单元：是 / 否（枚举翻译） */
    @ExcelProperty(value = "是否经营单元", index = 5)
    @ColumnWidth(14)
    private String bizUnitText;

    @ExcelProperty(value = "所属租户", index = 6)
    @ColumnWidth(12)
    private Long tenantId;

    @ExcelProperty(value = "备注", index = 7)
    @ColumnWidth(30)
    private String remark;
}
