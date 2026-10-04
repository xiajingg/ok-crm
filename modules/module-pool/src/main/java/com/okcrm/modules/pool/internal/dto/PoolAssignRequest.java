package com.okcrm.modules.pool.internal.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 主管指派客户请求。
 */
public record PoolAssignRequest(

        @NotNull(message = "客户不能为空")
        Long customerId,

        @NotNull(message = "目标负责人不能为空")
        Long employeeId,

        String remark
) {
}
