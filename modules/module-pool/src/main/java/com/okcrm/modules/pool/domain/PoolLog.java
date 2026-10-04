package com.okcrm.modules.pool.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.okcrm.modules.customer.api.OwnershipAction;
import com.okcrm.platform.common.entity.TenantEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 公海池流转日志。
 *
 * <p>每一次归属变化都留一条记录，用于：</p>
 * <ul>
 *   <li>客户详情页的「流转轨迹」</li>
 *   <li>纠纷时追溯「这个客户是谁、什么时候放进公海的」</li>
 *   <li>统计员工领取/回收行为，反推客户质量</li>
 * </ul>
 *
 * <p>写入方式：监听 {@code CustomerOwnershipChangedEvent}，而不是在各个业务接口里手工记录。
 * 这样无论归属从哪条路径被改变，都不会漏记。</p>
 */
@Getter
@Setter
@TableName("crm_pool_log")
public class PoolLog extends TenantEntity {

    private Long customerId;

    /** 冗余客户名：客户被删除后日志仍然可读 */
    private String customerName;

    /** 动作类型，见 {@link OwnershipAction} */
    private String action;

    /** 变更前负责人；为 null 表示之前在公海 */
    private Long fromOwnerId;

    /** 变更后负责人；为 null 表示回到公海 */
    private Long toOwnerId;

    /** 原因或备注 */
    private String reason;

    /** 操作人；为 null 表示系统自动执行（如定时回收） */
    private Long operatorId;
}
