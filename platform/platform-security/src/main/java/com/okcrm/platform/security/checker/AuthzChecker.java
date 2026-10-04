package com.okcrm.platform.security.checker;

import com.okcrm.platform.security.principal.LoginUserContext;
import org.springframework.stereotype.Component;

/**
 * 身份类型校验器，注册为 bean 名 {@code authz}。
 *
 * <pre>{@code
 * @PreAuthorize("@authz.platformAdmin()")   // 仅平台超管
 * }</pre>
 *
 * <p>「平台超管」与「租户管理员」必须是两套身份、两套接口。混用会导致越权：
 * 租户管理员只要拿到某个权限码，就能调用跨租户的管理接口。</p>
 */
@Component("authz")
public class AuthzChecker {

    public boolean platformAdmin() {
        return LoginUserContext.isPlatformAdmin();
    }

    public boolean authenticated() {
        return LoginUserContext.get() != null;
    }

    /**
     * 租户内用户（非平台超管）。
     */
    public boolean tenantUser() {
        var user = LoginUserContext.get();
        return user != null && !user.platformAdmin();
    }
}
