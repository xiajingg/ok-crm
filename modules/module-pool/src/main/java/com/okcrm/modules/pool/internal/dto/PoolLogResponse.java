package com.okcrm.modules.pool.internal.dto;

import java.time.LocalDateTime;

/**
 * 公海流转日志响应。
 */
public record PoolLogResponse(
        Long id,
        Long customerId,
        String customerName,
        /** CLAIM / ASSIGN / TRANSFER / RELEASE / RECYCLE */
        String action,
        /** 中文动作名，便于前端直接展示 */
        String actionLabel,
        Long fromOwnerId,
        String fromOwnerName,
        Long toOwnerId,
        String toOwnerName,
        String reason,
        Long operatorId,
        String operatorName,
        LocalDateTime createdAt
) {
}
