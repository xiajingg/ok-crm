package com.okcrm.modules.tenant.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.okcrm.platform.common.entity.TenantEntity;
import com.okcrm.platform.common.enums.EnableStatus;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 租户已授权模块 —— 「按模块独立售卖」在运行期的授权台账。
 *
 * <p>与编译期的 Maven profile 裁剪配合使用：</p>
 * <ul>
 *   <li>编译期裁剪决定「客户拿到的包里有没有这段代码」</li>
 *   <li>本表决定「已部署的实例对哪些租户开放该模块」</li>
 * </ul>
 * <p>SaaS 形态下多个租户共用一个部署包，靠本表区分各自买了什么。</p>
 */
@Getter
@Setter
@TableName("sys_tenant_module")
public class TenantModule extends TenantEntity {

    /** 模块标识，取值见 {@code com.okcrm.platform.common.constant.ModuleKeys} */
    private String moduleKey;

    /** 授权状态 */
    private EnableStatus status;

    /** 授权到期日，为空表示永久 */
    private LocalDate expireDate;

    /** 备注 */
    private String remark;
}
