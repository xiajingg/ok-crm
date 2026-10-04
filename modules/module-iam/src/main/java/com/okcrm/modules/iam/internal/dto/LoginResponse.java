package com.okcrm.modules.iam.internal.dto;

import java.util.Set;

/**
 * 登录响应。
 *
 * <p>一次性把前端启动所需的信息都给全：身份、租户、权限码、已购模块。
 * 前端据此渲染菜单、控制按钮显隐，不需要再多打几个接口。</p>
 */
public record LoginResponse(
        String token,
        String tokenType,
        long expireSeconds,
        UserInfo user,
        TenantSummary tenant,
        /** 权限码集合，供前端 v-permission 指令使用 */
        Set<String> permissions,
        /** 当前租户已购买模块 */
        Set<String> modules
) {

    public record UserInfo(
            Long id,
            String username,
            String realName,
            boolean platformAdmin
    ) {
    }

    public record TenantSummary(
            Long id,
            String code,
            String name,
            String region,
            String regionName,
            String timezone,
            String currency
    ) {
    }
}
