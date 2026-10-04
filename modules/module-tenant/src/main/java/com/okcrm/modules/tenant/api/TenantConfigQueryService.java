package com.okcrm.modules.tenant.api;

import java.util.Map;

/**
 * 租户配置读写服务。
 *
 * <p>模块之间不直接查 {@code sys_tenant_config} 表，一律走本接口，
 * 这样配置键的语义和默认值只有一处定义。</p>
 */
public interface TenantConfigQueryService {

    String getString(Long tenantId, String key, String defaultValue);

    int getInt(Long tenantId, String key, int defaultValue);

    boolean getBoolean(Long tenantId, String key, boolean defaultValue);

    /**
     * 该租户的全部配置项。
     */
    Map<String, String> getAll(Long tenantId);

    /**
     * 写入（不存在则新增）。
     */
    void put(Long tenantId, String key, String value);
}
