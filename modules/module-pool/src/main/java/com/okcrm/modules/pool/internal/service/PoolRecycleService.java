package com.okcrm.modules.pool.internal.service;

import com.okcrm.modules.customer.api.CustomerBrief;
import com.okcrm.modules.customer.api.CustomerOwnershipService;
import com.okcrm.modules.customer.api.CustomerQueryService;
import com.okcrm.modules.customer.api.OwnershipAction;
import com.okcrm.modules.pool.domain.PoolRecycleSettings;
import com.okcrm.modules.pool.internal.dto.RecycleResult;
import com.okcrm.modules.tenant.api.TenantBrief;
import com.okcrm.modules.tenant.api.TenantQueryService;
import com.okcrm.platform.common.lock.DistributedLock;
import com.okcrm.platform.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 公海池自动回收。
 *
 * <p><b>这是整个系统里最容易出跨租户事故的地方</b>，三个坑全部要处理：</p>
 * <ol>
 *   <li><b>上下文</b>：定时任务线程里没有租户上下文。遍历租户时必须
 *       {@link TenantContext#callAs} 包住整个租户的处理过程，否则会 fail-fast 报错；
 *       更糟的是如果某处「兜底」成了默认租户，就会跨租户误回收</li>
 *   <li><b>互斥</b>：多实例部署时两个节点会同时回收同一批客户，
 *       必须用 {@link DistributedLock} 保证同一时刻只有一个实例在跑</li>
 *   <li><b>幂等</b>：客户可能已被手工释放，回收动作要能重复执行不报错</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PoolRecycleService {

    /** 全局回收锁 key */
    public static final String RECYCLE_LOCK_KEY = "okcrm:pool:recycle";

    private final TenantQueryService tenantQueryService;
    private final CustomerQueryService customerQueryService;
    private final CustomerOwnershipService customerOwnershipService;
    private final PoolService poolService;
    private final DistributedLock distributedLock;

    /**
     * 遍历全部启用租户执行回收。
     */
    public RecycleResult recycleAllTenants() {
        return distributedLock.runIfLocked(RECYCLE_LOCK_KEY, Duration.ofMinutes(30), () -> {
            List<TenantBrief> tenants = tenantQueryService.findAll();
            int processed = 0;
            int recycled = 0;

            for (TenantBrief tenant : tenants) {
                if (!tenant.enabled()) {
                    continue;
                }
                processed++;
                try {
                    recycled += recycleTenant(tenant.id());
                } catch (Exception ex) {
                    // 单个租户失败不能中断其它租户
                    log.error("租户 {} 公海回收失败", tenant.code(), ex);
                }
            }
            return RecycleResult.of(processed, recycled);
        }).orElseGet(() -> {
            log.warn("公海回收任务未获得锁，可能有其它实例正在执行，本次跳过");
            return RecycleResult.skippedResult();
        });
    }

    /**
     * 回收单个租户的超期客户。
     *
     * <p>整个方法体都包在 {@code callAs} 里：内部任何一次查询或写入
     * 都会带上正确的 tenant_id，不依赖调用方。</p>
     */
    public int recycleTenant(Long tenantId) {
        if (tenantId == null) {
            return 0;
        }
        return TenantContext.callAs(tenantId, () -> {
            PoolRecycleSettings settings = poolService.settingsOf(tenantId);
            if (!settings.enabled()) {
                log.debug("租户 {} 未启用公海自动回收，跳过", tenantId);
                return 0;
            }

            LocalDateTime deadline = LocalDateTime.now().minusDays(settings.days());
            List<CustomerBrief> candidates =
                    customerQueryService.findRecycleCandidates(tenantId, settings.basis(), deadline);

            if (candidates.isEmpty()) {
                return 0;
            }

            String reason = "超过 " + settings.days() + " 天未跟进，系统自动回收";
            int count = 0;
            for (CustomerBrief candidate : candidates) {
                try {
                    customerOwnershipService.releaseToPool(candidate.id(), OwnershipAction.RECYCLE, reason);
                    count++;
                } catch (Exception ex) {
                    log.warn("回收客户失败: customerId={}", candidate.id(), ex);
                }
            }
            if (count > 0) {
                log.info("租户 {} 自动回收公海客户 {} 个（口径={}，阈值={} 天）",
                        tenantId, count, settings.basis(), settings.days());
            }
            return count;
        });
    }
}
