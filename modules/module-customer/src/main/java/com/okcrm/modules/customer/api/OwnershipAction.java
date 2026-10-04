package com.okcrm.modules.customer.api;

/**
 * 客户归属变更的动作类型。用于公海池流转日志的可读性与统计。
 */
public enum OwnershipAction {

    /** 员工从公海池主动领取 */
    CLAIM,

    /** 主管指派给某员工 */
    ASSIGN,

    /** 员工之间的转移 */
    TRANSFER,

    /** 手动退回公海池 */
    RELEASE,

    /** 超期未跟进被系统自动回收 */
    RECYCLE
}
