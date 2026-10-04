package com.okcrm.modules.pool.internal.listener;

import com.okcrm.modules.customer.api.event.CustomerOwnershipChangedEvent;
import com.okcrm.modules.pool.domain.PoolLog;
import com.okcrm.modules.pool.infra.mapper.PoolLogMapper;
import com.okcrm.platform.security.principal.LoginUserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 监听客户归属变更，写入公海池流转日志。
 *
 * <p>用事件而不是「在各个接口里手工记日志」，是为了保证不漏记：
 * 归属既可能从公海池接口改变，也可能从客户模块的转移接口改变，
 * 将来还可能从「员工离职自动释放」改变 —— 这些路径都只需要发同一个事件。</p>
 *
 * <p>同步监听（同一事务）：日志写失败则归属变更一起回滚，避免出现「改了归属但没留痕」。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CustomerOwnershipChangedListener {

    private final PoolLogMapper poolLogMapper;

    @EventListener
    public void onOwnershipChanged(CustomerOwnershipChangedEvent event) {
        PoolLog logEntry = new PoolLog();
        logEntry.setCustomerId(event.customerId());
        logEntry.setCustomerName(event.customerName());
        logEntry.setAction(event.action() == null ? null : event.action().name());
        logEntry.setFromOwnerId(event.fromOwnerId());
        logEntry.setToOwnerId(event.toOwnerId());
        logEntry.setReason(event.reason());
        // 系统自动回收时没有登录用户，这里就是 null，前端展示为「系统」
        logEntry.setOperatorId(LoginUserContext.employeeId());

        poolLogMapper.insert(logEntry);

        log.debug("记录公海流转日志: customerId={}, action={}, {} -> {}",
                event.customerId(), event.action(), event.fromOwnerId(), event.toOwnerId());
    }
}
