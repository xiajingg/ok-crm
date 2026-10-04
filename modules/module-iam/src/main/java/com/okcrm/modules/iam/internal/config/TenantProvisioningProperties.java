package com.okcrm.modules.iam.internal.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 租户开通时初始化管理员账号的配置。
 *
 * <p>默认密码只用于本地体验，<b>生产环境必须通过环境变量覆盖</b>，
 * 并在首次登录后强制修改。</p>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "okcrm.tenant-provisioning")
public class TenantProvisioningProperties {

    /** 租户开通时创建的默认管理员账号 */
    private String defaultAdminUsername = "admin";

    /** 默认管理员初始密码 */
    private String defaultAdminPassword = "admin123456";

    private String defaultAdminRealName = "系统管理员";
}
