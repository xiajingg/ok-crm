package com.okcrm.modules.iam.internal.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 员工详情响应。绝不包含密码字段。
 */
public record EmployeeResponse(
        Long id,
        String username,
        String realName,
        String phone,
        String email,
        Integer status,
        String statusLabel,
        String remark,
        /** 关联岗位 ID */
        List<Long> positionIds,
        /** 关联岗位名称，便于列表直接展示 */
        List<String> positionNames,
        LocalDateTime lastLoginAt,
        LocalDateTime createdAt
) {
}
