package com.cyan.controller;

import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cyan.common.R;
import com.cyan.common.log.annotation.Log;
import com.cyan.common.log.enums.BusinessType;
import com.cyan.common.security.util.SecurityUtils;
import com.cyan.dto.DeptExportVO;
import com.cyan.dto.DeptImportResultVO;
import com.cyan.dto.DeptImportTemplateVO;
import com.cyan.excel.ImportTemplateHandler;
import org.springframework.web.multipart.MultipartFile;
import com.cyan.entity.SysDept;
import com.cyan.mapper.SysBizUnitScopeMapper;
import com.cyan.service.SysDeptService;
import com.cyan.security.annotation.RequiresPermission;
import com.cyan.security.aspect.PermissionGuard;
import com.cyan.security.datascope.ResourceScope;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/system/dept")
@RequiredArgsConstructor
public class SysDeptController {

    private final SysDeptService sysDeptService;
    private final PermissionGuard permissionGuard;
    private final SysBizUnitScopeMapper bizUnitScopeMapper;

    @RequiresPermission("system:dept:list")
    @GetMapping("/list")
    public R<Page<SysDept>> list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String deptName,
            @RequestParam(required = false) Integer status) {

        LambdaQueryWrapper<SysDept> wrapper = new LambdaQueryWrapper<>();
        if (deptName != null && !deptName.isEmpty()) {
            wrapper.like(SysDept::getDeptName, deptName);
        }
        if (status != null) {
            wrapper.eq(SysDept::getStatus, status);
        }
        wrapper.orderByAsc(SysDept::getSortNum);

        Page<SysDept> page = new Page<>(pageNum, pageSize);
        sysDeptService.page(page, wrapper);
        return R.ok(page);
    }

    /**
     * 全量部门（树形表格 + 搜索用）
     * <p>
     * 支持 deptName（模糊）、status（精确）、tenantId（精确）三个可选过滤条件。
     * 树形搜索经典问题：只返回匹配节点会导致父节点缺失、树断链。
     * 解法：先查隔离范围内全量 → 内存过滤匹配节点 → 递归向上补齐祖先链 → 返回完整集合。
     * </p>
     */
    @GetMapping("/all")
    public R<?> all(
            @RequestParam(required = false) String deptName,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Long tenantId) {

        // 1. 先确定「查询范围」：tenantId 过滤 + status 过滤（注意：有 deptName 时不在这里按 status 过滤，
        //    否则会把祖先节点也过滤掉，导致树断链。祖先节点的 status 不影响自身展示）
        LambdaQueryWrapper<SysDept> scopeWrapper = new LambdaQueryWrapper<>();
        if (tenantId != null) scopeWrapper.eq(SysDept::getTenantId, tenantId);
        // 如果没有 deptName 搜索条件，直接按 status 过滤全量（保持原有行为）
        if (deptName == null || deptName.isEmpty()) {
            if (status != null) scopeWrapper.eq(SysDept::getStatus, status);
        }
        scopeWrapper.orderByAsc(SysDept::getSortNum);
        List<SysDept> scopeList = sysDeptService.list(scopeWrapper);

        // 2. 如果没有任何过滤条件，直接返回（最优路径）
        if ((deptName == null || deptName.isEmpty()) && status == null) {
            return R.ok(scopeList);
        }

        // 3. 构建内存 Map 加速查找
        Map<Long, SysDept> idMap = scopeList.stream()
                .collect(Collectors.toMap(SysDept::getId, d -> d));

        // 4. 先找出「命中节点」：deptName 模糊匹配 + status 精确匹配（只对命中节点校验 status）
        Set<Long> hitIds = new HashSet<>();
        for (SysDept d : scopeList) {
            boolean nameHit = deptName == null || deptName.isEmpty()
                    || (d.getDeptName() != null && d.getDeptName().contains(deptName));
            boolean statusHit = status == null || Objects.equals(d.getStatus(), status);
            if (nameHit && statusHit) {
                hitIds.add(d.getId());
            }
        }

        // 5. 对每个命中节点，递归向上补齐祖先链（保证树不断链）
        Set<Long> finalIds = new HashSet<>(hitIds);
        for (Long hitId : hitIds) {
            SysDept cur = idMap.get(hitId);
            while (cur != null && cur.getParentId() != null && cur.getParentId() != 0) {
                finalIds.add(cur.getParentId());
                cur = idMap.get(cur.getParentId());
            }
        }

        // 6. 收集最终结果，按 sortNum 排序
        List<SysDept> result = scopeList.stream()
                .filter(d -> finalIds.contains(d.getId()))
                .sorted(Comparator.comparingInt(d -> d.getSortNum() != null ? d.getSortNum() : 0))
                .collect(Collectors.toList());

        return R.ok(result);
    }

    @GetMapping("/{id}")
    public R<SysDept> getById(@PathVariable Long id) {
        SysDept dept = sysDeptService.getById(id);
        if (dept == null) return R.fail("部门不存在");
        // 读操作：L1 租户归属 + L2 DataScope（targetDeptId 即自身部门 id）
        permissionGuard.check(ResourceScope.of(dept.getTenantId(), dept.getId(), dept.getCreateBy()));
        return R.ok(dept);
    }

    @PostMapping
    @RequiresPermission("system:dept:add")
    public R<Void> add(@RequestBody SysDept sysDept) {
        sysDept.setId(null);
        // ⭐ 自动生成 dept_code：技术字段，前端传了也被覆盖，保证租户内全局唯一
        sysDept.setDeptCode(generateDeptCode());
        Long userId = SecurityUtils.getUserId();

        // === 三层原子校验：预期目标 = 前端指定租户 + 父部门ID + 当前操作用户 ===
        permissionGuard.check(ResourceScope.of(sysDept.getTenantId(), sysDept.getParentId(), SecurityUtils.getUsername()),
                              "system:dept:add");

        return sysDeptService.save(sysDept) ? R.ok() : R.fail("新增失败");
    }

    @PutMapping
    @RequiresPermission("system:dept:edit")
    public R<Void> update(@RequestBody SysDept sysDept) {
        SysDept exist = sysDeptService.getById(sysDept.getId());
        if (exist == null) return R.fail("部门不存在");
        // 写操作：完整三层 L1 租户归属 + L2 DataScope + L3 system:dept:edit
        permissionGuard.check(ResourceScope.of(exist.getTenantId(), exist.getId(), exist.getCreateBy()),
                              "system:dept:edit");

        // ⭐ 经营单元绑定校验：原 isBizUnit=1 → 新值=0 时，禁止取消已存在绑定的经营单元标记
        if (Integer.valueOf(1).equals(exist.getIsBizUnit())
                && sysDept.getIsBizUnit() != null && sysDept.getIsBizUnit() == 0) {
            long bindCount = bizUnitScopeMapper.selectCount(
                    new LambdaQueryWrapper<com.cyan.entity.SysBizUnitScope>()
                            .eq(com.cyan.entity.SysBizUnitScope::getBizUnitId, exist.getId())
            );
            if (bindCount > 0) {
                return R.fail("当前经营单元已存在用户绑定关系（" + bindCount + " 条），请先解绑后再取消标记");
            }
        }

        return sysDeptService.updateById(sysDept) ? R.ok() : R.fail("更新失败");
    }

    @DeleteMapping("/{id}")
    @RequiresPermission("system:dept:delete")
    public R<Void> delete(@PathVariable Long id) {
        SysDept exist = sysDeptService.getById(id);
        if (exist == null) return R.fail("部门不存在");
        // 写操作：完整三层校验
        permissionGuard.check(ResourceScope.of(exist.getTenantId(), exist.getId(), exist.getCreateBy()),
                              "system:dept:delete");
        // 1. 查询是否存在子部门
        LambdaQueryWrapper<SysDept> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SysDept::getParentId, id);
        queryWrapper.eq(SysDept::getIsDeleted, 0);
        long count = sysDeptService.count(queryWrapper);

        // 2. 有子部门禁止删除
        if (count > 0) {
            return R.fail("该部门下存在子部门，无法删除！");
        }

        // 3. 执行删除
        boolean result = sysDeptService.removeById(id);
        return result ? R.ok() : R.fail("删除失败");
    }

    /**
     * ⭐ 远程搜索部门（工作流节点配置下拉用）
     * 自动按当前登录租户过滤
     */
    @GetMapping("/search")
    public R<List<Map<String, Object>>> searchDepts(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "50") Integer limit) {
        LambdaQueryWrapper<SysDept> wrapper = new LambdaQueryWrapper<>();
        Long tenantId = SecurityUtils.getTenantId();
        if (tenantId != null) wrapper.eq(SysDept::getTenantId, tenantId);
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.like(SysDept::getDeptName, keyword);
        }
        wrapper.eq(SysDept::getStatus, 0).orderByAsc(SysDept::getSortNum).last("limit " + limit);
        List<Map<String, Object>> result = sysDeptService.list(wrapper).stream()
                .map(d -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", d.getId());
                    m.put("label", d.getDeptName());
                    return m;
                }).collect(Collectors.toList());
        return R.ok(result);
    }

    /**
     * 部门数据导出 —— 平台侧
     * <p>
     * 权限：@RequiresPermission("system:dept:list") 有列表查看权即可导出（不新增独立 export 权限点）。
     * 参数：deptName（模糊）、status（精确）、tenantId（精确，可选）。
     * 数据安全：SysDeptServiceImpl.buildExportData 依赖 MyBatis-Plus 拦截器链在 SQL 层自动追加
     *   tenant_id 隔离 + DataScope 过滤，查出的数据天然在当前操作权限范围内。
     * 超大数据量（>5000）抛 BusinessException 给前端友好提示。
     * </p>
     */
    @Log(title = "部门管理", businessType = BusinessType.EXPORT, isSaveRequestData = false, isSaveResponseData = false)
    @RequiresPermission("system:dept:list")
    @GetMapping("/export")
    public void export(
            @RequestParam(required = false) String deptName,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Long tenantId,
            HttpServletResponse resp) throws Exception {

        // ① 生成批次号（log.info 审计用）：DEPT-yyyyMMdd-HHmmss-8位随机串
        String batchNo = "DEPT-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
                + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);

        try {
            // ② 构造导出数据（Service 内部已做阈值校验 + 层级路径构造 + 枚举翻译）
            List<DeptExportVO> voList = sysDeptService.buildExportData(deptName, status, tenantId);

            // ③ 构造文件名 + 设置响应头（对齐 SysFileController.download 模板）
            String fileName = "部门数据_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".xlsx";
            String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
            resp.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            resp.setCharacterEncoding("utf-8");
            resp.setHeader("Content-Disposition", "attachment; filename=\"" + encoded + "\"; filename*=UTF-8''" + encoded);

            // ④ EasyExcel 4.0.3 流式写入（doWrite 自动关闭 OutputStream）
            EasyExcel.write(resp.getOutputStream(), DeptExportVO.class)
                    .sheet("部门数据")
                    .doWrite(voList);

            // ⑤ 审计日志（批次号 + 操作人 + 租户 + 条数）
            Long exportTenantId = tenantId != null ? tenantId : SecurityUtils.getTenantId();
            log.info("[部门导出完成][平台侧] batchNo={}, operName={}, operId={}, tenantId={}, exportCount={}",
                    batchNo, SecurityUtils.getUsername(), SecurityUtils.getUserId(), exportTenantId, voList.size());

        } catch (Exception e) {
            log.error("[部门导出失败][平台侧] batchNo={}, operName={}, tenantId={}, error={}",
                    batchNo, SecurityUtils.getUsername(), SecurityUtils.getTenantId(), e.getMessage(), e);
            // 异常时返回错误信息（JSON）而非 Excel，让前端能解析错误提示
            resp.resetBuffer();
            resp.setContentType("application/json;charset=UTF-8");
            resp.getWriter().write("{\"code\":500,\"msg\":\"导出失败：" + e.getMessage() + "\"}");
            resp.flushBuffer();
        }
    }

    /**
     * 下载部门导入模板 —— 平台侧
     * <p>
     * 模板特性：
     * <ul>
     *   <li>表头与导出 Excel 完全一致（DeptImportTemplateVO 列序对齐 DeptExportVO）</li>
     *   <li>表头上方加灰色说明行（ImportTemplateHandler 写入）</li>
     *   <li>状态列 / 是否经营单元列加 Excel 下拉（正常/停用、是/否）</li>
     * </ul>
     * 本接口只返回空模板，不做实际导入逻辑 —— 下一阶段再实现 upload 接口。
     * </p>
     */
    @Log(title = "部门导入模板下载", businessType = BusinessType.IMPORT, isSaveRequestData = false, isSaveResponseData = false)
    @RequiresPermission("system:dept:list")
    @GetMapping("/importTemplate")
    public void importTemplate(HttpServletResponse resp) throws Exception {
        String fileName = "部门导入模板.xlsx";
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        resp.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        resp.setCharacterEncoding("utf-8");
        resp.setHeader("Content-Disposition", "attachment; filename=\"" + encoded + "\"; filename*=UTF-8''" + encoded);

        // EasyExcel 默认 headRowNumber=1（仅 1 行表头），ImportTemplateHandler 给状态/经营单元列加下拉
        EasyExcel.write(resp.getOutputStream(), DeptImportTemplateVO.class)
                .registerWriteHandler(new ImportTemplateHandler())
                .sheet("部门导入模板")
                .doWrite(java.util.Collections.emptyList()); // 空模板，不写任何数据行

        log.info("[部门导入模板下载][平台侧] operName={}, operId={}, tenantId={}",
                SecurityUtils.getUsername(), SecurityUtils.getUserId(), SecurityUtils.getTenantId());
    }

    /**
     * 部门数据导入 —— 平台侧
     * <p>
     * 仅接受 .xlsx 文件；读取 Excel 【所属租户】列作为目标归属租户。
     * 全部行校验通过才统一入库（事务），任意一行出错整体回滚。
     * 返回结果带 Excel 真实行号的失败明细，供前端弹窗展示。
     * </p>
     */
    @Log(title = "部门导入", businessType = BusinessType.IMPORT, isSaveRequestData = false, isSaveResponseData = false)
    @RequiresPermission("system:dept:list")
    @PostMapping("/import")
    public R<DeptImportResultVO> importDept(@RequestParam("file") MultipartFile file) throws Exception {
        // ① 文件格式校验
        if (file == null || file.isEmpty()) {
            return R.fail("请选择要导入的文件");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".xlsx")) {
            return R.fail("仅支持 .xlsx 格式文件，请将旧版 .xls 另存为 .xlsx 后再导入");
        }

        // ② 批次号（审计用）
        String batchNo = "DEPT-IMPORT-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
                + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);

        try {
            DeptImportResultVO result = sysDeptService.importDept(false, null, file.getInputStream());

            // ③ 审计日志
            log.info("[部门导入完成][平台侧] batchNo={}, operName={}, operId={}, total={}, success={}, fail={}",
                    batchNo, SecurityUtils.getUsername(), SecurityUtils.getUserId(),
                    result.getTotalRows(), result.getSuccessRows(), result.getFailRows());

            return R.ok(result);
        } catch (Exception e) {
            log.error("[部门导入失败][平台侧] batchNo={}, operName={}, error={}",
                    batchNo, SecurityUtils.getUsername(), e.getMessage(), e);
            throw e; // 抛出让全局异常处理统一返回失败
        }
    }

    /** 生成 dept_code：UUID 去横线，32 位小写。应用层不查库判重，依赖数据库 uk_tenant_dept_code 唯一索引兜底。 */
    private String generateDeptCode() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}