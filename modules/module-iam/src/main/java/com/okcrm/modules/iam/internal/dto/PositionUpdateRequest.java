package com.okcrm.modules.iam.internal.dto;

import com.okcrm.platform.common.enums.DataScopeType;
import com.okcrm.platform.common.enums.EnableStatus;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 更新岗位请求。编码不可修改。
 */
public record PositionUpdateRequest(

        @Size(max = 64, message = "岗位名称最长 64 位")
        String name,

        DataScopeType dataScope,

        EnableStatus status,

        Integer sortOrder,

        @Size(max = 255, message = "备注最长 255 位")
        String remark,

        /** 传 null 表示不改权限；传空数组表示清空权限 */
        List<String> permissions
) {
}
