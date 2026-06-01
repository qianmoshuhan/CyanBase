package com.cyan.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("sys_menu")
public class SysMenu implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 父菜单ID */
    @TableField("parent_id")
    private Long parentId;

    /** 菜单名称 */
    @TableField("menu_name")
    private String menuName;

    /** 菜单类型 1目录 2菜单 3按钮 */
    @TableField("menu_type")
    private Integer menuType;

    /** 路由地址 */
    @TableField("route_path")
    private String routePath;

    /** 组件路径 */
    private String component;

    /** 权限标识 */
    private String perms;

    /** 图标 */
    private String icon;

    /** 排序 */
    @TableField("sort_num")
    private Integer sortNum;

    /** 显示状态 0显示 1隐藏 */
    private Integer visible;

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

    /** 备注 */
    private String remark;
}