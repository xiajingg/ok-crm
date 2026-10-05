package com.okcrm.modules.tenant.api;

import java.util.List;
import java.util.Optional;

/**
 * 企业信息查询服务 —— 其它模块访问企业信息的唯一入口。
 *
 * <p>实现内部会自行处理租户上下文，调用方不需要（也不应该）先设置上下文。</p>
 */
public interface TenantQueryService {

    Optional<TenantBrief> findById(Long tenantId);

    Optional<TenantBrief> findByCode(String tenantCode);

    /**
     * 取当前部署所属的企业。
     *
     * <p>单企业私有化部署下只有一个企业：优先按 {@code okcrm.setup.tenant-id} 取，
     * 取不到时退化为「库里唯一的那条记录」。取不到说明初始化没做，调用方应给出明确提示。</p>
     */
    Optional<TenantBrief> findCurrentDeploymentTenant();

    /**
     * 企业是否存在且处于启用状态。
     */
    boolean isEnabled(Long tenantId);

    /**
     * 全部企业列表。单企业部署下通常只有一条。
     */
    List<TenantBrief> findAll();
}
