package com.okcrm.modules.tenant.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.okcrm.modules.tenant.domain.ModuleInfo;
import org.apache.ibatis.annotations.Mapper;

/**
 * 可售卖模块目录 Mapper。全局表。
 */
@Mapper
public interface ModuleInfoMapper extends BaseMapper<ModuleInfo> {
}
