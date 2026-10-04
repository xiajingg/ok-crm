package com.okcrm.modules.customer.internal.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 客户转移/分配请求。
 */
public record TransferRequest(

        @NotNull(message = "目标负责人不能为空")
        Long ownerId,

        String remark
) {
}
