package com.okcrm.modules.tenant.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.okcrm.modules.tenant.domain.TenantModule;
import org.apache.ibatis.annotations.Mapper;

/**
 * 租户模块授权 Mapper。
 */
@Mapper
public interface TenantModuleMapper extends BaseMapper<TenantModule> {
}
