package com.cyan.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("sys_user")
public class SysUser implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 租户ID。
     * <p>
     * ⭐ 使用 ALWAYS 策略：INSERT 时永远显式包含该字段（值为 null 也会写 tenant_id=NULL），
     * 而不是因 MP 默认 NOT_NULL 策略跳过 null 字段，触发数据库 DEFAULT 0 兜底。
     * 平台主体场景需要 tenant_id=NULL（身份标识），必须跳过 TenantMetaObjectHandler 自动填充。
     */
    @TableField(insertStrategy = FieldStrategy.ALWAYS)
    private Long tenantId;

    /** 部门ID */
    private Long deptId;

    /** 岗位ID */
    private Long postId;

    /** 用户名 */
    @TableField("user_name")
    private String userName;

    /**
     * 密码（BCrypt 哈希）。
     * <p>
     * ⚠️ 安全：只允许「写入」不允许「序列化输出」。
     * 之前 GET /auth/getUserInfo、GET /system/user/list 等出参会把 BCrypt 哈希原样返回给前端，
     * 属于敏感信息泄露。WRITE_ONLY 保证：
     * <ul>
     *   <li>入参（新增/编辑用户 @RequestBody SysUser）的 password 仍可正常反序列化；</li>
     *   <li>所有出参 JSON 不再包含 password 字段。</li>
     * </ul>
     * 重置密码接口返回的是 {@code R<Map<String,String>>} 里的明文新密码，不受本注解影响。
     */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;

    /** 昵称 */
    @TableField("nick_name")
    private String nickName;

    /** 手机号 */
    private String phone;

    /** 邮箱 */
    private String email;

    /** 头像 */
    private String avatar;

    /** 性别 0男 1女 2未知 */
    private Integer sex;

    /** 状态 0正常 1停用 */
    private Integer status;

    /** 登录IP */
    @TableField("login_ip")
    private String loginIp;

    /** 登录时间 */
    @TableField("login_time")
    private LocalDateTime loginTime;

    /** 创建者 */
    @TableField("create_by")
    private String createBy;

    /** 创建时间 */
    @TableField("create_time")
    private LocalDateTime createTime;

    /** 更新者 */
    @TableField("update_by")
    private String updateBy;

    /** 更新时间 */
    @TableField("update_time")
    private LocalDateTime updateTime;

    /** 删除标志 0正常 1删除 */
    @TableLogic
    @TableField("del_flag")
    private Integer delFlag;

    /** 备注 */
    private String remark;

    /** 前端传入选中角色id集合，非数据库字段 */
    @TableField(exist = false)
    private List<Long> roleIds;

    // 在 roleIds 后面加：

    /** 所属租户名称（非数据库字段，list 查询时关联 sys_tenant 填充） */
    @TableField(exist = false)
    private String tenantName;

    /** 用户所属角色名称列表字符串（非数据库字段，list 查询时批量填充，多个逗号拼接） */
    @TableField(exist = false)
    private String roleNames;

    /** 用户所属部门名称（非数据库字段，list 查询时关联 sys_dept 填充） */
    @TableField(exist = false)
    private String deptName;

    /** 用户所属岗位名称（非数据库字段，list 查询时关联 sys_post 填充） */
    @TableField(exist = false)
    private String postName;
}