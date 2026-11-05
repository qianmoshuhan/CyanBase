package com.cyan.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.cyan.common.util.TreeUtil;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("sys_menu")
public class SysMenu implements Serializable, TreeUtil<SysMenu> {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long parentId;
    private String menuName;
    private Integer menuType;
    private String routePath;
    private String component;
    private String perms;
    private String icon;
    private Integer sortNum;
    private Integer visible;
    private Integer status;
    private String createBy;
    private LocalDateTime createTime;
    private String updateBy;
    private LocalDateTime updateTime;
    private Integer delFlag;
    private String remark;

    // 树形子节点 —— 后端建树用虚拟字段，Lombok @Data 自动生成 getter/setter
    @TableField(exist = false)
    private List<SysMenu> children;
}