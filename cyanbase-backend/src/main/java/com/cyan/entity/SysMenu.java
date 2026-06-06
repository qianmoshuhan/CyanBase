package com.cyan.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.cyan.common.util.TreeUtil;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("sys_menu")
public class SysMenu implements Serializable, TreeUtil {

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

    // 树形子节点
    @TableField(exist = false)
    private List<SysMenu> children;

    @Override
    public List<? extends TreeUtil> getChildren() {
        return this.children;
    }

    @Override
    public void setChildren(List<? extends TreeUtil> children) {
        this.children = (List<SysMenu>) children;
    }
}