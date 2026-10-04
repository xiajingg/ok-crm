package com.okcrm.modules.iam.internal.dto;

import com.okcrm.modules.iam.domain.Permission;

/**
 * 权限点（前端权限树/菜单配置用）。
 */
public record PermissionResponse(
        String code,
        String name,
        String moduleKey,
        String type,
        String parentCode,
        String path,
        String component,
        String icon,
        Integer sortOrder
) {

    public static PermissionResponse from(Permission permission) {
        return new PermissionResponse(
                permission.getCode(),
                permission.getName(),
                permission.getModuleKey(),
                permission.getType() == null ? null : permission.getType().getValue(),
                permission.getParentCode(),
                permission.getPath(),
                permission.getComponent(),
                permission.getIcon(),
                permission.getSortOrder()
        );
    }
}
