package com.okcrm.modules.iam.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.okcrm.modules.iam.domain.Permission;
import org.apache.ibatis.annotations.Mapper;

/**
 * 权限点 Mapper。{@code sys_permission} 是全局表，不受租户隔离。
 */
@Mapper
public interface PermissionMapper extends BaseMapper<Permission> {
}
