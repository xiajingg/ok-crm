package com.okcrm.platform.common.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Getter;
import lombok.Setter;

/**
 * 租户级实体基类。
 *
 * <p><b>tenantId 不要手动赋值</b>：写入时由 {@code TenantLineInnerInterceptor} 自动补列，
 * 查询时自动追加 {@code tenant_id = ?} 条件。这里声明字段只为读取。</p>
 */
@Getter
@Setter
public abstract class TenantEntity extends BaseEntity {

    @TableField(value = "tenant_id")
    private Long tenantId;
}
