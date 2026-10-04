package com.okcrm.modules.tenant.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.okcrm.platform.common.entity.BaseEntity;
import com.okcrm.platform.common.enums.EnableStatus;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 租户（企业）。
 *
 * <p>{@code sys_tenant} 是全局表，不参与租户隔离，因此继承 {@code BaseEntity} 而非
 * {@code TenantEntity} —— 它本身没有 tenant_id 列。</p>
 *
 * <p>地区（{@link #region}）只是租户的一个属性，租户内部不再划分地区层级。</p>
 */
@Getter
@Setter
@TableName("sys_tenant")
public class Tenant extends BaseEntity {

    /** 租户编码，登录时用于定位租户，全局唯一 */
    private String code;

    /** 企业名称 */
    private String name;

    /** 地区编码，如 CN-HUBEI */
    private String region;

    /** 地区名称，如 湖北 */
    private String regionName;

    /** 时区，如 Asia/Shanghai */
    private String timezone;

    /** 币种，如 CNY */
    private String currency;

    /** 联系人姓名 */
    private String contactName;

    /** 联系人电话 */
    private String contactPhone;

    /** 服务到期日 */
    private LocalDate expireDate;

    /** 状态：启用 / 停用 */
    private EnableStatus status;
}
