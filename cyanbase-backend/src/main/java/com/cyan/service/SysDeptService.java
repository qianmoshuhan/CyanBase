package com.cyan.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.cyan.dto.DeptExportVO;
import com.cyan.dto.DeptImportResultVO;
import com.cyan.entity.SysDept;

import java.io.InputStream;
import java.util.List;

public interface SysDeptService extends IService<SysDept> {
    /** 递归查询指定部门（含）及其所有子孙部门 ID */
    List<Long> getChildDeptIds(Long parentId);

    /**
     * 构造部门导出数据（EasyExcel VO 列表）
     * <p>
     * 核心安全保障：依赖 MyBatis-Plus 拦截器链在 SQL 层自动追加条件 ——
     * <ul>
     *   <li>TenantLineInnerInterceptor → 自动追加 tenant_id = ?（租户隔离）</li>
     *   <li>DataPermissionInterceptor  → 自动追加用户角色 DataScope（如 id IN (...) / create_by = ?）</li>
     * </ul>
     * 查出的数据天然在当前操作权限范围内，应用层不再额外手写校验。
     * </p>
     *
     * @param deptName 部门名称模糊匹配（可 null）
     * @param status   状态精确匹配（可 null：0 正常 / 1 停用）
     * @param tenantId 租户ID精确匹配（可 null —— 平台侧显式传入以跨租户筛选；租户侧不传让 MP 自动隔离）
     * @return 导出 VO 列表（含层级路径构造、枚举翻译）
     */
    List<DeptExportVO> buildExportData(String deptName, Integer status, Long tenantId);
    /**
     * 部门导入 —— 读取 Excel、逐行校验、事务性入库
     *
     * @param isTenantMode true=租户侧调用（忽略 Excel 租户列，数据归属当前登录租户）
     *                     false=平台侧调用（读取 Excel 租户列作为目标归属租户）
     * @param currentTenantId 当前登录租户 ID（租户侧从 SecurityUtils 取；平台侧传 null）
     * @param inputStream    Excel 文件流（.xlsx）
     * @return 导入结果（总行数 / 成功数 / 失败数 + 失败明细）
     */
    DeptImportResultVO importDept(boolean isTenantMode, Long currentTenantId, InputStream inputStream);
}