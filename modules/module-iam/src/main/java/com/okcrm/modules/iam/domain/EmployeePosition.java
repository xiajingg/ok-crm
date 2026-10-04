package com.okcrm.modules.iam.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.okcrm.platform.common.entity.TenantEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 员工 ↔ 岗位（多对多，支持一人兼任多岗）。
 *
 * <p>权限计算规则：所有岗位权限的<b>并集</b>；数据范围取所有岗位中<b>最宽</b>的一档。</p>
 */
@Getter
@Setter
@TableName("sys_emp_position")
public class EmployeePosition extends TenantEntity {

    private Long employeeId;

    private Long positionId;
}
