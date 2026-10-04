package com.okcrm.modules.tenant.internal.dto;

import jakarta.validation.constraints.NotNull;

import java.util.Map;

/**
 * 批量更新租户配置。key 见 {@code TenantConfigKeys}。
 */
public record TenantConfigUpdateRequest(

        @NotNull(message = "配置内容不能为空")
        Map<String, String> configs
) {
}
