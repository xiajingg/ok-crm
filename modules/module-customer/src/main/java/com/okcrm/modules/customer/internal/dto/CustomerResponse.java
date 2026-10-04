package com.okcrm.modules.customer.internal.dto;

import java.time.LocalDateTime;

/**
 * 客户详情响应。
 */
public record CustomerResponse(
        Long id,
        String name,
        String industry,
        String level,
        String source,
        String phone,
        String address,
        Long ownerId,
        /** 负责人姓名；在公海池时为 null */
        String ownerName,
        /** 是否在公海池中 */
        boolean inPool,
        LocalDateTime ownerAssignedAt,
        LocalDateTime lastFollowUpAt,
        LocalDateTime enterPoolAt,
        String poolReason,
        String tags,
        String remark,
        /** 联系人数量 */
        Long contactCount,
        LocalDateTime createdAt
) {
}
