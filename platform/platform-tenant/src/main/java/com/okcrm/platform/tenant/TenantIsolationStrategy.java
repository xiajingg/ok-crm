package com.okcrm.platform.tenant;

import java.util.Set;

/**
 * 租户隔离策略 SPI —— 多租户架构的扩展点。
 *
 * <p>业务代码永远只依赖 {@link TenantContext}，不感知底层是行级隔离、Schema 隔离还是独立库。
 * 需要升级隔离级别时，新增一个实现类并让 Spring 注入它即可，业务代码零改动。</p>
 */
public interface TenantIsolationStrategy {

    /**
     * 隔离模式。
     */
    IsolationMode mode();

    /**
     * 该表是否需要参与租户隔离。
     *
     * <p>返回 false 的表在 SQL 中不会追加 {@code tenant_id} 条件，
     * 因此只允许放「全局表」：租户主表、平台级字典、权限点目录、框架自建表等。</p>
     *
     * @param tableName 表名（不含 Schema 前缀）
     * @return true 表示需要隔离
     */
    boolean supportsTable(String tableName);

    /**
     * 全局表清单：这些表不属于任何租户，必须显式排除。
     */
    Set<String> globalTables();
}
