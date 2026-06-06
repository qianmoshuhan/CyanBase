package com.cyan.common.util;

import java.util.List;

public interface TreeUtil {

    Long getId();
    Long getParentId();
    void setChildren(List<? extends TreeUtil> children);
    List<? extends TreeUtil> getChildren();
}