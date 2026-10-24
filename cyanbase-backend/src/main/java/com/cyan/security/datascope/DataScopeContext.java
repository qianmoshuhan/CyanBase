package com.cyan.security.datascope;

/**
 * 数据权限拦截器的编程式上下文（ThreadLocal）
 * <p>
 * 用法：
 * <pre>
 * DataScopeContext.skip();
 * try { ... 全量查询逻辑 ... }
 * finally { DataScopeContext.reset(); }
 * </pre>
 * 或用注解 {@link SkipDataScope} 做类/方法级跳过。
 */
public final class DataScopeContext {

    private static final ThreadLocal<Boolean> SKIP = ThreadLocal.withInitial(() -> false);

    /** 进入跳过模式（之后所有 SQL 不再追加数据权限条件） */
    public static void skip() {
        SKIP.set(true);
    }

    /** 清除跳过标记（必须 finally 调用） */
    public static void reset() {
        SKIP.remove();
    }

    public static boolean isSkipped() {
        return Boolean.TRUE.equals(SKIP.get());
    }

    private DataScopeContext() {}
}
