package com.okcrm.modules.tenant.internal.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.okcrm.modules.tenant.api.TenantConfigQueryService;
import com.okcrm.modules.tenant.domain.TenantConfig;
import com.okcrm.modules.tenant.infra.mapper.TenantConfigMapper;
import com.okcrm.platform.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 租户配置服务。
 *
 * <p>所有读写都用 {@code TenantContext.callAs} 包一层：调用方可能处于平台超管上下文
 * （没有租户 ID），也可能想读别的租户的配置，不能依赖调用方的上下文。</p>
 */
@Service
@RequiredArgsConstructor
public class TenantConfigService implements TenantConfigQueryService {

    private final TenantConfigMapper tenantConfigMapper;

    @Override
    public String getString(Long tenantId, String key, String defaultValue) {
        if (tenantId == null || key == null) {
            return defaultValue;
        }
        return TenantContext.callAs(tenantId, () -> {
            TenantConfig config = selectByKey(key);
            return config == null || config.getConfigValue() == null ? defaultValue : config.getConfigValue();
        });
    }

    @Override
    public int getInt(Long tenantId, String key, int defaultValue) {
        String raw = getString(tenantId, key, null);
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    @Override
    public boolean getBoolean(Long tenantId, String key, boolean defaultValue) {
        String raw = getString(tenantId, key, null);
        return raw == null || raw.isBlank() ? defaultValue : Boolean.parseBoolean(raw.trim());
    }

    @Override
    public Map<String, String> getAll(Long tenantId) {
        if (tenantId == null) {
            return Map.of();
        }
        return TenantContext.callAs(tenantId, () -> {
            List<TenantConfig> rows = tenantConfigMapper.selectList(Wrappers.<TenantConfig>lambdaQuery());
            Map<String, String> result = new LinkedHashMap<>();
            rows.forEach(row -> result.put(row.getConfigKey(), row.getConfigValue()));
            return result;
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void put(Long tenantId, String key, String value) {
        if (tenantId == null || key == null) {
            return;
        }
        TenantContext.runAs(tenantId, () -> {
            TenantConfig existing = selectByKey(key);
            if (existing == null) {
                TenantConfig entity = new TenantConfig();
                entity.setConfigKey(key);
                entity.setConfigValue(value);
                tenantConfigMapper.insert(entity);
            } else {
                TenantConfig update = new TenantConfig();
                update.setConfigValue(value);
                tenantConfigMapper.update(update, Wrappers.<TenantConfig>lambdaUpdate()
                        .eq(TenantConfig::getConfigKey, key));
            }
        });
    }

    /**
     * 批量写入。
     */
    @Transactional(rollbackFor = Exception.class)
    public void putAll(Long tenantId, Map<String, String> configs) {
        if (configs == null || configs.isEmpty()) {
            return;
        }
        configs.forEach((key, value) -> put(tenantId, key, value));
    }

    private TenantConfig selectByKey(String key) {
        return tenantConfigMapper.selectOne(
                Wrappers.<TenantConfig>lambdaQuery().eq(TenantConfig::getConfigKey, key));
    }
}
