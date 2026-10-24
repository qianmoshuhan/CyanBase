package com.cyan.common.spel;

import org.springframework.expression.AccessException;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.PropertyAccessor;
import org.springframework.expression.TypedValue;

import java.util.Map;

/**
 * ⭐ 自定义 Map PropertyAccessor
 * Spring 旧版本没有内置 MapAccessor，这个类告诉 SpEL：
 * 遇到 Map 时用 map.get(key) 取值，而不是按 JavaBean getter 找方法
 *
 * 这样 SpEL 就能直接写裸 fieldKey：
 *   Fwgpmvjh7a9fajc >= 1000000  ✅ 自动从 vars Map 里取
 *
 * 而不是必须写：
 *   #variables.Fwgpmvjh7a9fajc >= 1000000
 */
public class MapPropertyAccessor implements PropertyAccessor {

    @Override
    public Class<?>[] getSpecificTargetClasses() {
        return new Class<?>[]{Map.class};
    }

    @Override
    public boolean canRead(EvaluationContext context, Object target, String name) throws AccessException {
        return target instanceof Map;
    }

    @Override
    public TypedValue read(EvaluationContext context, Object target, String name) throws AccessException {
        Map<?, ?> map = (Map<?, ?>) target;
        return new TypedValue(map.get(name));
    }

    @Override
    public boolean canWrite(EvaluationContext context, Object target, String name) throws AccessException {
        return false;
    }

    @Override
    public void write(EvaluationContext context, Object target, String name, Object newValue) throws AccessException {
        throw new UnsupportedOperationException("MapPropertyAccessor is read-only");
    }
}
