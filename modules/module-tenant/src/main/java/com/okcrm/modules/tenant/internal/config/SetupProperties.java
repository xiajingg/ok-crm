package com.okcrm.modules.tenant.internal.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 部署初始化配置。
 *
 * <p>单企业私有化部署下，一套系统只服务一个企业，企业信息不再由「平台管理端」录入，
 * 而是由这里的配置在启动时自动写入数据库（幂等：已存在则跳过）。</p>
 *
 * <p>这样客户拿到包之后只要改这几行配置就能部署，不需要任何额外操作步骤；
 * 部署完成后也可以在「企业设置」页里改（改的是数据库，不需要重启）。</p>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "okcrm.setup")
public class SetupProperties {

    /** 是否在启动时自动初始化企业信息。幂等，重复启动不会重复建 */
    private boolean enabled = true;

    /**
     * 本部署对应的企业 ID。
     *
     * <p>单企业部署下固定一个值即可。初始化时用它建记录，
     * 之后所有请求的租户上下文都用它 —— 隔离机制照常工作，只是永远只有一个值。</p>
     */
    private Long tenantId = 1L;

    /** 企业编码，仅用于日志与展示 */
    private String tenantCode = "default";

    /** 企业名称，会显示在管理后台左上角 */
    private String tenantName = "我的企业";

    /** 地区编码，如 CN-HUBEI */
    private String region = "CN";

    /** 地区名称，如 湖北 */
    private String regionName = "";

    /** 时区，如 Asia/Shanghai */
    private String timezone = "Asia/Shanghai";

    /** 币种，如 CNY */
    private String currency = "CNY";

    private String contactName = "";

    private String contactPhone = "";
}
