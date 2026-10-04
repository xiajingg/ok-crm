package com.okcrm.modules.tenant.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.okcrm.modules.tenant.domain.Tenant;
import org.apache.ibatis.annotations.Mapper;

/**
 * 租户表 Mapper。{@code sys_tenant} 为全局表，不受租户隔离影响。
 */
@Mapper
public interface TenantMapper extends BaseMapper<Tenant> {
}
