package com.okcrm.modules.iam.internal.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 租户内用户登录请求。
 *
 * <p>必须提供 {@code tenantCode}：同一套 SaaS 里不同企业的账号可以重名，
 * 先用租户编码定位租户，再在租户内匹配账号。</p>
 */
public record LoginRequest(

        @NotBlank(message = "租户编码不能为空")
        String tenantCode,

        @NotBlank(message = "账号不能为空")
        String username,

        @NotBlank(message = "密码不能为空")
        String password
) {
}
