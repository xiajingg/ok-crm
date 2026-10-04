package com.okcrm.modules.pool.internal.dto;

import com.okcrm.modules.customer.api.RecycleBasis;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 公海回收规则配置请求。
 *
 * <p>规则做成租户可配而不是全局常量：不同企业「多久没跟进算超期」的标准差异极大，
 * 写死必然导致每个客户都要求改代码。</p>
 */
public record PoolSettingsRequest(

        @NotNull(message = "请指定是否启用自动回收")
        Boolean enabled,

        @Min(value = 1, message = "回收天数至少为 1 天")
        @Max(value = 3650, message = "回收天数过大")
        Integer days,

        /** LAST_FOLLOWUP（默认）或 CREATE_TIME */
        RecycleBasis basis,

        @Min(value = 0, message = "持有上限不能为负")
        Integer claimLimit
) {
}
