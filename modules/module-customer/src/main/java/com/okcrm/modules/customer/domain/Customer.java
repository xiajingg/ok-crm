package com.okcrm.modules.customer.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.okcrm.platform.common.entity.TenantEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 客户（企业客户主体）。
 *
 * <p><b>归属模型</b>：{@code ownerId} 为 null 表示客户在<b>公海池</b>中，
 * 否则表示归属某个员工。用「一个可空字段」而不是「状态枚举 + 归属人」两个字段，
 * 是为了从数据层面保证「归属人与池状态」不会互相矛盾。</p>
 *
 * <p>三个时间字段是公海池回收规则的基础，缺一不可：</p>
 * <ul>
 *   <li>{@link #ownerAssignedAt} 本次归属开始时间</li>
 *   <li>{@link #lastFollowUpAt} 最后一次跟进时间（回收口径之一）</li>
 *   <li>{@link #enterPoolAt} 进入公海的时间</li>
 * </ul>
 */
@Getter
@Setter
@TableName("crm_customer")
public class Customer extends TenantEntity {

    /** 客户名称 */
    private String name;

    /** 行业 */
    private String industry;

    /** 客户级别：A / B / C */
    private String level;

    /** 客户来源：自主开发 / 转介绍 / 展会 / 广告 等 */
    private String source;

    /** 客户主电话 */
    private String phone;

    private String address;

    /**
     * 负责人（员工 ID）。<b>为 null 表示在公海池中。</b>
     */
    private Long ownerId;

    /** 本次归属生效时间 */
    private LocalDateTime ownerAssignedAt;

    /** 最后一次跟进时间；为 null 表示从未跟进 */
    private LocalDateTime lastFollowUpAt;

    /** 进入公海池的时间 */
    private LocalDateTime enterPoolAt;

    /** 进入公海池的原因（自动回收 / 手动退回 / 员工离职） */
    private String poolReason;

    /** 标签，逗号分隔 */
    private String tags;

    private String remark;
}
