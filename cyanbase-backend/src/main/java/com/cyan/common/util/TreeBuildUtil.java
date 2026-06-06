package com.cyan.common.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TreeBuildUtil {
    // 入参出参泛型完美匹配TreeUtil接口，调用代码不用改任何一行！
    @SuppressWarnings("unchecked")
    public static <T extends TreeUtil> List<T> build(List<T> list, long rootParentId) {
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
                    // getChildren返回List<? extends TreeUtil>，强转塞入
                    ((List<T>)parent.getChildren()).add(node);
                }
            }
        }
        return root;
    }
}