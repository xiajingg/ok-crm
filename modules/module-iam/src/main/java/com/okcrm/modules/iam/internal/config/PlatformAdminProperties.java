package com.okcrm.modules.iam.internal.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 平台超管凭据配置。
 *
 * <p><b>生产环境必须通过环境变量覆盖默认密码。</b>默认值只用于本地开发与首次体验。</p>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "okcrm.platform-admin")
public class PlatformAdminProperties {

    /** 是否启用平台超管登录入口 */
    private boolean enabled = true;

    private String username = "admin";

    private String password = "admin123456";

    private String realName = "平台管理员";
}
