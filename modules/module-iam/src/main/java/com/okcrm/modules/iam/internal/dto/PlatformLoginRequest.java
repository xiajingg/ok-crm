package com.okcrm.modules.iam.internal.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 平台超管登录请求。
 *
 * <p>平台超管不是任何租户的员工，因此不走 {@code sys_employee}，
 * 凭据来自配置（{@code okcrm.platform-admin.*}），生产环境必须用环境变量覆盖。</p>
 */
public record PlatformLoginRequest(

        @NotBlank(message = "账号不能为空")
        String username,

        @NotBlank(message = "密码不能为空")
        String password
) {
}
