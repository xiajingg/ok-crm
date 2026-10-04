package com.okcrm.modules.customer.api;

/**
 * 公海回收的时间口径。
 *
 * <p>不同企业的管理习惯差别很大：有的按「最后一次跟进」算超期，
 * 有的按「客户录入时间」算。所以口径必须可配，不能写死。</p>
 */
public enum RecycleBasis {

    /** 按最后一次跟进时间；从未跟进的客户按创建时间算 */
    LAST_FOLLOWUP,

    /** 按客户创建（录入）时间 */
    CREATE_TIME
}
