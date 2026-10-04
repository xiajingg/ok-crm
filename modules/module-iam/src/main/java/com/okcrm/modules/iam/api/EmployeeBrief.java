package com.okcrm.modules.iam.api;

import com.okcrm.platform.common.enums.EnableStatus;

/**
 * 员工摘要 —— 跨模块传递的只读视图。
 *
 * <p>客户模块分配客户时需要校验「负责人是否存在且在职」，只依赖这个 record，
 * 不依赖 IAM 的实体与表。</p>
 */
public record EmployeeBrief(
        Long id,
        String username,
        String realName,
        String phone,
        EnableStatus status
) {

    public boolean enabled() {
        return status == EnableStatus.ENABLED;
    }

    public String displayName() {
        return realName == null || realName.isBlank() ? username : realName;
    }
}
