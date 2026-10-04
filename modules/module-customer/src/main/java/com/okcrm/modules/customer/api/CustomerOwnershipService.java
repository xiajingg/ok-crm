package com.okcrm.modules.customer.api;

/**
 * 客户归属变更服务 —— 公海池模块通过它改变客户归属。
 *
 * <p>客户表由本模块独占，公海池模块只负责「规则与日志」，
 * 不直接写 {@code crm_customer}。这样「客户归属」只有一个写入口，
 * 也就不可能出现「归属改了但没记日志」的漏账。</p>
 */
public interface CustomerOwnershipService {

    /**
     * 把客户分配给某员工。公海领取、主管指派、员工间转移都走这里。
     *
     * @param action 动作类型，决定流转日志的语义
     * @param reason 备注
     * @throws com.okcrm.platform.common.exception.BizException 领取时客户已被他人领走
     */
    void assignOwner(Long customerId, Long employeeId, OwnershipAction action, String reason);

    /**
     * 把客户退回公海池。
     *
     * @param action {@link OwnershipAction#RELEASE} 手动退回，
     *               {@link OwnershipAction#RECYCLE} 系统自动回收
     */
    void releaseToPool(Long customerId, OwnershipAction action, String reason);
}
