package com.cyan.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import com.alibaba.excel.annotation.write.style.ContentRowHeight;
import lombok.Data;

/**
 * 部门导入模板 VO —— 结构与 DeptExportVO 完全对齐
 * <p>
 * 设计原则：
 * <ul>
 *   <li>与 DeptExportVO 列序 / 表头 1:1 对应 —— 保证导出 = 导入模板可复用</li>
 *   <li>下拉、说明行由 ImportTemplateHandler 统一处理 —— 不把样式/校验逻辑堆在 VO 上</li>
 * </ul>
 * </p>
 *
 * <h3>列序 & 表头</h3>
 * <pre>
 *  index | 表头名         | 字段      | 说明
 *  ----- | -------------- | --------- | --------------------------------
 *   0    | 部门层级路径   | path      | / 分隔，如：集团/事业部/部门
 *   1    | 部门编码       | deptCode  | ⚠️ 本列禁止填写，由系统自动生成
 *   2    | 部门名称       | deptName  | 必填
 *   3    | 排序           | sortNum   | 数字，可空
 *   4    | 状态           | statusText| 下拉：正常 / 停用
 *   5    | 是否经营单元   | bizUnitText| 下拉：是 / 否
 *   6    | 所属租户       | tenantId  | 租户模式下系统自动忽略
 *   7    | 备注           | remark    | 可空
 * </pre>
 */
@Data
@ContentRowHeight(20)
public class DeptImportTemplateVO {

    @ExcelProperty(value = "部门层级路径", index = 0)
    @ColumnWidth(40)
    private String path;

    @ExcelProperty(value = "部门编码", index = 1)
    @ColumnWidth(20)
    private String deptCode;

    @ExcelProperty(value = "部门名称", index = 2)
    @ColumnWidth(25)
    private String deptName;

    @ExcelProperty(value = "排序", index = 3)
    @ColumnWidth(10)
    private Integer sortNum;

    @ExcelProperty(value = "状态", index = 4)
    @ColumnWidth(10)
    private String statusText;

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
