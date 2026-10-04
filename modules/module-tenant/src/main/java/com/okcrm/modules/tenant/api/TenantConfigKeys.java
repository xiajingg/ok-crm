package com.okcrm.modules.tenant.api;

/**
 * 租户配置键常量。
 *
 * <p>放在 {@code api} 而不是 {@code domain}：它是模块之间共享的<b>契约</b>，
 * 其它模块（如公海池）需要按键读取配置。放进 domain 会被架构规则判定为
 * 「跨模块访问内部实现」。</p>
 */
public final class TenantConfigKeys {

    private TenantConfigKeys() {
    }

    /** 是否开启公海池自动回收，值：true / false */
    public static final String POOL_RECYCLE_ENABLED = "pool.recycle.enabled";

    /** 公海池回收阈值（天）。超过该天数未按口径跟进则自动回收 */
    public static final String POOL_RECYCLE_DAYS = "pool.recycle.days";

    /**
     * 回收口径：
     * <ul>
     *   <li>{@code LAST_FOLLOWUP} 按最后一次跟进时间（默认）</li>
     *   <li>{@code CREATE_TIME} 按客户创建时间</li>
     * </ul>
     */
    public static final String POOL_RECYCLE_BASIS = "pool.recycle.basis";

    /** 单个员工在公海可同时持有的客户数上限，0 或空表示不限制 */
    public static final String POOL_CLAIM_LIMIT = "pool.claim.limit";

    // ---------- 默认值 ----------

    public static final boolean DEFAULT_RECYCLE_ENABLED = false;
    public static final int DEFAULT_RECYCLE_DAYS = 30;
    public static final String DEFAULT_RECYCLE_BASIS = "LAST_FOLLOWUP";
    public static final int DEFAULT_CLAIM_LIMIT = 0;
}
