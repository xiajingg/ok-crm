package com.okcrm.modules.iam.internal.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 登录请求。
 *
 * <p>单企业私有化部署：一套系统只服务一个企业，企业信息由部署初始化流程写入
 * （见 {@code okcrm.setup} 配置），因此登录不再需要选择企业，只要账号密码。</p>
 */
public record LoginRequest(

        @NotBlank(message = "账号不能为空")
        String username,

        @NotBlank(message = "密码不能为空")
        String password
) {
}
