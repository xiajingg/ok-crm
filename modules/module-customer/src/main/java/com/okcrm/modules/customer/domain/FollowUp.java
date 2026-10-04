package com.okcrm.modules.customer.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.okcrm.platform.common.entity.TenantEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 跟进记录。
 *
 * <p>除了「记录沟通」之外，它还承担一个关键职责：<b>驱动公海池回收规则</b>。
 * 写入跟进记录时会同步刷新 {@code crm_customer.last_followup_at}，
 * 回收任务据此判断客户是否超期。</p>
 */
@Getter
@Setter
@TableName("crm_followup")
public class FollowUp extends TenantEntity {

    private Long customerId;

    /** 跟进人 */
    private Long employeeId;

    /** 跟进方式：PHONE / VISIT / WECHAT / EMAIL / OTHER */
    private String type;

    /** 跟进内容 */
    private String content;

    /** 跟进时间 */
    private LocalDateTime followedAt;

    /** 下次跟进计划时间 */
    private LocalDateTime nextFollowUpAt;
}
