package com.okcrm.platform.security.jwt;

import com.okcrm.platform.common.constant.CommonConstants;
import com.okcrm.platform.common.exception.BizException;
import com.okcrm.platform.security.principal.LoginUser;
import com.okcrm.platform.tenant.TenantContext;
import com.okcrm.platform.tenant.TenantInfo;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

/**
 * JWT 认证过滤器。
 *
 * <p>一次性完成三件事：解析令牌 → 写入 SecurityContext（身份）→ 写入 TenantContext（租户）。
 * 因此后续所有业务代码都能直接拿到「当前是谁」和「当前是哪个租户」。</p>
 *
 * <p>令牌无效时不直接返回错误，而是把异常挂到 request attribute 上继续放行，
 * 由 {@link RestAuthenticationEntryPoint} 统一输出 —— 这样错误响应体格式与全局一致。</p>
 */
@Slf4j
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /** request attribute key：认证失败原因 */
    public static final String ATTR_AUTH_ERROR = "okcrm.auth.error";

    private final JwtTokenProvider tokenProvider;

    public JwtAuthenticationFilter(JwtTokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        try {
            String token = resolveToken(request);
            if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                try {
                    LoginUser loginUser = tokenProvider.parse(token);
                    bindContext(request, loginUser);
                } catch (BizException ex) {
                    // 令牌过期/无效：记录原因，交给 EntryPoint 输出，避免绕过统一响应格式
                    request.setAttribute(ATTR_AUTH_ERROR, ex);
                    log.debug("JWT 认证失败: {}", ex.getMessage());
                }
            }
            filterChain.doFilter(request, response);
        } finally {
            // 必须清理：线程池复用线程时残留上下文会导致跨租户串数据
            TenantContext.clear();
        }
    }

    private void bindContext(HttpServletRequest request, LoginUser loginUser) {
        if (loginUser.platformAdmin()) {
            TenantContext.set(TenantInfo.ofPlatformAdmin());
        } else {
            TenantContext.set(TenantInfo.of(loginUser.tenantId(), loginUser.tenantCode(), null));
        }

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(loginUser, null, Collections.emptyList());
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(CommonConstants.HEADER_AUTHORIZATION);
        if (header == null || !header.startsWith(CommonConstants.BEARER_PREFIX)) {
            return null;
        }
        String token = header.substring(CommonConstants.BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }
}
