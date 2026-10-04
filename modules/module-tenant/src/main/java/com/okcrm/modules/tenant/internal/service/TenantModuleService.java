package com.okcrm.modules.tenant.internal.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.okcrm.modules.tenant.domain.ModuleInfo;
import com.okcrm.modules.tenant.domain.TenantModule;
import com.okcrm.modules.tenant.infra.mapper.ModuleInfoMapper;
import com.okcrm.modules.tenant.infra.mapper.TenantModuleMapper;
import com.okcrm.modules.tenant.internal.dto.ModuleResponse;
import com.okcrm.platform.common.enums.EnableStatus;
import com.okcrm.platform.security.spi.ModuleLicenseProvider;
import com.okcrm.platform.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 模块授权服务。
 *
 * <p>同时实现 {@link ModuleLicenseProvider} SPI，让平台层的
 * {@code @PreAuthorize("@module.licensed('pool')")} 能拦住未购买模块的调用。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantModuleService implements ModuleLicenseProvider {

    /** 模块授权缓存名；授权变更时按租户失效 */
    public static final String CACHE_NAME = "tenantModules";

    private final TenantModuleMapper tenantModuleMapper;
    private final ModuleInfoMapper moduleInfoMapper;

    /**
     * 租户已授权且在有效期内的模块集合。
     *
     * <p>缓存键是租户 ID，因此必须由本方法自己设置租户上下文，
     * 不能依赖调用方（平台超管调用时上下文里没有租户）。</p>
     */
    @Override
    @Cacheable(cacheNames = CACHE_NAME, key = "#tenantId", condition = "#tenantId != null")
    public Set<String> licensedModules(Long tenantId) {
        if (tenantId == null) {
            return Set.of();
        }
        return TenantContext.callAs(tenantId, () -> {
            List<TenantModule> rows = tenantModuleMapper.selectList(
                    Wrappers.<TenantModule>lambdaQuery()
                            .eq(TenantModule::getStatus, EnableStatus.ENABLED));

            LocalDate today = LocalDate.now();
            return rows.stream()
                    .filter(row -> row.getExpireDate() == null || !row.getExpireDate().isBefore(today))
                    .map(TenantModule::getModuleKey)
                    .collect(Collectors.toUnmodifiableSet());
        });
    }

    /**
     * 单个模块的授权校验 —— <b>刻意不走缓存，每次都查库</b>。
     *
     * <p>这是「按模块独立售卖」的收费闸门，正确性优先于性能：</p>
     * <ul>
     *   <li>一旦读到脏缓存，撤销授权后客户仍能继续使用已停用的模块 —— 这是直接的钱的问题</li>
     *   <li>本方法只在被 {@code @PreAuthorize("@module.licensed(...)")} 命中时调用，
     *       每次请求最多一次单行查询，代价可以接受</li>
     *   <li>另外这里也刻意不复用 {@link #licensedModules(Long)}：同类内部调用不会走 Spring 代理，
     *       复用会造成「以为在用缓存、实际没用」的误解</li>
     * </ul>
     */
    @Override
    public boolean isLicensed(Long tenantId, String moduleKey) {
        if (tenantId == null || moduleKey == null) {
            return false;
        }
        return TenantContext.callAs(tenantId, () -> {
            List<TenantModule> rows = tenantModuleMapper.selectList(
                    Wrappers.<TenantModule>lambdaQuery()
                            .eq(TenantModule::getModuleKey, moduleKey)
                            .eq(TenantModule::getStatus, EnableStatus.ENABLED));

            LocalDate today = LocalDate.now();
            return rows.stream()
                    .anyMatch(row -> row.getExpireDate() == null || !row.getExpireDate().isBefore(today));
        });
    }

    /**
     * 授权模块（已存在则更新到期日与状态）。
     */
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(cacheNames = CACHE_NAME, key = "#tenantId")
    public void grant(Long tenantId, Collection<String> moduleKeys, LocalDate expireDate, String remark) {
        if (moduleKeys == null || moduleKeys.isEmpty()) {
            return;
        }
        Set<String> existing = TenantContext.callAs(tenantId, () -> tenantModuleMapper.selectList(
                        Wrappers.<TenantModule>lambdaQuery()
                                .in(TenantModule::getModuleKey, moduleKeys))
                .stream()
                .map(TenantModule::getModuleKey)
                .collect(Collectors.toSet()));

        for (String moduleKey : moduleKeys) {
            TenantContext.runAs(tenantId, () -> {
                if (existing.contains(moduleKey)) {
                    TenantModule update = new TenantModule();
                    update.setStatus(EnableStatus.ENABLED);
                    update.setExpireDate(expireDate);
                    update.setRemark(remark);
                    tenantModuleMapper.update(update, Wrappers.<TenantModule>lambdaUpdate()
                            .eq(TenantModule::getModuleKey, moduleKey));
                } else {
                    TenantModule entity = new TenantModule();
                    entity.setModuleKey(moduleKey);
                    entity.setStatus(EnableStatus.ENABLED);
                    entity.setExpireDate(expireDate);
                    entity.setRemark(remark);
                    tenantModuleMapper.insert(entity);
                }
            });
        }
        log.info("租户 {} 授权模块: {}", tenantId, moduleKeys);
    }

    /**
     * 撤销模块授权。
     *
     * <p>刻意用「置为停用」而不是物理/逻辑删除：
     * {@code sys_tenant_module} 上有 {@code (tenant_id, module_key)} 唯一约束，
     * 若走逻辑删除，被删的那行仍然占用唯一键，后续重新授权就会插入失败。
     * 置为停用既避开了这个坑，也保留了「这个客户曾经买过什么」的销售线索。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(cacheNames = CACHE_NAME, key = "#tenantId")
    public void revoke(Long tenantId, String moduleKey) {
        TenantContext.runAs(tenantId, () -> {
            TenantModule update = new TenantModule();
            update.setStatus(EnableStatus.DISABLED);
            tenantModuleMapper.update(update, Wrappers.<TenantModule>lambdaUpdate()
                    .eq(TenantModule::getModuleKey, moduleKey));
        });
        log.info("租户 {} 撤销模块: {}", tenantId, moduleKey);
    }

    /**
     * 产品价目表：全部已注册的可售卖模块。
     */
    public List<ModuleResponse> listCatalog() {
        return moduleInfoMapper.selectList(Wrappers.<ModuleInfo>lambdaQuery()
                        .orderByAsc(ModuleInfo::getSortOrder))
                .stream()
                .map(info -> toResponse(info, false, null))
                .toList();
    }

    /**
     * 某租户的授权明细：价目表 + 该租户是否已授权。
     */
    public List<ModuleResponse> listLicensedDetail(Long tenantId) {
        Set<String> licensed = licensedModules(tenantId);

        Map<String, LocalDate> expireMap = TenantContext.callAs(tenantId, () -> tenantModuleMapper.selectList(
                        Wrappers.<TenantModule>lambdaQuery())
                .stream()
                .collect(Collectors.toMap(TenantModule::getModuleKey, row ->
                        row.getExpireDate() == null ? LocalDate.MAX : row.getExpireDate(),
                        (a, b) -> a, LinkedHashMap::new)));

        return moduleInfoMapper.selectList(Wrappers.<ModuleInfo>lambdaQuery()
                        .orderByAsc(ModuleInfo::getSortOrder))
                .stream()
                .sorted(Comparator.comparing(ModuleInfo::getSortOrder, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(info -> {
                    LocalDate expire = expireMap.get(info.getModuleKey());
                    return toResponse(info, licensed.contains(info.getModuleKey()),
                            expire == LocalDate.MAX ? null : expire);
                })
                .toList();
    }

    /**
     * 开通租户时按需授权；{@code moduleKeys} 为空表示授权全部已注册模块。
     */
    public void grantForNewTenant(Long tenantId, Collection<String> moduleKeys) {
        List<String> targets = (moduleKeys == null || moduleKeys.isEmpty())
                ? moduleInfoMapper.selectList(Wrappers.<ModuleInfo>lambdaQuery())
                .stream().map(ModuleInfo::getModuleKey).toList()
                : List.copyOf(moduleKeys);
        grant(tenantId, targets, null, "开通租户默认授权");
    }

    private ModuleResponse toResponse(ModuleInfo info, boolean licensed, LocalDate expireDate) {
        return new ModuleResponse(
                info.getModuleKey(),
                info.getName(),
                info.getDescription(),
                info.getVersion(),
                info.getCore(),
                info.getSortOrder(),
                licensed,
                expireDate
        );
    }
}
