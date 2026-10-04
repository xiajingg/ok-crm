package com.okcrm.modules.customer.internal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 新增客户请求。
 *
 * <p>{@code ownerId} 可空：留空表示直接进公海池，由员工后续领取。</p>
 */
public record CustomerCreateRequest(

        @NotBlank(message = "客户名称不能为空")
        @Size(max = 128, message = "客户名称最长 128 位")
        String name,

        @Size(max = 64)
        String industry,

        @Size(max = 8)
        String level,

        @Size(max = 64)
        String source,

        @Size(max = 32)
        String phone,

        @Size(max = 255)
        String address,

        /** 负责人 ID；为空表示进公海池 */
        Long ownerId,

        @Size(max = 255)
        String tags,

        @Size(max = 500)
        String remark
) {
}
