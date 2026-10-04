package com.okcrm.modules.iam.internal.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.okcrm.modules.iam.domain.EmployeePosition;
import com.okcrm.modules.iam.domain.Position;
import com.okcrm.modules.iam.domain.PositionPermission;
import com.okcrm.modules.iam.infra.mapper.EmployeePositionMapper;
import com.okcrm.modules.iam.infra.mapper.PositionMapper;
import com.okcrm.modules.iam.infra.mapper.PositionPermissionMapper;
import com.okcrm.platform.common.enums.DataScopeType;
import com.okcrm.platform.common.enums.EnableStatus;
import com.okcrm.platform.security.spi.PermissionProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 权限计算服务 —— 「岗位即角色」模型的执行者。
 *
 * <p>计算规则：</p>
 * <ul>
 *   <li><b>权限</b> = 员工所有岗位权限点的并集</li>
 *   <li><b>数据范围</b> = 员工所有岗位中最宽的一档（任一岗位为「全部」即为「全部」）</li>
 * </ul>
 *
 * <p>结果进缓存。缓存失效时机：</p>
 * <ul>
 *   <li>改了岗位的权限配置 → 失效该岗位下所有员工</li>
 *   <li>改了员工的岗位 → 失效该员工</li>
 *   <li>删了岗位 → 失效该岗位下所有员工</li>
 * </ul>
 * <p>失效用 {@link CacheManager} 直接操作而不是 {@code @CacheEvict}：
 * 因为「按岗位批量失效」需要在方法内部遍历员工逐个清除，自调用走不到代理。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionService implements PermissionProvider {

    public static final String CACHE_PERMISSIONS = "employeePermissions";
    public static final String CACHE_DATA_SCOPE = "employeeDataScope";

    private final EmployeePositionMapper employeePositionMapper;
    private final PositionMapper positionMapper;
    private final PositionPermissionMapper positionPermissionMapper;
    private final CacheManager cacheManager;

    // ------------------------------------------------------------ SPI 实现

    @Override
    @Cacheable(cacheNames = CACHE_PERMISSIONS, key = "#employeeId", condition = "#employeeId != null")
    public Set<String> permissionsOf(Long employeeId) {
        List<Long> positionIds = enabledPositionIdsOf(employeeId);
        if (positionIds.isEmpty()) {
            return Set.of();
        }
        return positionPermissionMapper.selectList(Wrappers.<PositionPermission>lambdaQuery()
                        .in(PositionPermission::getPositionId, positionIds))
                .stream()
                .map(PositionPermission::getPermissionCode)
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    @Cacheable(cacheNames = CACHE_DATA_SCOPE, key = "#employeeId", condition = "#employeeId != null")
    public String dataScopeOf(Long employeeId) {
        List<Long> positionIds = enabledPositionIdsOf(employeeId);
        if (positionIds.isEmpty()) {
            // 没挂岗位的人只能看自己的数据，这是最保守的默认
            return DataScopeType.SELF.getValue();
        }
        List<Position> positions = positionMapper.selectList(Wrappers.<Position>lambdaQuery()
                .in(Position::getId, positionIds)
                .eq(Position::getStatus, EnableStatus.ENABLED));

        boolean hasAll = positions.stream().anyMatch(p -> p.getDataScope() == DataScopeType.ALL);
        return (hasAll ? DataScopeType.ALL : DataScopeType.SELF).getValue();
    }

    // ------------------------------------------------------------ 查询辅助

    /**
     * 员工身上「启用状态」的岗位 ID 列表。停用的岗位不参与权限计算。
     */
    public List<Long> enabledPositionIdsOf(Long employeeId) {
        if (employeeId == null) {
            return List.of();
        }
        List<Long> positionIds = employeePositionMapper.selectList(
                        Wrappers.<EmployeePosition>lambdaQuery()
                                .eq(EmployeePosition::getEmployeeId, employeeId))
                .stream()
                .map(EmployeePosition::getPositionId)
                .toList();

        if (positionIds.isEmpty()) {
            return List.of();
        }
        return positionMapper.selectList(Wrappers.<Position>lambdaQuery()
                        .in(Position::getId, positionIds)
                        .eq(Position::getStatus, EnableStatus.ENABLED))
                .stream()
                .map(Position::getId)
                .toList();
    }

    /**
     * 岗位已配置的权限码。
     */
    public Set<String> permissionsOfPosition(Long positionId) {
        return positionPermissionMapper.selectList(Wrappers.<PositionPermission>lambdaQuery()
                        .eq(PositionPermission::getPositionId, positionId))
                .stream()
                .map(PositionPermission::getPermissionCode)
                .collect(Collectors.toSet());
    }

    // ------------------------------------------------------------ 缓存失效

    public void evictEmployee(Long employeeId) {
        if (employeeId == null) {
            return;
        }
        evictKey(CACHE_PERMISSIONS, employeeId);
        evictKey(CACHE_DATA_SCOPE, employeeId);
    }

    /**
     * 岗位的权限配置变化时，失效该岗位下所有员工的缓存。
     */
    public void evictByPosition(Long positionId) {
        if (positionId == null) {
            return;
        }
        employeePositionMapper.selectList(Wrappers.<EmployeePosition>lambdaQuery()
                        .eq(EmployeePosition::getPositionId, positionId))
                .stream()
                .map(EmployeePosition::getEmployeeId)
                .distinct()
                .forEach(this::evictEmployee);
        log.debug("已失效岗位 {} 下所有员工的权限缓存", positionId);
    }

    private void evictKey(String cacheName, Object key) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.evict(key);
        }
    }
}
