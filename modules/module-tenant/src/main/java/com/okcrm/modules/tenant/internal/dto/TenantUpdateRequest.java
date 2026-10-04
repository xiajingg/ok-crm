package com.okcrm.modules.tenant.internal.dto;

import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * 更新租户请求。编码（code）不可修改：它是登录时的定位依据。
 */
public record TenantUpdateRequest(

        @Size(max = 128, message = "企业名称最长 128 位")
        String name,

        @Size(max = 32)
        String region,

        @Size(max = 64)
        String regionName,

        @Size(max = 64)
        String timezone,

        @Size(max = 16)
        String currency,

        @Size(max = 64)
        String contactName,

        @Size(max = 32)
        String contactPhone,

        LocalDate expireDate
) {
}
