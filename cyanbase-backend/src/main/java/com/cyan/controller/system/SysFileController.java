package com.cyan.controller.system;

import com.cyan.common.R;
import com.cyan.common.security.util.SecurityUtils;
import com.cyan.entity.SysFile;
import com.cyan.security.annotation.RequiresPermission;
import com.cyan.security.aspect.PermissionGuard;
import com.cyan.security.datascope.ResourceScope;
import com.cyan.service.SysFileService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

/**
 * 文件管理 Controller —— 仅保留上传、下载、预览三件套。
 * <p>
 * 文件列表查询、批量文件管理等接口已下线（返回 403），原因：
 * 1. 独立文件管理菜单无业务价值，所有附件依附于业务单据（表单/工作流/三重一大）
 * 2. 跨租户附件泄露风险 —— 绕过单据 DataScope 隔离直接看全租户附件
 * 3. 删除/编辑必须在业务单据内部操作，不提供独立入口
 * </p>
 * <h3>权限模型</h3>
 * <pre>
 *   上传   → PermissionGuard L1（登录） + L3（system:file:add）
 *   下载   → 查 DB 取 tenantId → PermissionGuard L1（租户归属） + L3（system:file:query）
 *   预览   → 同下载
 *   平台超管通配 → 全部放行
 * </pre>
 */
@Slf4j
@RestController
@RequestMapping("/system/file")
@RequiredArgsConstructor
public class SysFileController {

    private final SysFileService sysFileService;
    private final PermissionGuard permissionGuard;

    // ==== ⛔ 以下接口全部下线，返回 403。附件能力依附于业务单据，不提供独立入口 ====

    @GetMapping("/list")
    public R<?> list() {
        return R.fail(403, "文件列表查询接口已下线，请在业务单据内部管理附件");
    }

    @GetMapping("/{fileId}")
    public R<?> getById(@PathVariable Long fileId) {
        return R.fail(403, "文件详情接口已下线，请通过业务单据查看附件");
    }

    @PostMapping
    public R<?> add() {
        return R.fail(403, "文件新增接口已下线，请使用 /system/file/upload");
    }

    @DeleteMapping("/{fileId}")
    public R<?> delete() {
        return R.fail(403, "文件删除接口已下线，请在业务单据内部删除附件");
    }

    // ==== ✅ 以下为唯一保留的三件套接口 ====

    /**
     * 上传附件 —— 业务表单、工作流、三重一大单据共用。
     * <p>
     * tenantId 由 TenantMetaObjectHandler.insertFill() 自动填充（同源 CustomTenantHandler.resolveTenantId()），
     * 不允许客户端指定 —— 防止伪造租户归属。
     * createBy 手动填 SecurityUtils.getUsername()，比 MetaObjectHandler 更可控。
     * </p>
     */
    @PostMapping("/upload")
    @RequiresPermission("system:file:add")
    public R<?> upload(@RequestParam("file") MultipartFile file) throws Exception {
        permissionGuard.check(ResourceScope.of(null, null, null));

        String basePath = System.getProperty("user.dir") + "/upload/";
        File dir = new File(basePath);
        if (!dir.exists()) dir.mkdirs();

        String originalName = file.getOriginalFilename();
        String suffix = originalName.substring(originalName.lastIndexOf("."));
        String uuidName = UUID.randomUUID() + suffix;
        File saveFile = new File(basePath + uuidName);
        file.transferTo(saveFile);

        SysFile sysFile = new SysFile();
        sysFile.setFileName(uuidName);
        sysFile.setFileOriginalName(originalName);
        sysFile.setFileSuffix(suffix.replace(".", ""));
        sysFile.setFileSize(String.valueOf(file.getSize()));
        sysFile.setStorageType("LOCAL");
        sysFile.setFileUrl("/upload/" + uuidName);
        sysFile.setCreateBy(SecurityUtils.getUsername());
        sysFile.setCreateTime(LocalDateTime.now());
        // tenantId 由 TenantMetaObjectHandler 自动填充，不手动 set —— 防伪造

        sysFileService.save(sysFile);
        log.info("[文件上传] fileId={}, originalName={}, tenantId={}, createBy={}",
                sysFile.getFileId(), originalName, sysFile.getTenantId(), sysFile.getCreateBy());
        return R.ok(sysFile);
    }

