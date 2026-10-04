package com.okcrm.platform.security.jwt;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT 配置。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "okcrm.jwt")
public class JwtProperties {

    /** 签名密钥，HS256 要求至少 32 字节 */
    private String secret;

    /** 令牌有效期（分钟） */
    private long expireMinutes = 720;

    /** 签发者 */
    private String issuer = "ok-crm";
}
