package com.okcrm.modules.tenant.internal.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.okcrm.modules.tenant.api.TenantBrief;
import com.okcrm.modules.tenant.api.TenantQueryService;
import com.okcrm.modules.tenant.domain.Tenant;
import com.okcrm.modules.tenant.infra.mapper.TenantMapper;
import com.okcrm.modules.tenant.internal.config.SetupProperties;
import com.okcrm.modules.tenant.internal.dto.TenantResponse;
import com.okcrm.modules.tenant.internal.dto.TenantUpdateRequest;
import com.okcrm.platform.common.api.ErrorCode;
import com.okcrm.platform.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 企业信息服务。
 *
 * <p>{@code sys_tenant} 是全局表（不参与租户隔离），所以这里的查询不需要租户上下文。</p>
 *
 * <p>单企业私有化部署下：企业记录由 {@code DeploymentSetupRunner} 在首次启动时创建，
 * 之后这里只提供「读取当前企业」与「修改企业信息（企业设置页）」两件事 ——
 * 不再有开通、停用、分页管理这些多租户运营动作。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantService implements TenantQueryService {

    private final TenantMapper tenantMapper;
    private final SetupProperties setupProperties;

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
        if (tenantCode == null || tenantCode.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(tenantMapper.selectOne(
                Wrappers.<Tenant>lambdaQuery().eq(Tenant::getCode, tenantCode))).map(TenantService::toBrief);
    }

    @Override
    public Optional<TenantBrief> findCurrentDeploymentTenant() {
        Long configuredId = setupProperties.getTenantId();
        if (configuredId != null) {
            Tenant byId = tenantMapper.selectById(configuredId);
            if (byId != null) {
                return Optional.of(toBrief(byId));
            }
        }

        // 退化路径：库里恰好只有一条就认它（例如客户改了 tenant-id 配置但没重建库）
        List<Tenant> all = tenantMapper.selectList(Wrappers.<Tenant>lambdaQuery()
                .orderByAsc(Tenant::getCreatedAt));

        if (all.size() == 1) {
            log.warn("按配置的 okcrm.setup.tenant-id={} 未找到企业，回退使用库中唯一的企业 id={}",
                    configuredId, all.get(0).getId());
            return Optional.of(toBrief(all.get(0)));
        }
        if (all.isEmpty()) {
            return Optional.empty();
        }
        log.error("库中存在 {} 条企业记录且都不匹配 okcrm.setup.tenant-id={}，无法确定当前企业。"
                + "请检查配置，或清理多余的企业记录。", all.size(), configuredId);
        return Optional.empty();
    }

    @Override
    public boolean isEnabled(Long tenantId) {
        return findById(tenantId).map(TenantBrief::enabled).orElse(false);
    }

    @Override
    public List<TenantBrief> findAll() {
        return tenantMapper.selectList(Wrappers.<Tenant>lambdaQuery()
                        .orderByAsc(Tenant::getCreatedAt))
                .stream()
                .map(TenantService::toBrief)
                .toList();
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
     * 修改企业信息（企业设置页）。
     *
     * <p>企业编码不可改：它出现在日志与对外展示里，改动没有收益只有风险。</p>
     */
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
        tenantMapper.updateById(update);

        log.info("企业信息已更新: id={}, name={}", tenantId, request.name());
        return detail(tenantId);
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
