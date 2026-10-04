package com.okcrm.modules.pool.internal.job;

import com.okcrm.modules.pool.internal.dto.RecycleResult;
import com.okcrm.modules.pool.internal.service.PoolRecycleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 公海池自动回收定时任务。
 *
 * <p>默认每天凌晨 2 点执行，cron 可通过 {@code okcrm.pool.recycle-cron} 调整。
 * 测试环境用 {@code okcrm.pool.recycle-enabled=false} 关掉，避免干扰用例。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "okcrm.pool.recycle-enabled", havingValue = "true", matchIfMissing = true)
public class PoolRecycleJob {

    private final PoolRecycleService poolRecycleService;

    @Scheduled(cron = "${okcrm.pool.recycle-cron:0 0 2 * * ?}")
    public void recycleExpiredCustomers() {
        long start = System.currentTimeMillis();
        log.info("【公海回收】任务开始");
        try {
            RecycleResult result = poolRecycleService.recycleAllTenants();
            log.info("【公海回收】任务结束: 扫描租户={}, 回收客户={}, 跳过={}, 耗时={}ms",
                    result.tenantsProcessed(), result.customersRecycled(), result.skipped(),
                    System.currentTimeMillis() - start);
        } catch (Exception ex) {
            log.error("【公海回收】任务异常", ex);
        }
    }
}
