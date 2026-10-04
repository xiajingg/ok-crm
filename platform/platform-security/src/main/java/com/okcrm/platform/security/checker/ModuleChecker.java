package com.okcrm.platform.security.checker;

import com.okcrm.platform.security.principal.LoginUserContext;
import com.okcrm.platform.security.spi.ModuleLicenseProvider;
import com.okcrm.platform.tenant.TenantContext;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 模块授权校验器，注册为 bean 名 {@code module}，供 SpEL 调用：
 *
 * <pre>{@code
 * @PreAuthorize("@module.licensed('pool')")
 * }</pre>
 *
 * <p>这是「按模块独立售卖」在接口层的闸门：客户只买了基础版，
 * 调用公海池接口会得到明确的「未购买该模块」，而不是 404 或 500。</p>
 */
@Component("module")
public class ModuleChecker {

    private final ObjectProvider<ModuleLicenseProvider> licenseProvider;

    public ModuleChecker(ObjectProvider<ModuleLicenseProvider> licenseProvider) {
        this.licenseProvider = licenseProvider;
    }

    public boolean licensed(String moduleKey) {
        // 平台超管需要跨租户查看任意模块，直接放行
        if (LoginUserContext.isPlatformAdmin()) {
            return true;
        }
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            return false;
        }
        ModuleLicenseProvider provider = licenseProvider.getIfAvailable();
        return provider != null && provider.isLicensed(tenantId, moduleKey);
    }

    /**
     * 当前租户已授权模块集合；未接入授权数据时返回空集合。
     */
    public Set<String> licensedModules() {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            return Set.of();
        }
        ModuleLicenseProvider provider = licenseProvider.getIfAvailable();
        return provider == null ? Set.of() : provider.licensedModules(tenantId);
    }
}
