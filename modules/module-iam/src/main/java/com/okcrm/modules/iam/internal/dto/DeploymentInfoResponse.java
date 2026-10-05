package com.okcrm.modules.iam.internal.dto;

/**
 * 部署信息（登录页展示用，无需登录即可访问）。
 *
 * <p>只暴露企业名称与编码 —— 登录页要显示「XX公司 客户管理系统」，
 * 但没必要把地区、状态这些也公开出去。</p>
 */
public record DeploymentInfoResponse(
        String tenantName,
        String tenantCode
) {
}
