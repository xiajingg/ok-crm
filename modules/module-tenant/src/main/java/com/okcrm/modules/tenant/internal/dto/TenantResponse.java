package com.okcrm.modules.tenant.internal.dto;

import com.okcrm.modules.tenant.domain.Tenant;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 租户详情响应。
 */
public record TenantResponse(
        Long id,
        String code,
        String name,
        String region,
        String regionName,
        String timezone,
        String currency,
        String contactName,
        String contactPhone,
        LocalDate expireDate,
        Integer status,
        String statusLabel,
        LocalDateTime createdAt
) {

    public static TenantResponse from(Tenant tenant) {
        return new TenantResponse(
                tenant.getId(),
                tenant.getCode(),
                tenant.getName(),
                tenant.getRegion(),
                tenant.getRegionName(),
                tenant.getTimezone(),
                tenant.getCurrency(),
                tenant.getContactName(),
                tenant.getContactPhone(),
                tenant.getExpireDate(),
                tenant.getStatus() == null ? null : tenant.getStatus().getValue(),
                tenant.getStatus() == null ? null : tenant.getStatus().getLabel(),
                tenant.getCreatedAt()
        );
    }
}
