package com.okcrm.modules.tenant.api;

import com.okcrm.platform.common.enums.EnableStatus;

/**
 * 租户摘要 —— 跨模块传递的只读视图。
 *
 * <p>刻意不直接暴露 {@code Tenant} 实体：实体带持久层注解且字段会演进，
 * 用它做跨模块契约会让「改个字段就牵动全系统」。</p>
 */
public record TenantBrief(
        Long id,
        String code,
        String name,
        String region,
        String regionName,
        String timezone,
        String currency,
        EnableStatus status
) {

    public boolean enabled() {
        return status == EnableStatus.ENABLED;
    }
}
