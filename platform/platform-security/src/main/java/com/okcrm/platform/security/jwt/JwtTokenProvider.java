package com.okcrm.platform.security.jwt;

import com.okcrm.platform.common.api.ErrorCode;
import com.okcrm.platform.common.exception.BizException;
import com.okcrm.platform.security.principal.LoginUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/**
 * JWT 签发与解析。
 *
 * <p>令牌中只放「身份」不放「权限」：权限随岗位调整会变，
 * 放进令牌会导致「改了岗位必须重新登录」。</p>
 */
@Component
public class JwtTokenProvider {

    private static final String CLAIM_TENANT_ID = "tenantId";
    private static final String CLAIM_TENANT_CODE = "tenantCode";
    private static final String CLAIM_USERNAME = "username";
    private static final String CLAIM_REAL_NAME = "realName";
    private static final String CLAIM_PLATFORM_ADMIN = "platformAdmin";

    private static final int MIN_SECRET_BYTES = 32;

    private final JwtProperties properties;
    private final SecretKey signingKey;

    public JwtTokenProvider(JwtProperties properties) {
        this.properties = properties;
        String secret = properties.getSecret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "okcrm.jwt.secret 长度不足：HS256 要求至少 " + MIN_SECRET_BYTES + " 字节，请通过环境变量 JWT_SECRET 配置");
        }
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 签发令牌。
     */
    public String generate(LoginUser user) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(properties.getExpireMinutes() * 60);

        var builder = Jwts.builder()
                .issuer(properties.getIssuer())
                .subject(String.valueOf(user.employeeId()))
                .claim(CLAIM_USERNAME, user.username())
                .claim(CLAIM_REAL_NAME, user.realName())
                .claim(CLAIM_PLATFORM_ADMIN, user.platformAdmin())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry));

        if (user.tenantId() != null) {
            builder.claim(CLAIM_TENANT_ID, user.tenantId());
            builder.claim(CLAIM_TENANT_CODE, user.tenantCode());
        }
        return builder.signWith(signingKey).compact();
    }

    /**
     * 解析令牌。签名不合法或已过期都会抛出业务异常。
     */
    public LoginUser parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .requireIssuer(properties.getIssuer())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            Long employeeId = Long.valueOf(claims.getSubject());
            Long tenantId = asLong(claims.get(CLAIM_TENANT_ID));
            String tenantCode = claims.get(CLAIM_TENANT_CODE, String.class);
            String username = claims.get(CLAIM_USERNAME, String.class);
            String realName = claims.get(CLAIM_REAL_NAME, String.class);
            Boolean platformAdmin = claims.get(CLAIM_PLATFORM_ADMIN, Boolean.class);

            return new LoginUser(employeeId, username, realName, tenantId, tenantCode,
                    Boolean.TRUE.equals(platformAdmin));
        } catch (ExpiredJwtException ex) {
            throw BizException.of(ErrorCode.TOKEN_EXPIRED);
        } catch (JwtException | IllegalArgumentException ex) {
            throw BizException.of(ErrorCode.TOKEN_INVALID);
        }
    }

    public long getExpireSeconds() {
        return properties.getExpireMinutes() * 60;
    }

    private static Long asLong(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }
}
