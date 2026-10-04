package com.okcrm.platform.security.principal;

import java.io.Serializable;

/**
 * 当前登录主体。
 *
 * <p>刻意不携带权限集合：权限会随岗位调整变化，塞进 token 会导致「改了权限必须重新登录」。
 * 权限由 {@code PermissionProvider} 按需查询（带缓存）。</p>
 *
 * @param employeeId    员工 ID
 * @param username      登录账号
 * @param realName      姓名
 * @param tenantId      租户 ID；平台超管为 null
 * @param tenantCode    租户编码
 * @param platformAdmin 是否平台超管（跨租户）
 */
public record LoginUser(
        Long employeeId,
        String username,
        String realName,
        Long tenantId,
        String tenantCode,
        boolean platformAdmin
) implements Serializable {

    public static LoginUser platformAdmin(Long employeeId, String username, String realName) {
        return new LoginUser(employeeId, username, realName, null, "PLATFORM", true);
    }
}
