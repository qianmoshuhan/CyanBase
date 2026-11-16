package com.cyan.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 平台侧 /system/user/{id}/detail 接口返回结构（只读聚合，与 list 解耦）
 */
@Data
public class UserDetailVO {
    // ====== 一、基础信息 ======
    private Long id;
    private String userName;
    private String nickName;
    private String phone;
    private String email;
    private Integer status;

    // ====== 二、归属信息 ======
    private Long tenantId;
    private String tenantName;
    private Long deptId;
    private String deptName;
    private Long postId;
    private String postName;

    // ====== 三、权限信息 ======
    /** 当前生效的最严格数据范围文本（如 "仅本人" / "本部门及下属"） */
    private String effectiveDataScope;
    /** 已分配角色（区分平台/租户/部门私有三类） */
    private List<UserRoleItemVO> roles;

    // ====== 四、运维信息 ======
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime loginTime;
    private String loginIp;
    /** 密码修改时间（项目未单独存储此字段，暂返回 null 前端显示"未记录"） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime pwdUpdateTime;
    /** 权限版本号（从 Redis sys:user:perm:ver:{userId} 读取，null 表示初始化或无变更） */
    private Long permVer;
    /** 创建人 */
    private String createBy;

    // ====== 五、预留扩展字段（后续挂工号/职级等自定义字段，无需改 VO 结构） ======
    /** 扩展 JSON 字段，可灵活挂载自定义扩展 */
    private String extJson;
}
