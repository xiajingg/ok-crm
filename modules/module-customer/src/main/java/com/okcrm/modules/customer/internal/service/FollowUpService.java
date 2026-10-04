package com.okcrm.modules.customer.internal.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.okcrm.modules.customer.domain.Customer;
import com.okcrm.modules.customer.domain.FollowUp;
import com.okcrm.modules.customer.infra.mapper.CustomerMapper;
import com.okcrm.modules.customer.infra.mapper.FollowUpMapper;
import com.okcrm.modules.customer.internal.dto.FollowUpCreateRequest;
import com.okcrm.modules.customer.internal.dto.FollowUpResponse;
import com.okcrm.modules.iam.api.EmployeeBrief;
import com.okcrm.modules.iam.api.EmployeeQueryService;
import com.okcrm.platform.common.api.ErrorCode;
import com.okcrm.platform.common.api.PageResult;
import com.okcrm.platform.common.exception.BizException;
import com.okcrm.platform.security.principal.LoginUserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 跟进记录服务。
 *
 * <p>关键副作用：写入跟进记录后同步刷新客户的 {@code last_follow_up_at}。
 * 公海池的「超期未跟进自动回收」完全依赖这个字段，
 * 如果只在跟进表里记录而不同步客户表，回收规则就会失真。</p>
 */
@Service
@RequiredArgsConstructor
public class FollowUpService {

    private final FollowUpMapper followUpMapper;
    private final CustomerMapper customerMapper;
    private final CustomerService customerService;
    private final EmployeeQueryService employeeQueryService;

    public PageResult<FollowUpResponse> pageByCustomer(Long customerId, long pageNum, long pageSize) {
        customerService.detail(customerId);

        Page<FollowUp> page = followUpMapper.selectPage(new Page<>(pageNum, pageSize),
                Wrappers.<FollowUp>lambdaQuery()
                        .eq(FollowUp::getCustomerId, customerId)
                        .orderByDesc(FollowUp::getFollowedAt));

        return PageResult.of(toResponses(page.getRecords()), page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Transactional(rollbackFor = Exception.class)
    public FollowUpResponse create(FollowUpCreateRequest request) {
        customerService.detail(request.customerId());

        LocalDateTime followedAt = request.followedAt() == null ? LocalDateTime.now() : request.followedAt();

        FollowUp followUp = new FollowUp();
        followUp.setCustomerId(request.customerId());
        followUp.setEmployeeId(LoginUserContext.employeeId());
        followUp.setType(request.type());
        followUp.setContent(request.content());
        followUp.setFollowedAt(followedAt);
        followUp.setNextFollowUpAt(request.nextFollowUpAt());
        followUpMapper.insert(followUp);

        // 同步刷新客户的最后跟进时间，供公海回收规则使用
        customerMapper.update(null, Wrappers.<Customer>lambdaUpdate()
                .eq(Customer::getId, request.customerId())
                .set(Customer::getLastFollowUpAt, followedAt));

        return toResponses(List.of(followUp)).get(0);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long followUpId) {
        FollowUp followUp = followUpMapper.selectById(followUpId);
        if (followUp == null) {
            throw BizException.of(ErrorCode.NOT_FOUND, "跟进记录不存在");
        }
        customerService.detail(followUp.getCustomerId());
        followUpMapper.deleteById(followUpId);
    }

    private List<FollowUpResponse> toResponses(List<FollowUp> followUps) {
        if (followUps.isEmpty()) {
            return List.of();
        }
        Set<Long> employeeIds = followUps.stream()
                .map(FollowUp::getEmployeeId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> nameMap = employeeIds.isEmpty()
                ? Collections.emptyMap()
                : employeeQueryService.findByIds(employeeIds).stream()
                .collect(Collectors.toMap(EmployeeBrief::id, EmployeeBrief::displayName, (a, b) -> a));

        return followUps.stream()
                .map(followUp -> new FollowUpResponse(
                        followUp.getId(),
                        followUp.getCustomerId(),
                        followUp.getEmployeeId(),
                        followUp.getEmployeeId() == null ? null : nameMap.get(followUp.getEmployeeId()),
                        followUp.getType(),
                        followUp.getContent(),
                        followUp.getFollowedAt(),
                        followUp.getNextFollowUpAt(),
                        followUp.getCreatedAt()))
                .toList();
    }
}
