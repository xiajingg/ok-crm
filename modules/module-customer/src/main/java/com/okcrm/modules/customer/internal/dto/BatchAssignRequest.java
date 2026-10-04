package com.okcrm.modules.customer.internal.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 批量分配请求。
 */
public record BatchAssignRequest(

        @NotEmpty(message = "请选择客户")
        List<Long> customerIds,

        @NotNull(message = "目标负责人不能为空")
        Long ownerId
) {
}
