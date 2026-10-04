package com.okcrm.modules.pool.domain;

import com.okcrm.modules.customer.api.RecycleBasis;

/**
 * 公海回收规则（从租户配置解析出来的值对象）。
 *
 * @param enabled    是否启用自动回收
 * @param days       超期天数阈值
 * @param basis      时间口径
 * @param claimLimit 单人最多持有的公海客户数；0 表示不限制
 */
public record PoolRecycleSettings(
        boolean enabled,
        int days,
        RecycleBasis basis,
        int claimLimit
) {

    public boolean claimLimited() {
        return claimLimit > 0;
    }
}
