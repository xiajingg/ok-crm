package com.okcrm.modules.tenant.internal.dto;

import java.time.LocalDate;

/**
 * 模块信息响应：既用于「产品价目表」，也用于「某租户的授权情况」。
 */
public record ModuleResponse(
        String moduleKey,
        String name,
        String description,
        String version,
        Boolean core,
        Integer sortOrder,
        /** 目标租户是否已授权；查询价目表时为 false */
        boolean licensed,
        /** 授权到期日 */
        LocalDate expireDate
) {
}
