package com.okcrm.platform.security.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.okcrm.platform.common.api.ErrorCode;
import com.okcrm.platform.common.api.Result;
import com.okcrm.platform.common.exception.BizException;
import com.okcrm.platform.security.jwt.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 未认证入口：返回真正的 HTTP 401，前端据此跳登录页。
 *
 * <p>若过滤器已记录具体原因（令牌过期 / 令牌无效），优先返回该业务码，
 * 便于前端区分「需要重新登录」和「令牌被篡改」。</p>
 */
@Component
@RequiredArgsConstructor
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        Object attr = request.getAttribute(JwtAuthenticationFilter.ATTR_AUTH_ERROR);
        Result<Void> body = attr instanceof BizException bizException
                ? Result.fail(bizException.getCode(), bizException.getMessage())
                : Result.fail(ErrorCode.UNAUTHORIZED);

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
