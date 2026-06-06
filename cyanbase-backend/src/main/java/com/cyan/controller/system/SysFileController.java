package com.cyan.controller.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cyan.common.R;
import com.cyan.entity.SysFile;
import com.cyan.service.SysFileService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.File;
import java.time.LocalDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/system/file")
@RequiredArgsConstructor
public class SysFileController {
    private final SysFileService sysFileService;

    @GetMapping("/list")
    public R<?> list(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String fileName) {
        LambdaQueryWrapper<SysFile> wrapper = new LambdaQueryWrapper<>();
        if (fileName != null && !fileName.isEmpty()) wrapper.like(SysFile::getFileName, fileName);
        wrapper.orderByDesc(SysFile::getFileId);
        return R.ok(sysFileService.page(new Page<>(current, size), wrapper));
    }

    @GetMapping("/{fileId}")
    public R<?> getById(@PathVariable Long fileId) {
        return R.ok(sysFileService.getById(fileId));
    }

    @PostMapping
    public R<?> add(@RequestBody SysFile file) {
        sysFileService.save(file);
        return R.ok("上传成功");
    }

    @DeleteMapping("/{fileId}")
    public R<?> delete(@PathVariable Long fileId) {
        sysFileService.removeById(fileId);
        return R.ok("删除成功");
    }

    @PostMapping("/upload")
    public R<?> upload(@RequestParam("file") MultipartFile file) throws Exception {
        //1.定义本地保存路径
        String basePath = System.getProperty("user.dir") + "/upload/";
        File dir = new File(basePath);
        if (!dir.exists()) dir.mkdirs();

        //2.拆分文件名、后缀
        String originalName = file.getOriginalFilename();
        String suffix = originalName.substring(originalName.lastIndexOf("."));
        String uuidName = UUID.randomUUID() + suffix;
        File saveFile = new File(basePath + uuidName);
        file.transferTo(saveFile);

        //3.组装sys_file实体，存入数据库（完全匹配你SysFile实体+sys_file表）
        SysFile sysFile = new SysFile();
        sysFile.setFileName(uuidName);
        sysFile.setFileOriginalName(originalName);
        sysFile.setFileSuffix(suffix.replace(".",""));
        sysFile.setFileSize(String.valueOf(file.getSize()));
        sysFile.setStorageType("LOCAL");
        sysFile.setFileUrl("http://localhost:8090/upload/"+uuidName);
        sysFile.setCreateBy("admin"); //后期登录后替换当前登录用户名，先固定admin
        sysFile.setCreateTime(LocalDateTime.now());

        //4.保存入库
        sysFileService.save(sysFile);
        return R.ok("上传成功");
    }
}
