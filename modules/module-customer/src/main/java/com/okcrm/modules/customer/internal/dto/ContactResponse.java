package com.okcrm.modules.customer.internal.dto;

import com.okcrm.modules.customer.domain.Contact;

import java.time.LocalDateTime;

/**
 * 联系人响应。
 */
public record ContactResponse(
        Long id,
        Long customerId,
        String name,
        String position,
        String phone,
        String email,
        Boolean primaryContact,
        String remark,
        LocalDateTime createdAt
) {

    public static ContactResponse from(Contact contact) {
        return new ContactResponse(
                contact.getId(),
                contact.getCustomerId(),
                contact.getName(),
                contact.getPosition(),
                contact.getPhone(),
                contact.getEmail(),
                contact.getPrimaryContact(),
                contact.getRemark(),
                contact.getCreatedAt()
        );
    }
}
