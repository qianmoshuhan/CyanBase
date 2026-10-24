package com.cyan.common.enums;

/**
 * 数据权限范围枚举（对齐 SysRole.dataScope 字段值）
 * <pre>
 * 1 = 全部（本租户全部，TenantLineInnerInterceptor 已保证租户隔离）
 * 2 = 本部门
 * 3 = 本部门及下属（递归子部门）
 * 4 = 仅本人（create_by = 当前登录用户名）
 * 5 = 自定义（sys_role_dept 手动绑定部门）
 * </pre>
 */
public enum DataScopeEnum {
    ALL(1, "全部"),
    DEPT(2, "本部门"),
    DEPT_AND_CHILD(3, "本部门及下属"),
    SELF(4, "仅本人"),
    CUSTOM(5, "自定义");

    public final int value;
    public final String label;

    DataScopeEnum(int value, String label) {
        this.value = value;
        this.label = label;
    }

    public static DataScopeEnum of(int v) {
        for (DataScopeEnum e : values()) {
            if (e.value == v) return e;
        }
        return ALL;
    }
}
