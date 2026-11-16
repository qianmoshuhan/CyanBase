package com.cyan.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 部门导入结果 —— 统一返回结构
 * <p>
 * 核心设计：<b>全部校验通过才入库</b>，任意一行出错整体回滚（事务级保证）。
 * 失败明细携带 Excel 真实行号（1-based），供前端弹窗展示。
 * </p>
 */
@Data
public class DeptImportResultVO {

    /** Excel 实际读取的总行数（不含表头） */
    private int totalRows;

    /** 成功入库的行数 */
    private int successRows;

    /** 校验失败的行数 */
    private int failRows;

    /** 失败明细 —— 仅当 failRows > 0 时有值 */
    private List<FailItem> failList = new ArrayList<>();

    /** 是否全部成功 */
    public boolean isAllSuccess() {
        return failRows == 0;
    }

    /** 单条失败：Excel 真实行号（1-based，从第 2 行开始）+ 错误信息 */
    @Data
    public static class FailItem {
        private int rowNum;        // Excel 行号（1-based）
        private String errorMsg;   // 错误描述

        public FailItem(int rowNum, String errorMsg) {
            this.rowNum = rowNum;
            this.errorMsg = errorMsg;
        }
    }
}
