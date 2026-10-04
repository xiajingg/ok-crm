package com.okcrm.modules.pool.internal.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.okcrm.modules.customer.api.CustomerBrief;
import com.okcrm.modules.customer.api.CustomerOwnershipService;
import com.okcrm.modules.customer.api.CustomerQueryService;
import com.okcrm.modules.customer.api.OwnershipAction;
import com.okcrm.modules.customer.api.RecycleBasis;
import com.okcrm.modules.iam.api.EmployeeBrief;
import com.okcrm.modules.iam.api.EmployeeQueryService;
import com.okcrm.modules.pool.domain.PoolLog;
import com.okcrm.modules.pool.domain.PoolRecycleSettings;
import com.okcrm.modules.pool.infra.mapper.PoolLogMapper;
import com.okcrm.modules.pool.internal.dto.PoolLogResponse;
import com.okcrm.modules.pool.internal.dto.PoolSettingsRequest;
import com.okcrm.modules.tenant.api.TenantConfigKeys;
import com.okcrm.modules.tenant.api.TenantConfigQueryService;
import com.okcrm.platform.common.api.ErrorCode;
import com.okcrm.platform.common.api.PageResult;
import com.okcrm.platform.common.exception.BizException;
import com.okcrm.platform.security.principal.LoginUserContext;
import com.okcrm.platform.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 公海池服务：领取、指派、退回、流转日志、回收规则读写。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PoolService {

    private final CustomerQueryService customerQueryService;
    private final CustomerOwnershipService customerOwnershipService;
    private final TenantConfigQueryService tenantConfigQueryService;
    private final EmployeeQueryService employeeQueryService;
    private final PoolLogMapper poolLogMapper;

    // ------------------------------------------------------------ 公海池操作

    /**
     * 公海列表。不做数据权限过滤 —— 公海客户本就无归属，过滤后谁都看不到。
     */
    public PageResult<CustomerBrief> page(String keyword, long pageNum, long pageSize) {
        return customerQueryService.pagePoolCustomers(keyword, pageNum, pageSize);
    }

    /**
     * 员工主动领取。
     */
    @Transactional(rollbackFor = Exception.class)
    public void claim(Long customerId) {
        Long me = LoginUserContext.employeeId();
        if (me == null) {
            throw BizException.of(ErrorCode.UNAUTHORIZED);
        }
        checkClaimLimit(me);
        customerOwnershipService.assignOwner(customerId, me, OwnershipAction.CLAIM, "员工主动领取");
    }

    /**
     * 主管指派。
     */
    @Transactional(rollbackFor = Exception.class)
    public void assign(Long customerId, Long employeeId, String remark) {
        customerOwnershipService.assignOwner(customerId, employeeId, OwnershipAction.ASSIGN,
                StringUtils.hasText(remark) ? remark : "主管指派");
    }

    /**
     * 退回公海。
     */
    @Transactional(rollbackFor = Exception.class)
    public void release(Long customerId, String reason) {
        customerOwnershipService.releaseToPool(customerId, OwnershipAction.RELEASE,
                StringUtils.hasText(reason) ? reason : "手动退回");
    }

    // ------------------------------------------------------------ 流转日志

    public PageResult<PoolLogResponse> logs(Long customerId, long pageNum, long pageSize) {
        var wrapper = Wrappers.<PoolLog>lambdaQuery();
        if (customerId != null) {
            wrapper.eq(PoolLog::getCustomerId, customerId);
        }
        wrapper.orderByDesc(PoolLog::getCreatedAt);

        Page<PoolLog> page = poolLogMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        return PageResult.of(toResponses(page.getRecords()), page.getTotal(), page.getCurrent(), page.getSize());
    }

    // ------------------------------------------------------------ 回收规则

    public PoolRecycleSettings settings() {
        return settingsOf(TenantContext.requireTenantId());
    }

    /**
     * 读取指定租户的回收规则。定时任务遍历租户时使用，因此必须显式传 tenantId。
     */
    public PoolRecycleSettings settingsOf(Long tenantId) {
        return new PoolRecycleSettings(
                tenantConfigQueryService.getBoolean(tenantId, TenantConfigKeys.POOL_RECYCLE_ENABLED,
                        TenantConfigKeys.DEFAULT_RECYCLE_ENABLED),
                tenantConfigQueryService.getInt(tenantId, TenantConfigKeys.POOL_RECYCLE_DAYS,
                        TenantConfigKeys.DEFAULT_RECYCLE_DAYS),
                parseBasis(tenantConfigQueryService.getString(tenantId, TenantConfigKeys.POOL_RECYCLE_BASIS,
                        TenantConfigKeys.DEFAULT_RECYCLE_BASIS)),
                tenantConfigQueryService.getInt(tenantId, TenantConfigKeys.POOL_CLAIM_LIMIT,
                        TenantConfigKeys.DEFAULT_CLAIM_LIMIT)
        );
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateSettings(PoolSettingsRequest request) {
        Long tenantId = TenantContext.requireTenantId();
        tenantConfigQueryService.put(tenantId, TenantConfigKeys.POOL_RECYCLE_ENABLED,
                String.valueOf(Boolean.TRUE.equals(request.enabled())));
        if (request.days() != null) {
            tenantConfigQueryService.put(tenantId, TenantConfigKeys.POOL_RECYCLE_DAYS,
                    String.valueOf(request.days()));
        }
        if (request.basis() != null) {
            tenantConfigQueryService.put(tenantId, TenantConfigKeys.POOL_RECYCLE_BASIS,
                    request.basis().name());
        }
        if (request.claimLimit() != null) {
            tenantConfigQueryService.put(tenantId, TenantConfigKeys.POOL_CLAIM_LIMIT,
                    String.valueOf(request.claimLimit()));
        }
        log.info("租户 {} 更新公海回收规则: enabled={}, days={}, basis={}, claimLimit={}",
                tenantId, request.enabled(), request.days(), request.basis(), request.claimLimit());
    }

    // ------------------------------------------------------------ 内部

    private void checkClaimLimit(Long employeeId) {
        PoolRecycleSettings settings = settings();
        if (!settings.claimLimited()) {
            return;
        }
        long owned = customerQueryService.countByOwner(employeeId);
        if (owned >= settings.claimLimit()) {
            throw BizException.of(ErrorCode.BAD_REQUEST,
                    "你当前负责的客户已达上限（" + settings.claimLimit() + " 个），请先释放部分客户");
        }
    }

    private static RecycleBasis parseBasis(String raw) {
        if (!StringUtils.hasText(raw)) {
            return RecycleBasis.LAST_FOLLOWUP;
        }
        try {
            return RecycleBasis.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            log.warn("无法识别的公海回收口径 [{}]，回退为 LAST_FOLLOWUP", raw);
            return RecycleBasis.LAST_FOLLOWUP;
        }
    }

    private List<PoolLogResponse> toResponses(List<PoolLog> logs) {
        if (logs.isEmpty()) {
            return List.of();
        }
        Set<Long> employeeIds = logs.stream()
                .flatMap(log -> java.util.stream.Stream.of(log.getFromOwnerId(), log.getToOwnerId(),
                        log.getOperatorId()))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, String> nameMap = employeeIds.isEmpty()
                ? Collections.emptyMap()
                : employeeQueryService.findByIds(employeeIds).stream()
                .collect(Collectors.toMap(EmployeeBrief::id, EmployeeBrief::displayName, (a, b) -> a));

        return logs.stream()
                .map(log -> new PoolLogResponse(
                        log.getId(),
                        log.getCustomerId(),
                        log.getCustomerName(),
                        log.getAction(),
                        actionLabel(log.getAction()),
                        log.getFromOwnerId(),
                        nameOf(nameMap, log.getFromOwnerId()),
                        log.getToOwnerId(),
                        nameOf(nameMap, log.getToOwnerId()),
                        log.getReason(),
                        log.getOperatorId(),
                        log.getOperatorId() == null ? "系统" : nameOf(nameMap, log.getOperatorId()),
                        log.getCreatedAt()))
                .toList();
    }

    private static String nameOf(Map<Long, String> nameMap, Long id) {
        return id == null ? null : nameMap.get(id);
    }

    private static String actionLabel(String action) {
        if (action == null) {
            return "";
        }
        try {
            return switch (OwnershipAction.valueOf(action)) {
                case CLAIM -> "领取";
                case ASSIGN -> "指派";
                case TRANSFER -> "转移";
                case RELEASE -> "退回公海";
                case RECYCLE -> "超期自动回收";
            };
        } catch (IllegalArgumentException ex) {
            return action;
        }
    }
}
