package com.okcrm.modules.iam.internal.dto;

import java.util.List;

/**
 * 前端菜单节点（由后端按「已授权模块 + 岗位权限」动态下发）。
 *
 * <p>这是「按模块独立售卖」在前端的落地点：模块未授权时对应菜单直接不下发，
 * 前端不需要维护两套路由表。</p>
 */
public record MenuNode(
        String code,
        String name,
        /** 前端路由路径 */
        String path,
        /** 前端组件路径 */
        String component,
        String icon,
        /** 所属模块 */
        String moduleKey,
        Integer sortOrder,
        List<MenuNode> children
) {
}
