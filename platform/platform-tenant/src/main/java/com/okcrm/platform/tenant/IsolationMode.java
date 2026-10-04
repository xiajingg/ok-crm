package com.okcrm.platform.tenant;

/**
 * 租户数据隔离模式。
 *
 * <p>本枚举是「按模块独立售卖 + 大客户私有化」两种交付形态的抽象出口：
 * 同一套业务代码，通过替换 {@link TenantIsolationStrategy} 实现即可切换隔离级别。</p>
 */
public enum IsolationMode {

    /** 共享库 + 共享表 + tenant_id 行级隔离（默认，SaaS 首选） */
    SHARED_TABLE,

    /** 共享库 + 每租户独立 Schema（预留） */
    SHARED_SCHEMA,

    /** 每租户独立数据库（预留，大客户私有化） */
    SEPARATE_DATABASE
}
