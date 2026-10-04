package com.okcrm.modules.tenant.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.okcrm.modules.tenant.api.TenantConfigKeys;
import com.okcrm.platform.common.entity.TenantEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 租户级配置（KV 形式）。
 *
 * <p>之所以做成 KV 而不是加字段：不同租户对同一功能的诉求差异很大
 * （例如公海回收天数），KV 能避免为了单个租户的需求改表结构。</p>
 */
@Getter
@Setter
@TableName("sys_tenant_config")
public class TenantConfig extends TenantEntity {

    /** 配置键，见 {@link TenantConfigKeys} */
    private String configKey;

    /** 配置值，统一按字符串存储，读取时按类型转换 */
    private String configValue;

    /** 说明 */
    private String remark;
}
