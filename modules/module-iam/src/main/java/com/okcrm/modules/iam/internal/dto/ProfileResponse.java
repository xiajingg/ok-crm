package com.okcrm.modules.iam.internal.dto;

import java.util.Set;

/**
 * 当前登录用户资料（不含令牌）。
 */
public record ProfileResponse(
        LoginResponse.UserInfo user,
        LoginResponse.TenantSummary tenant,
        Set<String> permissions,
        Set<String> modules
) {
}
