package com.okcrm.modules.tenant.api.event;

/**
 * 租户开通事件。
 *
 * <p>由租户模块在租户创建成功后发布；IAM 模块监听它来初始化默认岗位模板
 * （销售、销售主管、管理员）。</p>
 *
 * <p><b>这是模块解耦的关键手法</b>：租户模块不需要知道「谁要初始化什么」，
 * 也不需要依赖 IAM 模块。将来新增「开通时初始化客户标签」等逻辑，
 * 只要再加一个监听者，租户模块一行代码都不用改。</p>
 */
public record TenantCreatedEvent(
        Long tenantId,
        String tenantCode,
        String tenantName,
        String region
) {
}
