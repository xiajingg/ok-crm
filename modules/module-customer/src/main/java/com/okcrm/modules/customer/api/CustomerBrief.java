package com.okcrm.modules.customer.api;

import com.okcrm.modules.customer.domain.Customer;

import java.time.LocalDateTime;

/**
 * 客户摘要 —— 跨模块传递的只读视图（公海池模块使用）。
 */
public record CustomerBrief(
        Long id,
        String name,
        String industry,
        String level,
        Long ownerId,
        LocalDateTime ownerAssignedAt,
        LocalDateTime lastFollowUpAt,
        LocalDateTime enterPoolAt
) {

    public static CustomerBrief from(Customer customer) {
        return new CustomerBrief(
                customer.getId(),
                customer.getName(),
                customer.getIndustry(),
                customer.getLevel(),
                customer.getOwnerId(),
                customer.getOwnerAssignedAt(),
                customer.getLastFollowUpAt(),
                customer.getEnterPoolAt()
        );
    }

    /** 是否在公海池中（无归属） */
    public boolean inPool() {
        return ownerId == null;
    }
}
