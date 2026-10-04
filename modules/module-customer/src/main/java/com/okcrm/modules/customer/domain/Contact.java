package com.okcrm.modules.customer.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.okcrm.platform.common.entity.TenantEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 客户联系人。一个客户可以有多个联系人。
 */
@Getter
@Setter
@TableName("crm_contact")
public class Contact extends TenantEntity {

    private Long customerId;

    private String name;

    /** 职务 */
    private String position;

    private String phone;

    private String email;

    /** 是否主要联系人 */
    private Boolean primaryContact;

    private String remark;
}
