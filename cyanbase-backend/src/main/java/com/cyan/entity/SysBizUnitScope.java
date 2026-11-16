package com.cyan.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户-经营单元-角色 关联绑定表（纯中间表，无 tenant_id）
 * <p>
 * 设计说明：
 * <ul>
 *   <li>和 principal_tenant_scope 范式保持一致——绑定关系依托用户本身归属租户，
 *       业务层做跨租户校验即可，该表加入 IGNORED_TABLES 不参与租户拦截</li>
 *   <li>同一用户可以绑定多个经营单元 + 多个角色，用联合唯一约束防重复</li>
 * </ul>
 */
@Data
@TableName("sys_biz_unit_scope")
public class SysBizUnitScope implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户ID（sys_user.id） */
    @TableField("user_id")
    private Long userId;

    /** 经营单元部门ID（sys_dept.id，必须 is_biz_unit=1） */
    @TableField("biz_unit_id")
    private Long bizUnitId;

    /** 追加角色ID（sys_role.id） */
    @TableField("role_id")
    private Long roleId;

    @TableField("create_by")
    private String createBy;

    @TableField("create_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @TableField("update_by")
    private String updateBy;

    @TableField("update_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
