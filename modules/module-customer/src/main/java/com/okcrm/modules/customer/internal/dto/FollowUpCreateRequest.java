package com.okcrm.modules.customer.internal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * 新增跟进记录请求。
 *
 * <p>写入成功后会同步刷新客户的「最后跟进时间」，公海回收规则依赖这个时间。</p>
 */
public record FollowUpCreateRequest(

        @NotNull(message = "客户不能为空")
        Long customerId,

        /** PHONE / VISIT / WECHAT / EMAIL / OTHER */
        @Size(max = 32)
        String type,

        @NotBlank(message = "跟进内容不能为空")
        @Size(max = 1000, message = "跟进内容最长 1000 位")
        String content,

        /** 跟进时间；留空取当前时间 */
        LocalDateTime followedAt,

        /** 下次跟进计划 */
        LocalDateTime nextFollowUpAt
) {
}
