package com.okcrm.platform.security.spi;

import java.util.Set;

/**
 * 模块授权提供者 SPI —— 「按模块独立售卖」在运行期的落地接口。
 *
 * <p>由租户模块实现：读取 {@code sys_tenant_module} 得到该租户已购买的模块清单。
 * 接口层用 {@code @PreAuthorize("@module.licensed('pool')")} 即可拦住未购买模块的调用，
 * 未授权时返回明确的「未购买该模块」提示，而不是 500。</p>
 */
public interface ModuleLicenseProvider {

    /**
     * 判断租户是否已授权某模块。
     *
     * @param tenantId  租户 ID
     * @param moduleKey 模块标识，如 customer / pool / report
     */
    boolean isLicensed(Long tenantId, String moduleKey);

    /**
     * 租户已授权的模块集合。用于前端动态菜单下发。
     */
    Set<String> licensedModules(Long tenantId);
}
