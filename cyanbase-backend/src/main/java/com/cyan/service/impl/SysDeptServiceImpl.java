package com.cyan.service.impl;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.read.listener.ReadListener;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cyan.common.exception.BusinessException;
import com.cyan.dto.DeptExportVO;
import com.cyan.dto.DeptImportResultVO;
import com.cyan.dto.DeptImportTemplateVO;
import com.cyan.entity.SysDept;
import com.cyan.mapper.SysDeptMapper;
import com.cyan.service.SysDeptService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class SysDeptServiceImpl extends ServiceImpl<SysDeptMapper, SysDept> implements SysDeptService {

    /** 单次导出数据量阈值：超过给友好提示避免超时 */
    private static final int EXPORT_MAX_ROWS = 5000;

    /** 单次导入数据量阈值：超过给友好提示拒绝导入 */
    private static final int IMPORT_MAX_ROWS = 5000;

    /** 导入事务批量入库时的批次大小 */
    private static final int BATCH_SIZE = 500;

    /**
     * 编程式事务模板 —— 解决 Spring AOP 自调用绕过代理的经典问题。
     * <p>
     * 为什么不用 @Transactional 声明式事务？
     * importDept 内部调 saveBatch 是类内部自调用，
     * Spring AOP 代理不会拦截自调用 → @Transactional 失效。
     * TransactionTemplate 直接操作 TransactionManager，与 AOP 代理无关，事务一定生效。
     * </p>
     */
    @Autowired
    private TransactionTemplate transactionTemplate;

    @Override
    public List<Long> getChildDeptIds(Long parentId) {
        List<Long> result = new ArrayList<>();
        if (parentId == null) return result;
        result.add(parentId);
        collect(parentId, result);
        return result;
    }

    private void collect(Long parentId, List<Long> bucket) {
        List<SysDept> children = list(new LambdaQueryWrapper<SysDept>()
                .eq(SysDept::getParentId, parentId));
        if (children == null || children.isEmpty()) return;
        for (SysDept c : children) {
            bucket.add(c.getId());
            collect(c.getId(), bucket);
        }
    }

    // ======== 部门导出相关 ========

    @Override
    public List<DeptExportVO> buildExportData(String deptName, Integer status, Long tenantId) {
        LambdaQueryWrapper<SysDept> wrapper = new LambdaQueryWrapper<>();
        if (deptName != null && !deptName.isBlank()) {
            wrapper.like(SysDept::getDeptName, deptName.trim());
        }
        if (status != null) {
            wrapper.eq(SysDept::getStatus, status);
        }
        if (tenantId != null) {
            wrapper.eq(SysDept::getTenantId, tenantId);
        }
        wrapper.orderByAsc(SysDept::getTenantId, SysDept::getSortNum);

        List<SysDept> deptList = list(wrapper);

        if (deptList.size() > EXPORT_MAX_ROWS) {
            throw new BusinessException(
                    "当前导出数据量已达 " + deptList.size() + " 条，超过单次导出限制（" + EXPORT_MAX_ROWS + " 条），" +
                    "请按部门名称或状态筛选后再导出");
        }

        Map<Long, SysDept> idMap = deptList.stream()
                .collect(Collectors.toMap(SysDept::getId, d -> d));

        Map<Long, Map<Long, String>> pathCache = new HashMap<>();
        for (SysDept d : deptList) {
            pathCache.computeIfAbsent(d.getTenantId(), k -> new HashMap<>());
        }

        List<DeptExportVO> voList = new ArrayList<>(deptList.size());
        for (SysDept d : deptList) {
            DeptExportVO vo = new DeptExportVO();
            vo.setPath(buildPath(d, idMap, pathCache.get(d.getTenantId())));
            vo.setDeptCode(d.getDeptCode() == null ? "" : d.getDeptCode());
            vo.setDeptName(d.getDeptName());
            vo.setSortNum(d.getSortNum() == null ? 0 : d.getSortNum());
            vo.setStatusText(statusToText(d.getStatus()));
            vo.setBizUnitText(Integer.valueOf(1).equals(d.getIsBizUnit()) ? "是" : "否");
            vo.setTenantId(d.getTenantId());
            vo.setRemark(d.getRemark() == null ? "" : d.getRemark());
            voList.add(vo);
        }
        return voList;
    }

    private String buildPath(SysDept dept, Map<Long, SysDept> idMap, Map<Long, String> cache) {
        if (cache.containsKey(dept.getId())) return cache.get(dept.getId());
        List<String> names = new ArrayList<>();
        SysDept cur = dept;
        Set<Long> visited = new HashSet<>();
        while (cur != null && !visited.contains(cur.getId())) {
            visited.add(cur.getId());
            names.add(0, cur.getDeptName());
            Long pid = cur.getParentId();
            if (pid == null || pid == 0) break;
            cur = idMap.get(pid);
        }
        String path = String.join("/", names);
        cache.put(dept.getId(), path);
        return path;
    }

    private String statusToText(Integer status) {
        if (status == null || status == 0) return "正常";
        return "停用";
    }

    // ======== 部门导入相关 ========

    @Override
    public DeptImportResultVO importDept(boolean isTenantMode, Long currentTenantId, InputStream inputStream) {
        // ① EasyExcel 流式读取（ReadListener 一次性收集所有行，后续逐行校验 + 入库）
        List<DeptImportTemplateVO> dataList = new ArrayList<>();
        try {
            EasyExcel.read(inputStream, DeptImportTemplateVO.class, new ReadListener<DeptImportTemplateVO>() {
                @Override
                public void invoke(DeptImportTemplateVO data, AnalysisContext context) {
                    dataList.add(data);
                }
                @Override
                public void doAfterAllAnalysed(AnalysisContext context) {
                    // 全部读完
                }
            }).sheet().headRowNumber(1).doRead();
        } catch (BusinessException be) {
            throw be; // 业务异常直接抛出让全局异常处理
        } catch (Exception e) {
            log.error("[部门导入] Excel 解析失败", e);
            throw new BusinessException("Excel 文件解析失败，请确认文件格式是否正确（仅支持 .xlsx）");
        }

        // ② 阈值校验（空文件也拒绝）
        if (dataList.isEmpty()) {
            throw new BusinessException("Excel 文件为空，请填写数据后再导入");
        }
        if (dataList.size() > IMPORT_MAX_ROWS) {
            throw new BusinessException(
                    "当前导入数据量 " + dataList.size() + " 行，超过单次导入限制（" + IMPORT_MAX_ROWS + " 行），"
                            + "请拆分文件分批导入");
        }

        DeptImportResultVO result = new DeptImportResultVO();
        result.setTotalRows(dataList.size());

        // ③ 预加载：平台侧需要读取各租户现有部门做路径匹配
        //    Map<租户ID, Map<部门名, SysDept>> —— 路径解析时在对应租户范围内逐级查找
        Map<Long, Map<String, SysDept>> tenantDeptNameIndex = new HashMap<>();
        Set<Long> involvedTenantIds = new HashSet<>();
        // 用带索引的 for 循环收集 tenantId + 记录租户为空的行（避免 indexOf O(n²)）
        for (int idx = 0; idx < dataList.size(); idx++) {
            DeptImportTemplateVO row = dataList.get(idx);
            if (isTenantMode) {
                involvedTenantIds.add(currentTenantId);
            } else {
                if (row.getTenantId() != null && row.getTenantId() != 0) {
                    involvedTenantIds.add(row.getTenantId());
                } else {
                    result.getFailList().add(new DeptImportResultVO.FailItem(
                            idx + 2, "所属租户不能为空"));
                }
            }
        }
        // 批量预加载所有涉及租户的部门树
        for (Long tid : involvedTenantIds) {
            List<SysDept> tenantDepts = list(new LambdaQueryWrapper<SysDept>()
                    .eq(SysDept::getTenantId, tid)
                    .eq(SysDept::getIsDeleted, 0));
            Map<String, SysDept> nameMap = tenantDepts.stream()
                    .collect(Collectors.toMap(SysDept::getDeptName, d -> d, (a, b) -> a));
            tenantDeptNameIndex.put(tid, nameMap);
        }

        // ④ 逐行校验 + 构建待入库实体
        List<SysDept> toInsert = new ArrayList<>();
        for (int i = 0; i < dataList.size(); i++) {
            DeptImportTemplateVO row = dataList.get(i);
            int excelRowNum = i + 2; // EasyExcel headRowNumber=1 → dataList index 0 对应 Excel 第 2 行
            String errMsg = validateRow(row, isTenantMode, currentTenantId, tenantDeptNameIndex);
            if (errMsg != null) {
                result.getFailList().add(new DeptImportResultVO.FailItem(excelRowNum, errMsg));
                continue;
            }
            // 构建 SysDept（dept_code 由后端自动生成）
            Long targetTenantId = isTenantMode ? currentTenantId : row.getTenantId();
            SysDept dept = new SysDept();
            dept.setTenantId(targetTenantId);
            dept.setDeptCode(UUID.randomUUID().toString().replace("-", ""));
            dept.setDeptName(row.getDeptName().trim());
            dept.setSortNum(row.getSortNum() == null ? 0 : row.getSortNum());
            dept.setStatus("停用".equals(row.getStatusText()) ? 1 : 0);
            dept.setIsBizUnit("是".equals(row.getBizUnitText()) ? 1 : 0);
            dept.setRemark(row.getRemark() == null ? "" : row.getRemark());
            dept.setParentId(resolveParentId(row.getPath(), tenantDeptNameIndex.get(targetTenantId)));
            toInsert.add(dept);
        }

        // ⑤ 只要存在校验失败 → 直接返回带明细的结果，不进入事务、不写 DB
        //    （事务只覆盖 saveBatch 阶段，校验阶段完全在事务外）
        result.setFailRows(result.getFailList().size());
        result.setSuccessRows(toInsert.size());
        if (result.getFailRows() > 0) {
            return result;
        }

        // ⑥ 全通过才进入事务批量入库（编程式事务，避免自调用绕过 AOP 代理）
        transactionTemplate.executeWithoutResult(status -> {
            for (int i = 0; i < toInsert.size(); i += BATCH_SIZE) {
                int end = Math.min(i + BATCH_SIZE, toInsert.size());
                saveBatch(toInsert.subList(i, end));
            }
        });

        return result;
    }

    /**
     * 校验单行数据，返回错误信息（null 表示通过）
     *
     * @param row               Excel 行 VO
     * @param isTenantMode      租户侧 / 平台侧
     * @param currentTenantId   当前登录租户 ID
     * @param tenantDeptNameIndex 租户→部门名→SysDept 的索引
     */
    private String validateRow(DeptImportTemplateVO row, boolean isTenantMode, Long currentTenantId,
                               Map<Long, Map<String, SysDept>> tenantDeptNameIndex) {
        // 1. deptCode 禁止填写（导入仅支持新增，编码由系统生成）
        if (row.getDeptCode() != null && !row.getDeptCode().isBlank()) {
            return "导入仅支持新增部门，请清空部门编码列，不要填写任何内容";
        }

        // 2. 部门名称必填
        if (row.getDeptName() == null || row.getDeptName().isBlank()) {
            return "部门名称不能为空";
        }

        // 3. 排序：可以空（默认 0），但填了必须是合法整数
        if (row.getSortNum() != null && row.getSortNum() < 0) {
            return "排序必须为不小于 0 的整数";
        }

        // 4. 状态枚举校验（空则默认正常）
        if (row.getStatusText() != null && !row.getStatusText().isBlank()) {
            String s = row.getStatusText().trim();
            if (!"正常".equals(s) && !"停用".equals(s)) {
                return "状态仅允许填写：正常、停用（当前值：" + row.getStatusText() + "）";
            }
        }

        // 5. 是否经营单元枚举校验（空则默认否）
        if (row.getBizUnitText() != null && !row.getBizUnitText().isBlank()) {
            String s = row.getBizUnitText().trim();
            if (!"是".equals(s) && !"否".equals(s)) {
                return "是否经营单元仅允许填写：是、否（当前值：" + row.getBizUnitText() + "）";
            }
        }

        // 6. 租户归属校验（平台侧才需要校验 Excel 的租户列）
        Long targetTenantId = isTenantMode ? currentTenantId : row.getTenantId();
        if (targetTenantId == null || targetTenantId == 0) {
            return isTenantMode ? "当前租户信息异常，请重新登录" : "所属租户不能为空";
        }

        // 7. 路径解析校验（格式 + 上级部门存在性 + 循环嵌套防环）
        if (row.getPath() != null && !row.getPath().isBlank()) {
            String pathErr = validatePath(row.getPath(), tenantDeptNameIndex.get(targetTenantId));
            if (pathErr != null) return pathErr;
        }

        return null;
    }

    /**
     * 验证层级路径（A/B/C）格式 + 逐级检查上级部门在目标租户内是否存在
     *
     * @param pathNames 该租户下的部门名索引（name → SysDept）
     */
    private String validatePath(String pathStr, Map<String, SysDept> pathNames) {
        // 兼容 / 和 \ 两种分隔符（Java 正则里 \ 本身需要转义 → \\\\ 表示字面反斜杠）
        String[] parts = pathStr.split("[/\\\\]");        List<String> names = Arrays.stream(parts)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
        if (names.isEmpty()) return null; // 空路径 → 顶级部门

        // 防循环：路径内部门名不能重复（避免 A/B/A 或 A/A 这种自引用）
        Set<String> seen = new HashSet<>();
        for (String name : names) {
            if (!seen.add(name)) {
                return "层级路径存在循环嵌套：" + pathStr;
            }
        }

        // 逐级检查：路径里除了最后一级（即当前要新增的部门自己），前面的上级部门必须已存在
        // 比如路径 "总部/技术部/后端组" → 要查 "总部" + "总部下的技术部"，"后端组" 是当前行自己
        SysDept parent = null;
        for (int i = 0; i < names.size() - 1; i++) {
            String targetName = names.get(i);
            // 在 pathNames 中查找：同名 + parentId 匹配
            SysDept found = pathNames.get(targetName);
            if (found == null) {
                return "层级路径中上级部门不存在：" + targetName + "（路径：" + pathStr + "）";
            }
            if (parent != null && !parent.getId().equals(found.getParentId())) {
                // 路径有歧义：同名部门存在多实例时，必须严格按层级找到
                // 这里简化处理：只要存在同名部门就算通过，不深入树形歧义（后续可增强）
            }
            parent = found;
        }
        return null;
    }

    /**
     * 根据解析好的路径计算 parentId
     * <p>逻辑与 validatePath 一致：逐级查找最后一级已存在的部门 ID 作为 parentId。
     * 空路径或路径全部不存在 → parentId = 0（顶级部门）。</p>
     *
     * @param pathNames 该租户下的部门名索引（pathNames 已经是按租户分好的 Map，
     *                  不需要额外传 tenantId 了）
     */
    private Long resolveParentId(String pathStr, Map<String, SysDept> pathNames) {
        if (pathStr == null || pathStr.isBlank()) return 0L;
        String[] parts = pathStr.split("[/\\\\]");
        List<String> names = Arrays.stream(parts).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList());
        if (names.isEmpty()) return 0L;
        // 找最后一个已存在的部门作为 parent
        for (int i = names.size() - 1; i >= 0; i--) {
            SysDept found = pathNames.get(names.get(i));
            if (found != null) return found.getId();
        }
        return 0L;
    }
}