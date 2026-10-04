package com.okcrm.modules.iam.internal.dto;

import com.okcrm.modules.iam.domain.Position;
import com.okcrm.platform.common.enums.DataScopeType;
import com.okcrm.platform.common.enums.EnableStatus;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * 岗位详情响应。
 */
public record PositionResponse(
        Long id,
        String code,
        String name,
        String dataScope,
        String dataScopeLabel,
        Integer status,
        String statusLabel,
        Integer sortOrder,
        String remark,
        Set<String> permissions,
        LocalDateTime createdAt
) {

    public static PositionResponse from(Position position, Set<String> permissions) {
        DataScopeType scope = position.getDataScope();
        EnableStatus status = position.getStatus();
        return new PositionResponse(
                position.getId(),
                position.getCode(),
                position.getName(),
                scope == null ? null : scope.getValue(),
                scope == null ? null : scope.getLabel(),
                status == null ? null : status.getValue(),
                status == null ? null : status.getLabel(),
                position.getSortOrder(),
                position.getRemark(),
                permissions == null ? Set.of() : permissions,
                position.getCreatedAt()
        );
    }
}
