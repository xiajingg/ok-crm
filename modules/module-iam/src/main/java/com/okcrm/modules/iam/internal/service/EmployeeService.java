package com.okcrm.modules.iam.internal.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.okcrm.modules.iam.api.EmployeeBrief;
import com.okcrm.modules.iam.api.EmployeeQueryService;
import com.okcrm.modules.iam.domain.Employee;
import com.okcrm.modules.iam.domain.EmployeePosition;
import com.okcrm.modules.iam.domain.Position;
import com.okcrm.modules.iam.infra.mapper.EmployeeMapper;
import com.okcrm.modules.iam.infra.mapper.EmployeePositionMapper;
import com.okcrm.modules.iam.infra.mapper.PositionMapper;
import com.okcrm.modules.iam.internal.dto.EmployeeCreateRequest;
import com.okcrm.modules.iam.internal.dto.EmployeeResponse;
import com.okcrm.modules.iam.internal.dto.EmployeeUpdateRequest;
import com.okcrm.platform.common.api.ErrorCode;
import com.okcrm.platform.common.api.PageResult;
import com.okcrm.platform.common.enums.EnableStatus;
import com.okcrm.platform.common.exception.BizException;
import com.okcrm.platform.security.principal.LoginUserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 员工服务。员工既是「人」也是「登录账号」，同时实现 {@link EmployeeQueryService}
 * 供其它模块（客户模块）查询。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmployeeService implements EmployeeQueryService {

    private final EmployeeMapper employeeMapper;
    private final EmployeePositionMapper employeePositionMapper;
    private final PositionMapper positionMapper;
    private final PermissionService permissionService;
    private final PasswordEncoder passwordEncoder;

    // ------------------------------------------------------------ SPI 实现

    @Override
    public Optional<EmployeeBrief> findById(Long employeeId) {
        if (employeeId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(employeeMapper.selectById(employeeId)).map(EmployeeService::toBrief);
    }

    @Override
    public List<EmployeeBrief> findByIds(Collection<Long> employeeIds) {
        if (employeeIds == null || employeeIds.isEmpty()) {
            return List.of();
        }
        return employeeMapper.selectList(Wrappers.<Employee>lambdaQuery()
                        .in(Employee::getId, employeeIds))
                .stream()
                .map(EmployeeService::toBrief)
                .toList();
    }

    @Override
    public boolean existsAndEnabled(Long employeeId) {
        return findById(employeeId).map(EmployeeBrief::enabled).orElse(false);
    }

    @Override
    public List<EmployeeBrief> listEnabled() {
        return employeeMapper.selectList(Wrappers.<Employee>lambdaQuery()
                        .eq(Employee::getStatus, EnableStatus.ENABLED)
                        .orderByAsc(Employee::getId))
                .stream()
                .map(EmployeeService::toBrief)
                .toList();
    }

    /**
     * 按账号查员工。登录流程使用，调用前必须已设置租户上下文。
     */
    public Optional<Employee> findByUsername(String username) {
        if (!StringUtils.hasText(username)) {
            return Optional.empty();
        }
        return Optional.ofNullable(employeeMapper.selectOne(
                Wrappers.<Employee>lambdaQuery().eq(Employee::getUsername, username)));
    }

    // ------------------------------------------------------------ 查询

    public PageResult<EmployeeResponse> page(String keyword, EnableStatus status, long pageNum, long pageSize) {
        var wrapper = Wrappers.<Employee>lambdaQuery();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(Employee::getUsername, keyword)
                    .or().like(Employee::getRealName, keyword)
                    .or().like(Employee::getPhone, keyword));
        }
        if (status != null) {
            wrapper.eq(Employee::getStatus, status);
        }
        wrapper.orderByDesc(Employee::getCreatedAt);

        Page<Employee> page = employeeMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        List<EmployeeResponse> records = toResponses(page.getRecords());
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    public EmployeeResponse detail(Long employeeId) {
        Employee employee = require(employeeId);
        return toResponses(List.of(employee)).get(0);
    }

    // ------------------------------------------------------------ 写入

    @Transactional(rollbackFor = Exception.class)
    public EmployeeResponse create(EmployeeCreateRequest request) {
        Long exists = employeeMapper.selectCount(Wrappers.<Employee>lambdaQuery()
                .eq(Employee::getUsername, request.username()));
        if (exists != null && exists > 0) {
            throw BizException.of(ErrorCode.EMPLOYEE_USERNAME_EXISTS);
        }

        Employee employee = new Employee();
        employee.setUsername(request.username());
        employee.setPassword(passwordEncoder.encode(request.password()));
        employee.setRealName(request.realName());
        employee.setPhone(request.phone());
        employee.setEmail(request.email());
        employee.setStatus(request.status() == null ? EnableStatus.ENABLED : request.status());
        employee.setRemark(request.remark());
        employeeMapper.insert(employee);

        replacePositions(employee.getId(), request.positionIds());
        log.info("新增员工: username={}, id={}", employee.getUsername(), employee.getId());
        return detail(employee.getId());
    }

    @Transactional(rollbackFor = Exception.class)
    public EmployeeResponse update(Long employeeId, EmployeeUpdateRequest request) {
        require(employeeId);

        Employee update = new Employee();
        update.setId(employeeId);
        update.setRealName(request.realName());
        update.setPhone(request.phone());
        update.setEmail(request.email());
        update.setStatus(request.status());
        update.setRemark(request.remark());
        if (StringUtils.hasText(request.password())) {
            update.setPassword(passwordEncoder.encode(request.password()));
        }
        employeeMapper.updateById(update);

        if (request.positionIds() != null) {
            replacePositions(employeeId, request.positionIds());
            permissionService.evictEmployee(employeeId);
        }
        return detail(employeeId);
    }

    /**
     * 删除员工（逻辑删除）。不允许删除自己，避免把自己锁在系统外面。
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long employeeId) {
        require(employeeId);
        if (employeeId.equals(LoginUserContext.employeeId())) {
            throw BizException.of(ErrorCode.BAD_REQUEST, "不能删除当前登录账号");
        }
        employeePositionMapper.delete(Wrappers.<EmployeePosition>lambdaQuery()
                .eq(EmployeePosition::getEmployeeId, employeeId));
        permissionService.evictEmployee(employeeId);
        employeeMapper.deleteById(employeeId);
        log.info("删除员工: id={}", employeeId);
    }

    /**
     * 重置密码。
     */
    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(Long employeeId, String rawPassword) {
        require(employeeId);
        if (!StringUtils.hasText(rawPassword) || rawPassword.length() < 6) {
            throw BizException.of(ErrorCode.BAD_REQUEST, "密码长度至少 6 位");
        }
        Employee update = new Employee();
        update.setId(employeeId);
        update.setPassword(passwordEncoder.encode(rawPassword));
        employeeMapper.updateById(update);
    }

    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(Long employeeId, boolean enabled) {
        require(employeeId);
        Employee update = new Employee();
        update.setId(employeeId);
        update.setStatus(enabled ? EnableStatus.ENABLED : EnableStatus.DISABLED);
        employeeMapper.updateById(update);
    }

    public Employee require(Long employeeId) {
        Employee employee = employeeMapper.selectById(employeeId);
        if (employee == null) {
            throw BizException.of(ErrorCode.EMPLOYEE_NOT_FOUND);
        }
        return employee;
    }

    // ------------------------------------------------------------ 内部

    /**
     * 全量替换员工的岗位关联。
     */
    private void replacePositions(Long employeeId, Collection<Long> positionIds) {
        employeePositionMapper.delete(Wrappers.<EmployeePosition>lambdaQuery()
                .eq(EmployeePosition::getEmployeeId, employeeId));

        if (positionIds == null || positionIds.isEmpty()) {
            return;
        }
        Set<Long> distinct = new LinkedHashSet<>(positionIds);
        // 校验岗位存在，避免挂到别的租户或已删除的岗位上
        List<Long> validIds = positionMapper.selectList(Wrappers.<Position>lambdaQuery()
                        .in(Position::getId, distinct))
                .stream()
                .map(Position::getId)
                .toList();
        if (validIds.size() != distinct.size()) {
            throw BizException.of(ErrorCode.POSITION_NOT_FOUND, "存在无效的岗位，请刷新后重试");
        }

        for (Long positionId : distinct) {
            EmployeePosition relation = new EmployeePosition();
            relation.setEmployeeId(employeeId);
            relation.setPositionId(positionId);
            employeePositionMapper.insert(relation);
        }
    }

    private List<EmployeeResponse> toResponses(List<Employee> employees) {
        if (employees.isEmpty()) {
            return List.of();
        }
        List<Long> employeeIds = employees.stream().map(Employee::getId).toList();

        Map<Long, List<Long>> positionIdsMap = employeePositionMapper.selectList(
                        Wrappers.<EmployeePosition>lambdaQuery()
                                .in(EmployeePosition::getEmployeeId, employeeIds))
                .stream()
                .collect(Collectors.groupingBy(EmployeePosition::getEmployeeId,
                        Collectors.mapping(EmployeePosition::getPositionId, Collectors.toList())));

        Set<Long> allPositionIds = positionIdsMap.values().stream()
                .flatMap(List::stream).collect(Collectors.toSet());
        Map<Long, String> positionNameMap = allPositionIds.isEmpty()
                ? Collections.emptyMap()
                : positionMapper.selectList(Wrappers.<Position>lambdaQuery()
                        .in(Position::getId, allPositionIds))
                .stream()
                .collect(Collectors.toMap(Position::getId, Position::getName, (a, b) -> a));

        return employees.stream()
                .map(employee -> {
                    List<Long> positionIds = positionIdsMap.getOrDefault(employee.getId(), List.of());
                    List<String> positionNames = positionIds.stream()
                            .map(positionNameMap::get)
                            .filter(Objects::nonNull)
                            .toList();
                    return toResponse(employee, positionIds, positionNames);
                })
                .toList();
    }

    private static EmployeeResponse toResponse(Employee employee, List<Long> positionIds, List<String> positionNames) {
        EnableStatus status = employee.getStatus();
        return new EmployeeResponse(
                employee.getId(),
                employee.getUsername(),
                employee.getRealName(),
                employee.getPhone(),
                employee.getEmail(),
                status == null ? null : status.getValue(),
                status == null ? null : status.getLabel(),
                employee.getRemark(),
                positionIds,
                positionNames,
                employee.getLastLoginAt(),
                employee.getCreatedAt()
        );
    }

    private static EmployeeBrief toBrief(Employee employee) {
        return new EmployeeBrief(
                employee.getId(),
                employee.getUsername(),
                employee.getRealName(),
                employee.getPhone(),
                employee.getStatus()
        );
    }

    /** 供登录流程更新最后登录时间 */
    @Transactional(rollbackFor = Exception.class)
    public void touchLastLogin(Long employeeId) {
        Employee update = new Employee();
        update.setId(employeeId);
        update.setLastLoginAt(LocalDateTime.now());
        employeeMapper.updateById(update);
    }
}
