package com.okcrm.modules.iam.internal.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.okcrm.modules.iam.domain.Permission;
import com.okcrm.modules.iam.domain.PermissionType;
import com.okcrm.modules.iam.infra.mapper.PermissionMapper;
import com.okcrm.modules.iam.internal.dto.MenuNode;
import com.okcrm.platform.common.constant.CommonConstants;
import com.okcrm.platform.common.enums.EnableStatus;
import com.okcrm.platform.security.spi.ModuleLicenseProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 动态菜单服务。
 *
 * <p>菜单 = 权限点目录 ∩ 岗位已授权限 ∩ 租户已购模块。三道过滤缺一不可：</p>
 * <ul>
 *   <li>少了「岗位权限」→ 销售能看到管理员菜单</li>
 *   <li>少了「已购模块」→ 没买公海池的客户也能看到公海菜单，点进去报错</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class MenuService {

    private final PermissionMapper permissionMapper;
    private final ModuleLicenseProvider moduleLicenseProvider;

    /**
     * 构建当前用户可见的菜单树。
     *
     * @param tenantId            租户 ID；平台超管可为 null
     * @param platformAdmin       平台超管可见全部菜单
     * @param grantedPermissions  该员工拥有的权限码
     */
    public List<MenuNode> menuOf(Long tenantId, boolean platformAdmin, Set<String> grantedPermissions) {
        List<Permission> menus = permissionMapper.selectList(Wrappers.<Permission>lambdaQuery()
                .eq(Permission::getType, PermissionType.MENU)
                .eq(Permission::getStatus, EnableStatus.ENABLED)
                .orderByAsc(Permission::getSortOrder)
                .orderByAsc(Permission::getId));

        Set<String> licensedModules = platformAdmin ? Set.of() : moduleLicenseProvider.licensedModules(tenantId);

        Map<String, List<Permission>> byParent = menus.stream()
                .collect(Collectors.groupingBy(permission ->
                        permission.getParentCode() == null ? CommonConstants.PERMISSION_ROOT : permission.getParentCode()));

        return buildChildren(CommonConstants.PERMISSION_ROOT, byParent, platformAdmin, grantedPermissions, licensedModules);
    }

    private List<MenuNode> buildChildren(String parentCode,
                                         Map<String, List<Permission>> byParent,
                                         boolean platformAdmin,
                                         Set<String> grantedPermissions,
                                         Set<String> licensedModules) {
        List<Permission> children = byParent.get(parentCode);
        if (children == null || children.isEmpty()) {
            return List.of();
        }

        List<MenuNode> nodes = new ArrayList<>();
        for (Permission permission : children) {
            // 第一道闸门：模块未授权，整棵子树直接剪掉
            if (!platformAdmin && !licensedModules.contains(permission.getModuleKey())) {
                continue;
            }

            List<MenuNode> subNodes = buildChildren(permission.getCode(), byParent, platformAdmin,
                    grantedPermissions, licensedModules);

            boolean selfGranted = platformAdmin || grantedPermissions.contains(permission.getCode());
            // 第二道闸门：自身无权限且没有可见子节点 → 剪掉
            if (!selfGranted && subNodes.isEmpty()) {
                continue;
            }

            nodes.add(new MenuNode(
                    permission.getCode(),
                    permission.getName(),
                    permission.getPath(),
                    permission.getComponent(),
                    permission.getIcon(),
                    permission.getModuleKey(),
                    permission.getSortOrder(),
                    subNodes
            ));
        }
        return nodes;
    }
}
