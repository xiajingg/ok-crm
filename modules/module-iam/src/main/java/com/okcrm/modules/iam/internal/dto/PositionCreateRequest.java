package com.okcrm.modules.iam.internal.dto;

import com.okcrm.platform.common.enums.DataScopeType;
import com.okcrm.platform.common.enums.EnableStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 新增岗位请求。岗位即角色，所以权限集合在同一个请求里提交。
 */
public record PositionCreateRequest(

        @NotBlank(message = "岗位编码不能为空")
        @Size(max = 64, message = "岗位编码最长 64 位")
        String code,

        @NotBlank(message = "岗位名称不能为空")
        @Size(max = 64, message = "岗位名称最长 64 位")
        String name,

        @NotNull(message = "数据范围不能为空")
        DataScopeType dataScope,

        EnableStatus status,

        Integer sortOrder,

        @Size(max = 255, message = "备注最长 255 位")
        String remark,

        /** 授予该岗位的权限码集合；为空表示不授予任何权限 */
        List<String> permissions
) {
}
