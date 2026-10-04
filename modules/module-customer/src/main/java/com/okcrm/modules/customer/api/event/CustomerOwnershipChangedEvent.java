package com.okcrm.modules.customer.api.event;

import com.okcrm.modules.customer.api.OwnershipAction;

/**
 * 客户归属变更事件。
 *
 * <p>由客户模块在归属发生变化后发布；公海池模块监听它写入
 * {@code crm_pool_log} 流转日志。</p>
 *
 * <p><b>为什么用事件而不是让公海池模块直接写日志</b>：客户归属也可能从客户模块
 * 自己的接口（主管转移客户）被改变，如果日志写在公海池模块的接口里，
 * 这条路径就会被漏掉。事件驱动保证「任何路径的归属变化都留痕」。</p>
 */
public record CustomerOwnershipChangedEvent(
        Long customerId,
        String customerName,
        /** 变更前负责人；为 null 表示之前就在公海 */
        Long fromOwnerId,
        /** 变更后负责人；为 null 表示退回公海 */
        Long toOwnerId,
        OwnershipAction action,
        String reason
) {
}
