package com.cyan.common.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TreeBuildUtil {
    /**
     * 构建树形结构。泛型约束 T extends TreeUtil<T> 保证节点类型与 children 元素类型一致。
     * 泛型化 TreeUtil 后不再需要 (List<T>)parent.getChildren() 的强转。
     */
    public static <T extends TreeUtil<T>> List<T> build(List<T> list, long rootParentId) {
        if(list == null || list.isEmpty()) return new ArrayList<>();

        Map<Long, T> idMap = new HashMap<>(list.size());
        for (T node : list) {
            idMap.put(node.getId(), node);
            node.setChildren(new ArrayList<>());
        }

        List<T> root = new ArrayList<>();
        for (T node : list) {
            Long pid = node.getParentId();
            if(pid == rootParentId){
                root.add(node);
            }else{
                T parent = idMap.get(pid);
                if(parent != null){
                    parent.getChildren().add(node);  // ← 不再需要 unchecked 强转
                }
            }
        }
        return root;
    }
}