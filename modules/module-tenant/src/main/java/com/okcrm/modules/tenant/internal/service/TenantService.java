package com.okcrm.modules.tenant.internal.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.okcrm.modules.tenant.api.TenantBrief;
import com.okcrm.modules.tenant.api.TenantQueryService;
import com.okcrm.modules.tenant.api.event.TenantCreatedEvent;
import com.okcrm.modules.tenant.domain.Tenant;
import com.okcrm.modules.tenant.infra.mapper.TenantMapper;
import com.okcrm.modules.tenant.internal.dto.TenantCreateRequest;
import com.okcrm.modules.tenant.internal.dto.TenantResponse;
import com.okcrm.modules.tenant.internal.dto.TenantUpdateRequest;
import com.okcrm.platform.common.api.ErrorCode;
import com.okcrm.platform.common.api.PageResult;
import com.okcrm.platform.common.enums.EnableStatus;
import com.okcrm.platform.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

/**
 * 租户服务。
 *
 * <p>{@code sys_tenant} 是全局表（不参与租户隔离），所以这里的查询不需要租户上下文，
 * 这也是登录流程能工作的前提 —— 登录时还不知道用户属于哪个租户。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantService implements TenantQueryService {

    private final TenantMapper tenantMapper;
    private final TenantModuleService tenantModuleService;
    private final ApplicationEventPublisher eventPublisher;

    // ---------------------------------------------------------------- 查询

    @Override
    public Optional<TenantBrief> findById(Long tenantId) {
        if (tenantId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(tenantMapper.selectById(tenantId)).map(TenantService::toBrief);
    }

    @Override
    public Optional<TenantBrief> findByCode(String tenantCode) {
        if (!StringUtils.hasText(tenantCode)) {
            return Optional.empty();
        }
        return Optional.ofNullable(tenantMapper.selectOne(
                Wrappers.<Tenant>lambdaQuery().eq(Tenant::getCode, tenantCode))).map(TenantService::toBrief);
    }

    @Override
    public boolean isEnabled(Long tenantId) {
        return findById(tenantId).map(TenantBrief::enabled).orElse(false);
    }

    @Override
    public List<TenantBrief> findAll() {
        return tenantMapper.selectList(Wrappers.<Tenant>lambdaQuery()
                        .orderByDesc(Tenant::getCreatedAt))
                .stream()
                .map(TenantService::toBrief)
                .toList();
    }

    /**
     * 登录页用：列出启用中的租户编码与名称，供用户选择。
     * 只暴露最小信息，避免未登录状态泄露客户资料。
     */
    @Override
    public List<TenantBrief> listEnabledForLogin() {
        return tenantMapper.selectList(Wrappers.<Tenant>lambdaQuery()
                        .eq(Tenant::getStatus, EnableStatus.ENABLED)
                        .orderByAsc(Tenant::getName))
                .stream()
                .map(TenantService::toBrief)
                .toList();
    }

    public PageResult<TenantResponse> page(String keyword, long pageNum, long pageSize) {
        Page<Tenant> page = new Page<>(pageNum, pageSize);
        var wrapper = Wrappers.<Tenant>lambdaQuery();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(Tenant::getCode, keyword).or().like(Tenant::getName, keyword));
        }
        wrapper.orderByDesc(Tenant::getCreatedAt);

        Page<Tenant> result = tenantMapper.selectPage(page, wrapper);
        List<TenantResponse> records = result.getRecords().stream().map(TenantResponse::from).toList();
        return PageResult.of(records, result.getTotal(), result.getCurrent(), result.getSize());
    }

    public TenantResponse detail(Long tenantId) {
        Tenant tenant = tenantMapper.selectById(tenantId);
        if (tenant == null) {
            throw BizException.of(ErrorCode.TENANT_NOT_FOUND);
        }
        return TenantResponse.from(tenant);
    }

    // ---------------------------------------------------------------- 写入

    /**
     * 开通租户。
     *
     * <p>三步在同一事务内完成：建租户 → 授权模块 → 发布开通事件。
     * 事件用同步监听，这样「初始化默认岗位」失败会整体回滚，
     * 不会留下一个没有管理员岗位的残缺租户。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public TenantResponse create(TenantCreateRequest request) {
        Long exists = tenantMapper.selectCount(Wrappers.<Tenant>lambdaQuery()
                .eq(Tenant::getCode, request.code()));
        if (exists != null && exists > 0) {
            throw BizException.of(ErrorCode.TENANT_CODE_EXISTS);
        }

        Tenant tenant = new Tenant();
        tenant.setCode(request.code());
        tenant.setName(request.name());
        tenant.setRegion(request.region());
        tenant.setRegionName(request.regionName());
        tenant.setTimezone(StringUtils.hasText(request.timezone()) ? request.timezone() : "Asia/Shanghai");
        tenant.setCurrency(StringUtils.hasText(request.currency()) ? request.currency() : "CNY");
        tenant.setContactName(request.contactName());
        tenant.setContactPhone(request.contactPhone());
        tenant.setExpireDate(request.expireDate());
        tenant.setStatus(EnableStatus.ENABLED);
        tenantMapper.insert(tenant);

        tenantModuleService.grantForNewTenant(tenant.getId(), request.modules());

        eventPublisher.publishEvent(new TenantCreatedEvent(
                tenant.getId(), tenant.getCode(), tenant.getName(), tenant.getRegion()));

        log.info("开通租户成功: code={}, id={}", tenant.getCode(), tenant.getId());
        return TenantResponse.from(tenant);
    }

    @Transactional(rollbackFor = Exception.class)
    public TenantResponse update(Long tenantId, TenantUpdateRequest request) {
        Tenant existing = tenantMapper.selectById(tenantId);
        if (existing == null) {
            throw BizException.of(ErrorCode.TENANT_NOT_FOUND);
        }

        Tenant update = new Tenant();
        update.setId(tenantId);
        update.setName(request.name());
        update.setRegion(request.region());
        update.setRegionName(request.regionName());
        update.setTimezone(request.timezone());
        update.setCurrency(request.currency());
        update.setContactName(request.contactName());
        update.setContactPhone(request.contactPhone());
        update.setExpireDate(request.expireDate());
        tenantMapper.updateById(update);

        return detail(tenantId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(Long tenantId, boolean enabled) {
        Tenant existing = tenantMapper.selectById(tenantId);
        if (existing == null) {
            throw BizException.of(ErrorCode.TENANT_NOT_FOUND);
        }
        Tenant update = new Tenant();
        update.setId(tenantId);
        update.setStatus(enabled ? EnableStatus.ENABLED : EnableStatus.DISABLED);
        tenantMapper.updateById(update);
        log.info("租户 {} 状态变更为 {}", tenantId, enabled ? "启用" : "停用");
    }

    private static TenantBrief toBrief(Tenant tenant) {
        return new TenantBrief(
                tenant.getId(),
                tenant.getCode(),
                tenant.getName(),
                tenant.getRegion(),
                tenant.getRegionName(),
                tenant.getTimezone(),
                tenant.getCurrency(),
                tenant.getStatus()
        );
    }
}
