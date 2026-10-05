package com.okcrm.modules.iam.internal.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 初始管理员账号配置。
 *
 * <p>单企业私有化部署下，管理员账号由首次启动的初始化流程自动创建
 * （见 {@code DeploymentSetupRunner}），不再有「平台超管开通租户」这一步。</p>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "okcrm.setup.admin")
public class AdminSetupProperties {

    /** 初始管理员账号 */
    private String username = "admin";

    /**
     * 初始管理员密码。
     *
     * <p><b>留空则自动生成一个随机强密码并打印到启动日志</b> —— 这样每个客户拿到的
     * 初始密码都不同，避免「所有客户共用 admin123456」这种一撞一个准的情况。</p>
     */
    private String password = "";

    private String realName = "系统管理员";
}
