package com.okcrm.modules.tenant.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.okcrm.modules.tenant.domain.TenantConfig;
import org.apache.ibatis.annotations.Mapper;

/**
 * 租户配置 Mapper。租户级表，SQL 自动带 tenant_id 条件。
 */
@Mapper
public interface TenantConfigMapper extends BaseMapper<TenantConfig> {
}
