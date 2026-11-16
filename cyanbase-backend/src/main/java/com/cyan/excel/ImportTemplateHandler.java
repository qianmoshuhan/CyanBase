package com.cyan.excel;

import com.alibaba.excel.write.handler.SheetWriteHandler;
import com.alibaba.excel.write.handler.context.SheetWriteHandlerContext;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddressList;

import java.util.Arrays;
import java.util.List;

/**
 * 部门导入模板专用 —— 自定义 EasyExcel 写入处理器
 * <p>
 * 职责：<b>仅给状态列 / 是否经营单元列加 Excel 下拉框</b>，不加说明行（提示全部由前端弹窗展示）。
 * </p>
 *
 * <h3>模板结构</h3>
 * <pre>
 *  Row 0（Excel 第 1 行）：8 列表头（EasyExcel 默认生成，顺序与 DeptImportTemplateVO 一致）
 *  Row 1（Excel 第 2 行）：空白起始行 + 下拉生效
 *  ...
 *  Row 5000（Excel 第 5001 行）：下拉最后生效行
 * </pre>
 *
 * <h3>下拉范围</h3>
 * <ul>
 *   <li>状态列（index=4，Excel E 列）：E2:E5001，可选：正常 / 停用</li>
 *   <li>是否经营单元列（index=5，Excel F 列）：F2:F5001，可选：是 / 否</li>
 * </ul>
 */
public class ImportTemplateHandler implements SheetWriteHandler {

    /** 表头占位：仅 1 行表头，无额外说明行 —— 下拉从 Excel 第 2 行开始生效（0-based row=1） */
    private static final int DATA_START_ROW = 1;

    /** 下拉覆盖到 Excel 第 5001 行（0-based row=5000），对应模板支持最多 5000 条数据 */
    private static final int DATA_END_ROW = 5000;

    @Override
    public void afterSheetCreate(SheetWriteHandlerContext context) {
        Sheet sheet = context.getWriteSheetHolder().getSheet();

        // 状态列（index=4，Excel E 列）：E2:E5001
        addDataValidation(sheet, 4, Arrays.asList("正常", "停用"));
        // 是否经营单元列（index=5，Excel F 列）：F2:F5001
        addDataValidation(sheet, 5, Arrays.asList("是", "否"));
    }

    /**
     * 给指定列加 Excel 下拉框
     *
     * @param sheet        当前 Sheet
     * @param columnIndex  列索引（0-based，对应 DeptImportTemplateVO 中的 ExcelProperty index）
     * @param dropdownList 下拉可选项
     */
    private void addDataValidation(Sheet sheet, int columnIndex, List<String> dropdownList) {
        CellRangeAddressList range = new CellRangeAddressList(
                DATA_START_ROW,          // Excel 第 2 行（跳过 1 行表头）
                DATA_END_ROW,            // Excel 第 5001 行
                columnIndex, columnIndex // 单列
        );

        DataValidationHelper helper = sheet.getDataValidationHelper();
        DataValidationConstraint constraint = helper.createExplicitListConstraint(
                dropdownList.toArray(new String[0])
        );
        DataValidation validation = helper.createValidation(constraint, range);

        // 输入提示 + 错误拦截
        validation.createPromptBox("输入提示", "可选值：" + String.join("、", dropdownList));
        validation.setShowPromptBox(true);
        validation.createErrorBox("输入错误", "请从下拉列表中选择有效选项");
        validation.setShowErrorBox(true);

        sheet.addValidationData(validation);
    }
}