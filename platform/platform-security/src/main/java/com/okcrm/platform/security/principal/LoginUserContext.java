package com.okcrm.platform.security.principal;

import com.okcrm.platform.common.api.ErrorCode;
import com.okcrm.platform.common.exception.BizException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 登录主体上下文。业务代码取「当前是谁」统一走这里。
 */
public final class LoginUserContext {

    private LoginUserContext() {
    }

    /**
     * 当前登录主体；未登录返回 null。
     */
    public static LoginUser get() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        return principal instanceof LoginUser loginUser ? loginUser : null;
    }

    /**
     * 当前登录主体，未登录直接抛 401。
     */
    public static LoginUser require() {
        LoginUser loginUser = get();
        if (loginUser == null) {
            throw BizException.of(ErrorCode.UNAUTHORIZED);
        }
        return loginUser;
    }

    public static Long employeeId() {
        LoginUser loginUser = get();
        return loginUser == null ? null : loginUser.employeeId();
    }

    public static boolean isPlatformAdmin() {
        LoginUser loginUser = get();
        return loginUser != null && loginUser.platformAdmin();
    }
}
