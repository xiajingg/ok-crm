package com.okcrm.modules.iam.api;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 员工查询服务 —— 其它模块访问员工信息的唯一入口。
 *
 * <p>客户模块靠它校验「客户负责人是否合法」，不直接查 {@code sys_employee} 表。</p>
 */
public interface EmployeeQueryService {

    Optional<EmployeeBrief> findById(Long employeeId);

    List<EmployeeBrief> findByIds(Collection<Long> employeeIds);

    /**
     * 员工是否存在且在职。分配客户前必须校验。
     */
    boolean existsAndEnabled(Long employeeId);

    /**
     * 当前租户内全部在职员工（用于「分配给谁」的下拉框）。
     */
    List<EmployeeBrief> listEnabled();
}
