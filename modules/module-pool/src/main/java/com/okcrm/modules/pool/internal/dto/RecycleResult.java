package com.okcrm.modules.pool.internal.dto;

/**
 * 公海回收任务执行结果。
 *
 * @param tenantsProcessed 实际扫描的租户数
 * @param customersRecycled 被回收的客户数
 * @param skipped          是否因为已有实例在执行而跳过
 */
public record RecycleResult(
        int tenantsProcessed,
        int customersRecycled,
        boolean skipped
) {

    public static RecycleResult of(int tenantsProcessed, int customersRecycled) {
        return new RecycleResult(tenantsProcessed, customersRecycled, false);
    }

    public static RecycleResult skippedResult() {
        return new RecycleResult(0, 0, true);
    }
}
