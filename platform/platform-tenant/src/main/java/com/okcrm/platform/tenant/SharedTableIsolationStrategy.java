package com.okcrm.platform.tenant;

import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * 共享库 + 共享表 + tenant_id 行级隔离（默认策略）。
 *
 * <p>真正的 SQL 改写由 MyBatis-Plus 的 {@code TenantLineInnerInterceptor} 完成，
 * 本类只负责回答「哪些表该隔离」。</p>
 */
public class SharedTableIsolationStrategy implements TenantIsolationStrategy {

    /** 全局表：不属于任何租户，SQL 中不追加 tenant_id 条件 */
    private static final Set<String> DEFAULT_GLOBAL_TABLES = Set.of(
            // 租户主表本身
            "sys_tenant",
            // 平台级模块目录（可售卖模块的注册表，全局唯一）
            "sys_module",
            // 权限点目录：由平台在代码/Flyway 中定义，全租户共享
            "sys_permission",
            // 框架自建表
            "flyway_schema_history",
            "event_publication"
    );

    private final Set<String> globalTables;

    public SharedTableIsolationStrategy() {
        this(Collections.emptySet());
    }

    public SharedTableIsolationStrategy(Set<String> extraGlobalTables) {
        Set<String> merged = new HashSet<>(DEFAULT_GLOBAL_TABLES);
        extraGlobalTables.forEach(t -> merged.add(t.toLowerCase(Locale.ROOT)));
        this.globalTables = Collections.unmodifiableSet(merged);
    }

    @Override
    public IsolationMode mode() {
        return IsolationMode.SHARED_TABLE;
    }

    @Override
    public boolean supportsTable(String tableName) {
        if (tableName == null || tableName.isBlank()) {
            return true;
        }
        String normalized = tableName.toLowerCase(Locale.ROOT);
        // 去掉可能的 schema 前缀，例如 `ok_crm.sys_tenant`
        int dot = normalized.lastIndexOf('.');
        if (dot >= 0) {
            normalized = normalized.substring(dot + 1);
        }
        // 去掉反引号
        normalized = normalized.replace("`", "");
        return !globalTables.contains(normalized);
    }

    @Override
    public Set<String> globalTables() {
        return globalTables;
    }
}
