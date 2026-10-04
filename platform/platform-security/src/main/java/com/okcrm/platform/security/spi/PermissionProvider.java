package com.okcrm.platform.security.spi;

import java.util.Set;

/**
 * 权限提供者 SPI。
 *
 * <p>权限点由 IAM 模块定义与计算，但鉴权发生在平台层。用 SPI 反转依赖方向，
 * 避免 platform-security 反向依赖业务模块。</p>
 */
public interface PermissionProvider {

    /**
     * 员工拥有的权限点编码集合（多个岗位权限的并集）。
     */
    Set<String> permissionsOf(Long employeeId);

    /**
     * 员工的数据权限范围（多个岗位取最宽）。
     */
    String dataScopeOf(Long employeeId);
}