    /**
     * 下载附件 —— 租户只能下载归属自己租户的文件。
     * <p>
     * 权限：PermissionGuard L1 校验租户归属 + MP 多租户拦截器自动加 tenant_id 过滤。
     * 不引入 system:file:query 按钮权限码，避免角色漏分配导致上传后下载不了。
     * </p>
     */
    @GetMapping("/download/{fileId}")
    public void download(@PathVariable Long fileId, HttpServletResponse resp) throws Exception {
        SysFile file = sysFileService.getById(fileId);
        if (file == null) { resp.sendError(404, "文件不存在"); return; }
        // L1 租户归属校验 —— 跨租户越权拦截
        permissionGuard.check(ResourceScope.of(file.getTenantId(), null, null));

        File localFile = new File(System.getProperty("user.dir") + "/upload/" + file.getFileName());
        if (!localFile.exists()) { resp.sendError(404, "服务器磁盘上文件已丢失"); return; }

        String encoded = URLEncoder.encode(file.getFileOriginalName(), StandardCharsets.UTF_8).replace("+", "%20");
        resp.setContentType("application/octet-stream");
        resp.setHeader("Content-Disposition", "attachment; filename=\"" + encoded + "\"; filename*=UTF-8''" + encoded);
        resp.setContentLengthLong(localFile.length());

        try (FileInputStream fis = new FileInputStream(localFile); OutputStream os = resp.getOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = fis.read(buf)) != -1) os.write(buf, 0, n);
            os.flush();
        }
    }

    /**
     * 预览附件 —— 仅支持图片/PDF 等浏览器可渲染的格式。其他格式返回提示。
     * <p>
     * 权限同 download：PermissionGuard L1 租户归属 + MP 拦截。
     * </p>
     */
    @GetMapping("/preview/{fileId}")
    public void preview(@PathVariable Long fileId, HttpServletResponse resp) throws Exception {
        SysFile file = sysFileService.getById(fileId);
        if (file == null) { resp.sendError(404, "文件不存在"); return; }
        permissionGuard.check(ResourceScope.of(file.getTenantId(), null, null));

        File localFile = new File(System.getProperty("user.dir") + "/upload/" + file.getFileName());
        if (!localFile.exists()) { resp.sendError(404, "服务器磁盘上文件已丢失"); return; }

        // 白名单：浏览器能直接 inline 渲染的格式
        Set<String> inlineSuffixes = Set.of(
                "jpg", "jpeg", "png", "gif", "bmp", "webp", "svg", "ico",
                "pdf",
                "txt", "json", "xml", "csv", "html", "htm",
                "mp4", "webm", "ogg", "mp3", "wav"
        );
        String suffix = file.getFileSuffix().toLowerCase();
        if (!inlineSuffixes.contains(suffix)) {
            resp.setContentType("text/plain;charset=UTF-8");
            resp.getWriter().write("该文件类型不支持在线预览，请使用下载功能。");
            resp.getWriter().flush();
            return;
        }

        // 根据后缀推断 Content-Type（简洁版，够用）
        String contentType = switch (suffix) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "bmp" -> "image/bmp";
            case "webp" -> "image/webp";
            case "svg" -> "image/svg+xml";
            case "pdf" -> "application/pdf";
            case "txt", "json", "xml", "csv", "html", "htm" -> "text/plain;charset=UTF-8";
            case "mp4" -> "video/mp4";
            case "webm" -> "video/webm";
            case "mp3" -> "audio/mpeg";
            default -> "application/octet-stream";
        };

        resp.setContentType(contentType);
        resp.setHeader("Content-Disposition", "inline; filename=\"" + URLEncoder.encode(file.getFileOriginalName(), StandardCharsets.UTF_8) + "\"");
        resp.setContentLengthLong(localFile.length());

        try (FileInputStream fis = new FileInputStream(localFile); OutputStream os = resp.getOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = fis.read(buf)) != -1) os.write(buf, 0, n);
            os.flush();
        }
    }
}