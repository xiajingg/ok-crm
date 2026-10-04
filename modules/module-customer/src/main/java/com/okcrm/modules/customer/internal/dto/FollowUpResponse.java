package com.okcrm.modules.customer.internal.dto;

import java.time.LocalDateTime;

/**
 * 跟进记录响应。
 */
public record FollowUpResponse(
        Long id,
        Long customerId,
        Long employeeId,
        /** 跟进人姓名，避免前端再查一次 */
        String employeeName,
        String type,
        String content,
        LocalDateTime followedAt,
        LocalDateTime nextFollowUpAt,
        LocalDateTime createdAt
) {
}
