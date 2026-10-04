package com.okcrm.platform.tenant;

/**
 * 当前请求的租户信息快照。
 *
 * @param tenantId      租户 ID；平台超管跨租户操作时为 null
 * @param tenantCode    租户编码，便于日志排查
 * @param region        地区编码（如 CN-HUBEI / CN-GUANGDONG），仅作为租户属性
 * @param platformAdmin 是否平台超管（跨租户）
 */
public record TenantInfo(Long tenantId, String tenantCode, String region, boolean platformAdmin) {

    public static TenantInfo of(Long tenantId, String tenantCode, String region) {
        return new TenantInfo(tenantId, tenantCode, region, false);
    }

    /** 平台超管身份：无租户归属，可跨租户操作 */
    public static TenantInfo ofPlatformAdmin() {
        return new TenantInfo(null, "PLATFORM", null, true);
    }
}
