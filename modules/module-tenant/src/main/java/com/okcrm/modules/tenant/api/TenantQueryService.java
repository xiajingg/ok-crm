package com.okcrm.modules.tenant.api;

import java.util.List;
import java.util.Optional;

/**
 * 租户查询服务 —— 其它模块访问租户信息的唯一入口。
 *
 * <p>实现内部会自行处理租户上下文，调用方不需要（也不应该）先设置上下文。</p>
 */
public interface TenantQueryService {

    Optional<TenantBrief> findById(Long tenantId);

    Optional<TenantBrief> findByCode(String tenantCode);

    /**
     * 租户是否存在且处于启用状态。登录与业务入口的校验点。
     */
    boolean isEnabled(Long tenantId);

    /**
     * 全部租户列表（平台超管用）。
     */
    List<TenantBrief> findAll();

    /**
     * 启用中的租户列表。登录页用它渲染「选择企业」下拉框，
     * 只暴露编码与名称等最小信息。
     */
    List<TenantBrief> listEnabledForLogin();
}
