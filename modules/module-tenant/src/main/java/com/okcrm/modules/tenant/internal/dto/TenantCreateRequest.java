package com.okcrm.modules.tenant.internal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/**
 * 开通租户请求（平台超管）。
 */
public record TenantCreateRequest(

        @NotBlank(message = "租户编码不能为空")
        @Size(max = 64, message = "租户编码最长 64 位")
        @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "租户编码只能包含字母、数字、下划线和短横线")
        String code,

        @NotBlank(message = "企业名称不能为空")
        @Size(max = 128, message = "企业名称最长 128 位")
        String name,

        @NotBlank(message = "地区编码不能为空")
        @Size(max = 32, message = "地区编码最长 32 位")
        String region,

        @Size(max = 64, message = "地区名称最长 64 位")
        String regionName,

        @Size(max = 64)
        String timezone,

        @Size(max = 16)
        String currency,

        @Size(max = 64)
        String contactName,

        @Size(max = 32)
        String contactPhone,

        LocalDate expireDate,

        /** 开通时一并授权的模块；为空表示授权全部已注册模块 */
        List<String> modules
) {
}
