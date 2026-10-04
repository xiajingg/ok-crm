package com.okcrm.platform.security.checker;

import com.okcrm.platform.common.enums.DataScopeType;
import com.okcrm.platform.security.principal.LoginUser;
import com.okcrm.platform.security.principal.LoginUserContext;
import com.okcrm.platform.security.spi.PermissionProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * 数据权限判定，注册为 bean 名 {@code dataScope}。
 *
 * <p>业务模块（客户、公海池）用它决定查询要不要加 {@code owner_id = 当前用户} 条件。</p>
 *
 * <p><b>注意一个必须处理的边界</b>：公海客户（{@code owner_id IS NULL}）不属于任何人，
 * 如果数据权限条件写成 {@code owner_id = :me}，公海池就会「谁都看不到」。
 * 因此业务侧的公海查询必须显式绕过数据权限过滤 —— 见 {@code DataScopeType} 的注释。</p>
 */
@Component("dataScope")
public class DataScopeChecker {

    private final ObjectProvider<PermissionProvider> permissionProvider;

    public DataScopeChecker(ObjectProvider<PermissionProvider> permissionProvider) {
        this.permissionProvider = permissionProvider;
    }

    /**
     * 当前用户是否拥有「全部数据」范围。
     */
    public boolean all() {
        if (LoginUserContext.isPlatformAdmin()) {
            return true;
        }
        Long employeeId = LoginUserContext.employeeId();
        if (employeeId == null) {
            return false;
        }
        PermissionProvider provider = permissionProvider.getIfAvailable();
        if (provider == null) {
            // 没有权限提供者时按最保守处理
            return false;
        }
        return DataScopeType.ALL.getValue().equals(provider.dataScopeOf(employeeId));
    }

    /**
     * 当前用户是否只能看自己的数据。
     */
    public boolean selfOnly() {
        return !all();
    }

    /**
     * 当前用户 ID；未登录返回 null。
     */
    public Long currentEmployeeId() {
        LoginUser user = LoginUserContext.get();
        return user == null ? null : user.employeeId();
    }
}
