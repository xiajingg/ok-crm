package com.okcrm.modules.iam.internal.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.okcrm.modules.iam.domain.DefaultPermissionTemplates;
import com.okcrm.modules.iam.domain.EmployeePosition;
import com.okcrm.modules.iam.domain.Permission;
import com.okcrm.modules.iam.domain.Position;
import com.okcrm.modules.iam.domain.PositionPermission;
import com.okcrm.modules.iam.infra.mapper.EmployeePositionMapper;
import com.okcrm.modules.iam.infra.mapper.PermissionMapper;
import com.okcrm.modules.iam.infra.mapper.PositionMapper;
import com.okcrm.modules.iam.infra.mapper.PositionPermissionMapper;
import com.okcrm.modules.iam.internal.dto.PositionCreateRequest;
import com.okcrm.modules.iam.internal.dto.PositionResponse;
import com.okcrm.modules.iam.internal.dto.PositionUpdateRequest;
import com.okcrm.platform.common.api.ErrorCode;
import com.okcrm.platform.common.enums.DataScopeType;
import com.okcrm.platform.common.enums.EnableStatus;
import com.okcrm.platform.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 岗位（即角色）服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PositionService {

    private final PositionMapper positionMapper;
    private final PositionPermissionMapper positionPermissionMapper;
    private final EmployeePositionMapper employeePositionMapper;
    private final PermissionMapper permissionMapper;
    private final PermissionService permissionService;

    public List<PositionResponse> list(String keyword) {
        var wrapper = Wrappers.<Position>lambdaQuery();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(Position::getName, keyword).or().like(Position::getCode, keyword));
        }
        wrapper.orderByAsc(Position::getSortOrder).orderByAsc(Position::getId);

        List<Position> positions = positionMapper.selectList(wrapper);
        if (positions.isEmpty()) {
            return List.of();
        }

        List<Long> positionIds = positions.stream().map(Position::getId).toList();
        Map<Long, Set<String>> permissionMap = positionPermissionMapper.selectList(
                        Wrappers.<PositionPermission>lambdaQuery()
                                .in(PositionPermission::getPositionId, positionIds))
                .stream()
                .collect(Collectors.groupingBy(PositionPermission::getPositionId,
                        Collectors.mapping(PositionPermission::getPermissionCode, Collectors.toSet())));

        return positions.stream()
                .map(position -> PositionResponse.from(position,
                        permissionMap.getOrDefault(position.getId(), Set.of())))
                .toList();
    }

    public PositionResponse detail(Long positionId) {
        Position position = require(positionId);
        return PositionResponse.from(position, permissionService.permissionsOfPosition(positionId));
    }

    @Transactional(rollbackFor = Exception.class)
    public PositionResponse create(PositionCreateRequest request) {
        Long exists = positionMapper.selectCount(Wrappers.<Position>lambdaQuery()
                .eq(Position::getCode, request.code()));
        if (exists != null && exists > 0) {
            throw BizException.of(ErrorCode.POSITION_CODE_EXISTS);
        }

        Position position = new Position();
        position.setCode(request.code());
        position.setName(request.name());
        position.setDataScope(request.dataScope());
        position.setStatus(request.status() == null ? EnableStatus.ENABLED : request.status());
        position.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        position.setRemark(request.remark());
        positionMapper.insert(position);

        replacePermissions(position.getId(), request.permissions());
        log.info("新增岗位: code={}, id={}", position.getCode(), position.getId());
        return detail(position.getId());
    }

    @Transactional(rollbackFor = Exception.class)
    public PositionResponse update(Long positionId, PositionUpdateRequest request) {
        require(positionId);

        Position update = new Position();
        update.setId(positionId);
        update.setName(request.name());
        update.setDataScope(request.dataScope());
        update.setStatus(request.status());
        update.setSortOrder(request.sortOrder());
        update.setRemark(request.remark());
        positionMapper.updateById(update);

        if (request.permissions() != null) {
            replacePermissions(positionId, request.permissions());
            // 权限变了，该岗位下所有员工的缓存必须失效
            permissionService.evictByPosition(positionId);
        }
        return detail(positionId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long positionId) {
        require(positionId);

        Long references = employeePositionMapper.selectCount(Wrappers.<EmployeePosition>lambdaQuery()
                .eq(EmployeePosition::getPositionId, positionId));
        if (references != null && references > 0) {
            throw BizException.of(ErrorCode.POSITION_IN_USE);
        }

        permissionService.evictByPosition(positionId);
        positionPermissionMapper.delete(Wrappers.<PositionPermission>lambdaQuery()
                .eq(PositionPermission::getPositionId, positionId));
        positionMapper.deleteById(positionId);
        log.info("删除岗位: id={}", positionId);
    }

    /**
     * 单独调整岗位权限（前端权限树保存按钮）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void assignPermissions(Long positionId, Collection<String> permissionCodes) {
        require(positionId);
        replacePermissions(positionId, permissionCodes);
        permissionService.evictByPosition(positionId);
    }

    public Position require(Long positionId) {
        Position position = positionMapper.selectById(positionId);
        if (position == null) {
            throw BizException.of(ErrorCode.POSITION_NOT_FOUND);
        }
        return position;
    }

    /**
     * 全量替换岗位的权限集合。
     */
    private void replacePermissions(Long positionId, Collection<String> permissionCodes) {
        positionPermissionMapper.delete(Wrappers.<PositionPermission>lambdaQuery()
                .eq(PositionPermission::getPositionId, positionId));

        if (permissionCodes == null || permissionCodes.isEmpty()) {
            return;
        }
        // 去重，避免前端重复提交造成唯一约束冲突
        for (String code : new LinkedHashSet<>(permissionCodes)) {
            PositionPermission relation = new PositionPermission();
            relation.setPositionId(positionId);
            relation.setPermissionCode(code);
            positionPermissionMapper.insert(relation);
        }
    }

    /**
     * 租户开通时批量创建默认岗位，并按模板分配权限。
     *
     * <p>权限必须在这里一并分配：否则新租户开出来虽然有三个岗位，
     * 但谁都点不动任何菜单（权限是空集），只能靠平台运维手工配，体验极差。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void createDefaultPositions(Long tenantId) {
        Long adminId = createDefault(tenantId, "admin", "管理员", DataScopeType.ALL, 0, "拥有全部权限");
        Long managerId = createDefault(tenantId, "sales_manager", "销售主管", DataScopeType.ALL, 1, "可查看全部客户，管理公海池");
        Long salesId = createDefault(tenantId, "sales", "销售", DataScopeType.SELF, 2, "仅可查看本人负责的客户");

        List<String> allCodes = permissionMapper.selectList(Wrappers.<Permission>lambdaQuery()
                        .eq(Permission::getStatus, EnableStatus.ENABLED))
                .stream()
                .map(Permission::getCode)
                .toList();

        insertPermissions(adminId, allCodes);
        insertPermissions(managerId, allCodes.stream()
                .filter(DefaultPermissionTemplates::forSalesManager).toList());
        insertPermissions(salesId, allCodes.stream()
                .filter(DefaultPermissionTemplates::forSales).toList());

        log.info("租户 {} 默认岗位初始化完成: 权限总数={}", tenantId, allCodes.size());
    }

    private Long createDefault(Long tenantId, String code, String name,
                               DataScopeType dataScope, int sortOrder, String remark) {
        Position position = new Position();
        position.setCode(code);
        position.setName(name);
        position.setDataScope(dataScope);
        position.setStatus(EnableStatus.ENABLED);
        position.setSortOrder(sortOrder);
        position.setRemark(remark);
        // 显式指定租户：事件监听发生在租户上下文之外
        position.setTenantId(tenantId);
        positionMapper.insert(position);
        return position.getId();
    }

    private void insertPermissions(Long positionId, Collection<String> permissionCodes) {
        for (String code : new LinkedHashSet<>(permissionCodes)) {
            PositionPermission relation = new PositionPermission();
            relation.setPositionId(positionId);
            relation.setPermissionCode(code);
            positionPermissionMapper.insert(relation);
        }
    }
}
