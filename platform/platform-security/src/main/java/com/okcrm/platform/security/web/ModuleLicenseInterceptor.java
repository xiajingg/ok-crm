package com.okcrm.platform.security.web;

import com.okcrm.platform.common.api.ErrorCode;
import com.okcrm.platform.common.exception.BizException;
import com.okcrm.platform.security.annotation.RequiresModule;
import com.okcrm.platform.security.checker.ModuleChecker;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.lang.NonNull;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 模块授权拦截器：检查 {@link RequiresModule} 声明的模块当前租户是否已购买。
 *
 * <p>刻意做成 MVC 拦截器而不是 {@code @PreAuthorize} 表达式：</p>
 * <ol>
 *   <li>可以和 {@code @PreAuthorize} 并存而不互相覆盖（见 {@link RequiresModule} 的说明）</li>
 *   <li>未授权时抛业务异常，能被全局异常处理器翻译成统一的 1401 响应体，
 *       前端可以直接提示「该功能需要购买 XX 模块」，而不是一个没有上下文的 403</li>
 * </ol>
 */
@RequiredArgsConstructor
public class ModuleLicenseInterceptor implements HandlerInterceptor {

    private final ModuleChecker moduleChecker;

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request,
                             @NonNull HttpServletResponse response,
                             @NonNull Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        // 方法级优先，其次类级
        RequiresModule annotation = AnnotatedElementUtils.findMergedAnnotation(
                handlerMethod.getMethod(), RequiresModule.class);
        if (annotation == null) {
            annotation = AnnotatedElementUtils.findMergedAnnotation(
                    handlerMethod.getBeanType(), RequiresModule.class);
        }
        if (annotation == null) {
            return true;
        }

        if (!moduleChecker.licensed(annotation.value())) {
            throw BizException.of(ErrorCode.MODULE_NOT_LICENSED);
        }
        return true;
    }
}
