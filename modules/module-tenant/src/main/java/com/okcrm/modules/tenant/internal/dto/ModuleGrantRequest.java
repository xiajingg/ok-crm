package com.okcrm.modules.tenant.internal.dto;

import jakarta.validation.constraints.NotEmpty;

import java.time.LocalDate;
import java.util.List;

/**
 * 模块授权请求（平台超管）。
 */
public record ModuleGrantRequest(

        @NotEmpty(message = "至少选择一个模块")
        List<String> moduleKeys,

        /** 到期日；为空表示永久授权 */
        LocalDate expireDate,

        String remark
) {
}
