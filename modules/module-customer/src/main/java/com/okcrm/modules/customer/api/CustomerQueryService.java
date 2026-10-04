package com.okcrm.modules.customer.api;

import com.okcrm.platform.common.api.PageResult;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 客户查询服务 —— 公海池模块访问客户数据的唯一入口。
 *
 * <p>注意：公海相关查询<b>不做数据权限过滤</b>。公海客户没有归属人，
 * 若按「只能看自己的」过滤，公海池会变成所有人都看不见的空列表。</p>
 */
public interface CustomerQueryService {

    Optional<CustomerBrief> findById(Long customerId);

    List<CustomerBrief> findByIds(Collection<Long> customerIds);

    boolean exists(Long customerId);

    /**
     * 某员工当前负责的客户数量。公海池用来判断是否超出「单人持有上限」。
     */
    long countByOwner(Long employeeId);

    /**
     * 公海池客户分页（{@code owner_id IS NULL}）。
     */
    PageResult<CustomerBrief> pagePoolCustomers(String keyword, long pageNum, long pageSize);

    /**
     * 超期未跟进、可被回收的客户。
     *
     * <p>由调用方显式传入租户与口径，不依赖调用方的线程上下文 ——
     * 定时任务最容易在这里出问题（线程池里上下文丢失会导致跨租户误回收）。</p>
     *
     * @param tenantId 目标租户
     * @param basis    时间口径
     * @param deadline 截止时间：口径时间早于该值的客户视为超期
     */
    List<CustomerBrief> findRecycleCandidates(Long tenantId, RecycleBasis basis, LocalDateTime deadline);
}
