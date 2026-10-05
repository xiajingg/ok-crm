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

    /**
     * 密码重置：非空时，启动后把 {@link #username} 的密码重置为该值。
     *
     * <p>用途有两个：</p>
     * <ul>
     *   <li><b>客户忘了管理员密码</b> —— 这是必然会发生的支持场景，
     *       没有这个入口就只能让客户重装数据库</li>
     *   <li>本地开发图方便 —— 在 <code>.env.local</code> 里写一行
     *       <code>ADMIN_RESET_PASSWORD=admin123456</code>，每次启动密码都是它，
     *       不用去日志里翻随机密码</li>
     * </ul>
     *
     * <p>⚠️ 每次启动都会重置，用完请从配置里删掉。设了它会打 WARN 提醒。</p>
     */
    private String resetPassword = "";

    private String realName = "系统管理员";
}
