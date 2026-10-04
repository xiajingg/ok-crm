package com.okcrm.platform.mybatis.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 多租户相关配置。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "okcrm.tenant")
public class TenantProperties {

    /**
     * 额外需要排除租户隔离的全局表。
     *
     * <p>默认清单见 {@code SharedTableIsolationStrategy}；这里用于业务方临时追加，
     * 例如接入第三方 SDK 自带的表。</p>
     */
    private List<String> extraGlobalTables = new ArrayList<>();
}
