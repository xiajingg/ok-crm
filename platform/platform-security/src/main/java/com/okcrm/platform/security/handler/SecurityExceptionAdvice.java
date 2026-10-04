package com.okcrm.platform.security.handler;

import com.okcrm.platform.common.api.ErrorCode;
import com.okcrm.platform.common.api.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 安全异常处理器。
 *
 * <p><b>这个类必须存在，而且必须优先级最高。</b>
 * 原因是踩过的一个坑：业务侧通常会写一个 {@code @ExceptionHandler(Exception.class)}
 * 兜底处理器（本项目在 {@code platform-web} 里）。而 Spring Security 的
 * {@code @PreAuthorize} 校验失败时抛的是 {@code AuthorizationDeniedException}
 * （继承自 {@code AccessDeniedException}），它会被那个「兜底处理器」当成未知异常接住，
 * 最终返回 <b>HTTP 200 + 业务码 500</b>。</p>
 *
 * <p>后果非常隐蔽：权限校验其实是生效的，日志里能看到 {@code granted=false}，
 * 但前端拿到的是 200，既不会跳登录也不会提示无权限，看起来就像「权限没生效」。</p>
 *
 * <p>这里的做法是把 {@code AccessDeniedException} 显式翻译成真正的 HTTP 403，
 * 响应体与 {@link RestAccessDeniedHandler} 保持一致，前端只需要看 HTTP 状态码。</p>
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class SecurityExceptionAdvice {

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Result<Void>> handleAccessDenied(AccessDeniedException ex) {
        log.debug("权限校验未通过: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Result.fail(ErrorCode.PERMISSION_DENIED));
    }
}
