package com.cyan.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("sys_dept")
public class SysDept implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 租户ID */
    private Long tenantId;

    /**
     * 部门编码（技术字段，租户内唯一，导入导出时用于替代自增 ID 做父子引用）
     * 前端完全无感：@JsonIgnore 阻止所有 JSON 接口序列化输出，
     * 新增/编辑时 Controller 在 insert 前自动生成，不接受前端传入值。
     */
    @JsonIgnore
    @TableField("dept_code")
    private String deptCode;

    /** 父部门ID */
    @TableField("parent_id")
    private Long parentId;

    /** 部门名称 */
    @TableField("dept_name")
    private String deptName;

    /** 排序 */
    @TableField("sort_num")
    private Integer sortNum;

    /** 状态 0正常 1停用 */
    private Integer status;

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

    /** 是否独立法人经营单元 0-否 1-是 */
    @TableField("is_biz_unit")
    private Integer isBizUnit;

    /** 备注 */
    private String remark;

    private Integer isDeleted;  
}