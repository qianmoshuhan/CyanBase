package com.cyan.common.util;

import java.util.List;

/**
 * 树节点通用接口 —— 泛型化后让实现类可以用具体类型。
 * <p>
 * 之前的 List<? extends TreeUtil> 签名和 Lombok @Data 生成的 List<SysMenu> 签名冲突，
 * JVM 里会有两个同参不同类型的方法，Jackson 扫描时随机命中导致 500 或 children 丢失。
 * 改成泛型后 SysMenu implements TreeUtil<SysMenu>，Lombok 生成的 getter 自动满足接口契约，
 * 不再需要手写 override，JVM 里只有一个正确类型的方法。
 * </p>
 */
public interface TreeUtil<T> {

    Long getId();
    Long getParentId();
    void setChildren(List<T> children);
    List<T> getChildren();
}