package com.okcrm.modules.customer.internal.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.okcrm.modules.customer.api.CustomerBrief;
import com.okcrm.modules.customer.api.CustomerOwnershipService;
import com.okcrm.modules.customer.api.CustomerQueryService;
import com.okcrm.modules.customer.api.OwnershipAction;
import com.okcrm.modules.customer.api.RecycleBasis;
import com.okcrm.modules.customer.api.event.CustomerOwnershipChangedEvent;
import com.okcrm.modules.customer.domain.Contact;
import com.okcrm.modules.customer.domain.Customer;
import com.okcrm.modules.customer.infra.mapper.ContactMapper;
import com.okcrm.modules.customer.infra.mapper.CustomerMapper;
import com.okcrm.modules.customer.internal.dto.CustomerCreateRequest;
import com.okcrm.modules.customer.internal.dto.CustomerResponse;
import com.okcrm.modules.customer.internal.dto.CustomerUpdateRequest;
import com.okcrm.modules.iam.api.EmployeeBrief;
import com.okcrm.modules.iam.api.EmployeeQueryService;
import com.okcrm.platform.common.api.ErrorCode;
import com.okcrm.platform.common.api.PageResult;
import com.okcrm.platform.common.exception.BizException;
import com.okcrm.platform.security.checker.DataScopeChecker;
import com.okcrm.platform.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 客户服务。同时实现 {@link CustomerQueryService} 与 {@link CustomerOwnershipService} 两个 SPI，
 * 是「客户数据」的唯一读写入口。
 *
 * <p>本类里有两处必须特别小心的边界：</p>
 * <ol>
 *   <li><b>公海客户必须绕过数据权限</b>：公海客户没有归属人，
 *       若按「只能看自己的」过滤，公海池会变成空列表</li>
 *   <li><b>归属变更必须发事件</b>：否则公海池流转日志会漏记
 *       从客户模块自身接口发起的转移</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerService implements CustomerQueryService, CustomerOwnershipService {

    private final CustomerMapper customerMapper;
    private final ContactMapper contactMapper;
    private final EmployeeQueryService employeeQueryService;
    private final DataScopeChecker dataScopeChecker;
    private final ApplicationEventPublisher eventPublisher;

    // ============================================================ 常规查询

    /**
     * 客户分页。{@code poolOnly=true} 时只查公海客户，且不做数据权限过滤。
     */
    public PageResult<CustomerResponse> page(String keyword, String level, Long ownerId,
                                             boolean poolOnly, long pageNum, long pageSize) {
        var wrapper = Wrappers.<Customer>lambdaQuery();

        if (poolOnly) {
            wrapper.isNull(Customer::getOwnerId);
        } else {
            if (ownerId != null) {
                wrapper.eq(Customer::getOwnerId, ownerId);
            } else {
                applyDataScope(wrapper);
            }
        }
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(Customer::getName, keyword)
                    .or().like(Customer::getPhone, keyword)
                    .or().like(Customer::getIndustry, keyword));
        }
        if (StringUtils.hasText(level)) {
            wrapper.eq(Customer::getLevel, level);
        }
        wrapper.orderByDesc(Customer::getCreatedAt);

        Page<Customer> page = customerMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        return PageResult.of(toResponses(page.getRecords()), page.getTotal(), page.getCurrent(), page.getSize());
    }

    public CustomerResponse detail(Long customerId) {
        Customer customer = require(customerId);
        checkVisible(customer);
        return toResponses(List.of(customer)).get(0);
    }

    /**
     * 客户的全部联系人。
     */
    public List<Contact> contactsOf(Long customerId) {
        require(customerId);
        return contactMapper.selectList(Wrappers.<Contact>lambdaQuery()
                .eq(Contact::getCustomerId, customerId)
                .orderByDesc(Contact::getPrimaryContact)
                .orderByAsc(Contact::getId));
    }

    // ============================================================ 写入

    @Transactional(rollbackFor = Exception.class)
    public CustomerResponse create(CustomerCreateRequest request) {
        Customer customer = new Customer();
        customer.setName(request.name());
        customer.setIndustry(request.industry());
        customer.setLevel(request.level());
        customer.setSource(request.source());
        customer.setPhone(request.phone());
        customer.setAddress(request.address());
        customer.setTags(request.tags());
        customer.setRemark(request.remark());

        if (request.ownerId() != null) {
            requireEnabledEmployee(request.ownerId());
            customer.setOwnerId(request.ownerId());
            customer.setOwnerAssignedAt(LocalDateTime.now());
        } else {
            // 未指定负责人 → 直接进公海池
            customer.setEnterPoolAt(LocalDateTime.now());
            customer.setPoolReason("录入时未指定负责人");
        }

        customerMapper.insert(customer);
        log.info("新增客户: id={}, name={}, ownerId={}", customer.getId(), customer.getName(), customer.getOwnerId());
        return detail(customer.getId());
    }

    @Transactional(rollbackFor = Exception.class)
    public CustomerResponse update(Long customerId, CustomerUpdateRequest request) {
        Customer customer = require(customerId);
        checkVisible(customer);

        Customer update = new Customer();
        update.setId(customerId);
        update.setName(request.name());
        update.setIndustry(request.industry());
        update.setLevel(request.level());
        update.setSource(request.source());
        update.setPhone(request.phone());
        update.setAddress(request.address());
        update.setTags(request.tags());
        update.setRemark(request.remark());
        customerMapper.updateById(update);

        return detail(customerId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long customerId) {
        Customer customer = require(customerId);
        checkVisible(customer);
        contactMapper.delete(Wrappers.<Contact>lambdaQuery().eq(Contact::getCustomerId, customerId));
        customerMapper.deleteById(customerId);
        log.info("删除客户: id={}", customerId);
    }

    /**
     * 主管把客户指派给某人 / 员工之间转移。
     */
    @Transactional(rollbackFor = Exception.class)
    public void transfer(Long customerId, Long newOwnerId, String remark) {
        Customer customer = require(customerId);
        checkVisible(customer);
        OwnershipAction action = customer.getOwnerId() == null
                ? OwnershipAction.ASSIGN
                : OwnershipAction.TRANSFER;
        doAssign(customer, newOwnerId, action, remark);
    }

    /**
     * 批量指派。逐个处理，单个失败不影响其它（用 REQUIRES_NEW 太重，这里选择收集失败清单）。
     */
    @Transactional(rollbackFor = Exception.class)
    public int batchAssign(List<Long> customerIds, Long ownerId, String remark) {
        requireEnabledEmployee(ownerId);
        int success = 0;
        for (Long customerId : customerIds) {
            Customer customer = customerMapper.selectById(customerId);
            if (customer == null) {
                continue;
            }
            doAssign(customer, ownerId, customer.getOwnerId() == null
                    ? OwnershipAction.ASSIGN : OwnershipAction.TRANSFER, remark);
            success++;
        }
        return success;
    }

    /**
     * 退回公海池（手动）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void releaseToPoolByUser(Long customerId, String reason) {
        Customer customer = require(customerId);
        checkVisible(customer);
        if (customer.getOwnerId() == null) {
            throw BizException.of(ErrorCode.CUSTOMER_NOT_IN_POOL, "该客户已经在公海池中");
        }
        doRelease(customer, OwnershipAction.RELEASE, reason);
    }

    // ============================================================ SPI: CustomerQueryService

    @Override
    public Optional<CustomerBrief> findById(Long customerId) {
        return Optional.ofNullable(customerMapper.selectById(customerId)).map(CustomerBrief::from);
    }

    @Override
    public List<CustomerBrief> findByIds(Collection<Long> customerIds) {
        if (customerIds == null || customerIds.isEmpty()) {
            return List.of();
        }
        return customerMapper.selectList(Wrappers.<Customer>lambdaQuery()
                        .in(Customer::getId, customerIds))
                .stream()
                .map(CustomerBrief::from)
                .toList();
    }

    @Override
    public boolean exists(Long customerId) {
        return customerId != null && customerMapper.selectById(customerId) != null;
    }

    @Override
    public long countByOwner(Long employeeId) {
        if (employeeId == null) {
            return 0L;
        }
        Long count = customerMapper.selectCount(Wrappers.<Customer>lambdaQuery()
                .eq(Customer::getOwnerId, employeeId));
        return count == null ? 0L : count;
    }

    @Override
    public PageResult<CustomerBrief> pagePoolCustomers(String keyword, long pageNum, long pageSize) {
        var wrapper = Wrappers.<Customer>lambdaQuery().isNull(Customer::getOwnerId);
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(Customer::getName, keyword)
                    .or().like(Customer::getPhone, keyword));
        }
        wrapper.orderByAsc(Customer::getEnterPoolAt);

        Page<Customer> page = customerMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        return PageResult.of(page.getRecords().stream().map(CustomerBrief::from).toList(),
                page.getTotal(), page.getCurrent(), page.getSize());
    }

    /**
     * 超期未跟进的客户。
     *
     * <p>显式用 {@code callAs} 绑定租户：定时任务在遍历租户时调用本方法，
     * 绝不能依赖调用方的上下文。</p>
     */
    @Override
    public List<CustomerBrief> findRecycleCandidates(Long tenantId, RecycleBasis basis, LocalDateTime deadline) {
        if (tenantId == null || deadline == null) {
            return List.of();
        }
        return TenantContext.callAs(tenantId, () -> {
            var wrapper = Wrappers.<Customer>lambdaQuery().isNotNull(Customer::getOwnerId);

            if (basis == RecycleBasis.CREATE_TIME) {
                wrapper.le(Customer::getCreatedAt, deadline);
            } else {
                // LAST_FOLLOWUP：从未跟进的按创建时间兜底，否则永远回收不掉
                wrapper.and(w -> w.le(Customer::getLastFollowUpAt, deadline)
                        .or(inner -> inner.isNull(Customer::getLastFollowUpAt)
                                .le(Customer::getCreatedAt, deadline)));
            }
            return customerMapper.selectList(wrapper).stream().map(CustomerBrief::from).toList();
        });
    }

    // ============================================================ SPI: CustomerOwnershipService

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignOwner(Long customerId, Long employeeId, OwnershipAction action, String reason) {
        Customer customer = require(customerId);
        // 领取场景必须先确认没被别人抢先领走
        if (action == OwnershipAction.CLAIM && customer.getOwnerId() != null) {
            throw BizException.of(ErrorCode.CUSTOMER_ALREADY_OWNED);
        }
        doAssign(customer, employeeId, action, reason);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void releaseToPool(Long customerId, OwnershipAction action, String reason) {
        Customer customer = require(customerId);
        if (customer.getOwnerId() == null) {
            // 已在公海，幂等返回，避免定时任务重复执行时报错
            log.debug("客户 {} 已在公海池中，跳过回收", customerId);
            return;
        }
        doRelease(customer, action, reason);
    }

    // ============================================================ 内部

    private void doAssign(Customer customer, Long newOwnerId, OwnershipAction action, String reason) {
        requireEnabledEmployee(newOwnerId);

        Long fromOwnerId = customer.getOwnerId();
        LocalDateTime now = LocalDateTime.now();

        Customer update = new Customer();
        update.setId(customer.getId());
        update.setOwnerId(newOwnerId);
        update.setOwnerAssignedAt(now);
        // 离开公海，清掉公海相关字段
        update.setEnterPoolAt(null);
        update.setPoolReason("");
        customerMapper.updateById(update);

        eventPublisher.publishEvent(new CustomerOwnershipChangedEvent(
                customer.getId(), customer.getName(), fromOwnerId, newOwnerId, action, reason));

        log.info("客户归属变更: customerId={}, {} -> {}, action={}",
                customer.getId(), fromOwnerId, newOwnerId, action);
    }

    private void doRelease(Customer customer, OwnershipAction action, String reason) {
        Long fromOwnerId = customer.getOwnerId();

        Customer update = new Customer();
        update.setId(customer.getId());
        update.setEnterPoolAt(LocalDateTime.now());
        update.setPoolReason(reason);
        // MyBatis-Plus 默认忽略 null 字段，要显式把 owner_id 置空必须用 UpdateWrapper
        customerMapper.update(null, Wrappers.<Customer>lambdaUpdate()
                .eq(Customer::getId, customer.getId())
                .set(Customer::getOwnerId, null)
                .set(Customer::getOwnerAssignedAt, null)
                .set(Customer::getEnterPoolAt, update.getEnterPoolAt())
                .set(Customer::getPoolReason, reason));

        eventPublisher.publishEvent(new CustomerOwnershipChangedEvent(
                customer.getId(), customer.getName(), fromOwnerId, null, action, reason));

        log.info("客户退回公海: customerId={}, from={}, action={}, reason={}",
                customer.getId(), fromOwnerId, action, reason);
    }

    /**
     * 数据权限过滤：非「全部数据」范围时，只能看到自己负责的客户。
     */
    private void applyDataScope(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Customer> wrapper) {
        if (dataScopeChecker.all()) {
            return;
        }
        Long me = dataScopeChecker.currentEmployeeId();
        if (me == null) {
            // 拿不到当前用户（异常情况）：返回空集而不是全量，宁可少看不能多看
            wrapper.eq(Customer::getId, -1L);
            return;
        }
        wrapper.eq(Customer::getOwnerId, me);
    }

    /**
     * 可见性校验：公海客户对所有人可见（否则没人能领取），
     * 有归属的客户只有负责人本人和「全部数据」范围的人可见。
     */
    private void checkVisible(Customer customer) {
        if (dataScopeChecker.all()) {
            return;
        }
        if (customer.getOwnerId() == null) {
            return;
        }
        if (Objects.equals(customer.getOwnerId(), dataScopeChecker.currentEmployeeId())) {
            return;
        }
        throw BizException.of(ErrorCode.PERMISSION_DENIED, "该客户不属于你，无法操作");
    }

    private void requireEnabledEmployee(Long employeeId) {
        if (!employeeQueryService.existsAndEnabled(employeeId)) {
            throw BizException.of(ErrorCode.EMPLOYEE_NOT_FOUND, "负责人不存在或已停用");
        }
    }

    private Customer require(Long customerId) {
        Customer customer = customerId == null ? null : customerMapper.selectById(customerId);
        if (customer == null) {
            throw BizException.of(ErrorCode.CUSTOMER_NOT_FOUND);
        }
        return customer;
    }

    private List<CustomerResponse> toResponses(List<Customer> customers) {
        if (customers.isEmpty()) {
            return List.of();
        }

        Set<Long> ownerIds = customers.stream()
                .map(Customer::getOwnerId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> ownerNameMap = ownerIds.isEmpty()
                ? Collections.emptyMap()
                : employeeQueryService.findByIds(ownerIds).stream()
                .collect(Collectors.toMap(EmployeeBrief::id, EmployeeBrief::displayName, (a, b) -> a));

        List<Long> customerIds = customers.stream().map(Customer::getId).toList();
        Map<Long, Long> contactCountMap = contactMapper.selectList(Wrappers.<Contact>lambdaQuery()
                        .in(Contact::getCustomerId, customerIds))
                .stream()
                .collect(Collectors.groupingBy(Contact::getCustomerId, Collectors.counting()));

        return customers.stream()
                .map(customer -> new CustomerResponse(
                        customer.getId(),
                        customer.getName(),
                        customer.getIndustry(),
                        customer.getLevel(),
                        customer.getSource(),
                        customer.getPhone(),
                        customer.getAddress(),
                        customer.getOwnerId(),
                        customer.getOwnerId() == null ? null : ownerNameMap.get(customer.getOwnerId()),
                        customer.getOwnerId() == null,
                        customer.getOwnerAssignedAt(),
                        customer.getLastFollowUpAt(),
                        customer.getEnterPoolAt(),
                        customer.getPoolReason(),
                        customer.getTags(),
                        customer.getRemark(),
                        contactCountMap.getOrDefault(customer.getId(), 0L),
                        customer.getCreatedAt()))
                .toList();
    }
}
