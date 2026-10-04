package com.okcrm.platform.security.checker;

import com.okcrm.platform.security.principal.LoginUser;
import com.okcrm.platform.security.principal.LoginUserContext;
import com.okcrm.platform.security.spi.PermissionProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;

/**
 * 权限校验器，注册为 bean 名 {@code perm}，供 SpEL 调用：
 *
 * <pre>{@code
 * @PreAuthorize("@perm.has('customer:create')")
 * @PreAuthorize("@perm.hasAny('customer:update', 'customer:delete')")
 * }</pre>
 *
 * <p>平台超管直接放行。</p>
 */
@Slf4j
@Component("perm")
public class PermissionChecker {

    private final ObjectProvider<PermissionProvider> permissionProvider;

    public PermissionChecker(ObjectProvider<PermissionProvider> permissionProvider) {
        this.permissionProvider = permissionProvider;
    }

    public boolean has(String permissionCode) {
        LoginUser user = LoginUserContext.get();
        if (user == null) {
            return false;
        }
        if (user.platformAdmin()) {
            return true;
        }
        boolean granted = permissions().contains(permissionCode);
        log.debug("权限校验: employeeId={}, code={}, granted={}", user.employeeId(), permissionCode, granted);
        return granted;
    }

    public boolean hasAny(String... permissionCodes) {
        LoginUser user = LoginUserContext.get();
        if (user == null) {
            return false;
        }
        if (user.platformAdmin()) {
            return true;
        }
        Set<String> owned = permissions();
        return Arrays.stream(permissionCodes).anyMatch(owned::contains);
    }

    public boolean hasAll(String... permissionCodes) {
        LoginUser user = LoginUserContext.get();
        if (user == null) {
            return false;
        }
        if (user.platformAdmin()) {
            return true;
        }
        Set<String> owned = permissions();
        return Arrays.stream(permissionCodes).allMatch(owned::contains);
    }

    private Set<String> permissions() {
        LoginUser user = LoginUserContext.require();
        PermissionProvider provider = permissionProvider.getIfAvailable();
        return provider == null ? Set.of() : provider.permissionsOf(user.employeeId());
    }
}
