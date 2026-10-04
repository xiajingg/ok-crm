package com.okcrm.platform.common.constant;

/**
 * 全局常量。
 */
public final class CommonConstants {

    private CommonConstants() {
    }

    /** 请求头：携带租户 ID */
    public static final String HEADER_TENANT_ID = "X-Tenant-Id";

    /** 请求头：Bearer 令牌 */
    public static final String HEADER_AUTHORIZATION = "Authorization";

    /** Bearer 前缀 */
    public static final String BEARER_PREFIX = "Bearer ";

    /** 超级管理员角色标识（平台级，跨租户） */
    public static final String PLATFORM_SUPER_ADMIN = "PLATFORM_SUPER_ADMIN";

    /** 平台超管内置账号 ID */
    public static final Long PLATFORM_ADMIN_ID = 1L;

    /** 逻辑删除：未删除 */
    public static final int NOT_DELETED = 0;

    /** 逻辑删除：已删除 */
    public static final int DELETED = 1;

    /** 权限点根节点 */
    public static final String PERMISSION_ROOT = "0";
}
