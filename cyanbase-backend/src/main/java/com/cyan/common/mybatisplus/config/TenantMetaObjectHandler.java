package com.cyan.common.mybatisplus.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.cyan.common.security.util.SecurityUtils;
import com.cyan.config.CustomTenantHandler;
import com.cyan.security.domain.LoginUser;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * 租户字段（tenant_id）自动填充处理器
 * <p>
 * MyBatis-Plus 全局只允许注册一个 MetaObjectHandler 实例，
 * 因此本类通过 @Primary 被 MP 选中，同时内部委托原有
 * {@link MyMetaObjectHandler} 完成 createBy/createTime/updateBy/updateTime 填充，
 * 在此基础上追加 tenantId 自动填充。原有 MyMetaObjectHandler 零修改。
 * </p>
 *
 * <h3>填充链路</h3>
 * <pre>
 * INSERT 一条数据
 *   → TenantMetaObjectHandler.insertFill() 被 MP 调用
 *     → 1. delegate.insertFill()   原有逻辑：填 createBy / createTime / updateBy / updateTime
 *     → 2. 填 tenantId               新增逻辑：strictInsertFill("tenantId", Long, 当前租户ID)
 *
 * UPDATE 一条数据
 *   → TenantMetaObjectHandler.updateFill() 被 MP 调用
 *     → 1. delegate.updateFill()    原有逻辑：填 updateBy / updateTime
 *     → （tenantId 不在 UPDATE 时填充，避免误覆盖）
 * </pre>
 */
@Slf4j
@Component
@Primary
public class TenantMetaObjectHandler implements MetaObjectHandler {

    /**
     * ThreadLocal 跳过标记：创建平台主体等场景需要 tenantId 保持 NULL，
     * 调用 skip() 后本线程内下一次 insertFill 将跳过 tenantId 自动填充。
     * 风格与 CustomTenantHandler.skip() 一致。
     */
    private static final ThreadLocal<Boolean> SKIP_TENANT_FILL = new ThreadLocal<>();

    /** 外部调用：临时跳过本线程的 tenantId 自动填充 */
    public static void skip() {
        SKIP_TENANT_FILL.set(Boolean.TRUE);
    }

    /** 外部调用：恢复本线程的 tenantId 自动填充（务必在 finally 中调用） */
    public static void reset() {
        SKIP_TENANT_FILL.remove();
    }

    /**
     * 委托原有处理器，保持 createBy/createTime/updateBy/updateTime 填充能力不变。
     */
    @Autowired
    @Qualifier("myMetaObjectHandler")
    private MetaObjectHandler delegate;

    /**
     * 注入 CustomTenantHandler，直接复用其三级降级 tenantId 解析策略，
     * 保证实体填充与 SQL 拦截同源，消除 tenantId=0 / tenantId=6 不一致。
     */
    @Autowired
    private CustomTenantHandler customTenantHandler;

    @PostConstruct
    public void init() {
        log.info("[TenantMeta] ✅ TenantMetaObjectHandler 已加载，delegate={}",
                delegate != null ? delegate.getClass().getSimpleName() : "NULL! Bean 注入失败!");
        if (delegate == null) {
            throw new IllegalStateException(
                    "TenantMetaObjectHandler delegate 注入失败，请检查 @Qualifier(\"myMetaObjectHandler\") 是否匹配到了 MyMetaObjectHandler Bean");
        }
    }

    /**
     * 插入时填充：先委托原有处理器，再追加 tenantId。
     * <p>
     * tenantId 永远给一个确定的值，绝不留 null —— 数据库 tenant_id 列是 NOT NULL，
     * Java 实体字段默认值 null 会被 MyBatis 显式传到 SQL，覆盖数据库的 DEFAULT 0，
     * 导致 "Column 'tenant_id' cannot be null" 约束失败。
     * <p>
     * 取值优先级：
     * <ol>
     *   <li>有登录上下文 → 用当前用户的 tenantId</li>
     *   <li>无登录上下文（登录接口写日志、定时任务、启动自检等）→ 默认 0（系统级数据）</li>
     * </ol>
     * <p>
     * strictInsertFill 会跳过实体不存在的属性（静默），也会跳过业务层已手动设置非 null 的属性。
     */
    @Override
    public void insertFill(MetaObject metaObject) {
        // 1. 先让原有处理器填充 createBy / createTime / updateBy / updateTime
        delegate.insertFill(metaObject);

        // 2. 检查跳过标记：业务层明确要 tenantId=NULL 时（如创建平台主体），跳过自动填充
        if (Boolean.TRUE.equals(SKIP_TENANT_FILL.get())) {
            // ⭐ 关键：用 setFieldValByName（不走 strict 检查）显式把 tenantId 设为 null，
            // 确保 MP 生成 INSERT SQL 时显式写 tenant_id = NULL，
            // 而不是因实体 null + NOT_NULL 策略跳过该字段 → 触发数据库 DEFAULT 0 兜底
            this.setFieldValByName("tenantId", null, metaObject);
            log.debug("[TenantMeta] SKIP tenantId 自动填充，显式设置 tenantId=NULL");
            return;
        }

        // 3. 追加填充 tenantId —— 复用 CustomTenantHandler 同源解析（已含平台主体 activeTenantId 最高优先级）
        Long tenantId = customTenantHandler.resolveTenantId();
        if (tenantId == null) {
            // 无任何有效租户上下文 → 默认 0（系统级数据，如登录日志、定时任务）
            tenantId = 0L;
        }

        this.strictInsertFill(metaObject, "tenantId", Long.class, tenantId);
        log.debug("[TenantMeta] 填充 tenantId={} 到实体 {} (同源 CustomTenantHandler)",
                tenantId, metaObject.getOriginalObject().getClass().getSimpleName());
    }

    /**
     * 更新时：只委托原有处理器填 updateBy / updateTime，tenantId 不动。
     */
    @Override
    public void updateFill(MetaObject metaObject) {
        delegate.updateFill(metaObject);
    }
}