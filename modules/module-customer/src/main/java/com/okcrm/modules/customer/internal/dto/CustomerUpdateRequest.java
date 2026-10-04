package com.okcrm.modules.customer.internal.dto;

import jakarta.validation.constraints.Size;

/**
 * 修改客户请求。
 *
 * <p><b>刻意不包含 ownerId</b>：归属变更必须走专门的分配/转移接口，
 * 这样每一次归属变化都能被记录到公海池流转日志里，不会被「顺手改字段」绕过。</p>
 */
public record CustomerUpdateRequest(

        @Size(max = 128, message = "客户名称最长 128 位")
        String name,

        @Size(max = 64)
        String industry,

        @Size(max = 8)
        String level,

        @Size(max = 64)
        String source,

        @Size(max = 32)
        String phone,

        @Size(max = 255)
        String address,

        @Size(max = 255)
        String tags,

        @Size(max = 500)
        String remark
) {
}
