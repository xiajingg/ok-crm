package com.okcrm.modules.iam.domain;

import java.util.Set;

/**
 * 默认岗位的权限模板。
 *
 * <p>放在代码里而不是数据库里，是因为「销售该有哪些权限」属于产品定义，
 * 应该随版本一起演进、可被 code review，而不是靠运维手工配。</p>
 *
 * <p>新租户开通时由 {@code PositionService} 据此分配。</p>
 */
public final class DefaultPermissionTemplates {

    private DefaultPermissionTemplates() {
    }

    /**
     * 销售：只能碰自己的客户，公海里可以看和领，不能指派别人、不能改规则。
     */
    private static final Set<String> SALES_PERMISSIONS = Set.of(
            "customer:menu",
            "customer:list",
            "customer:create",
            "customer:update",
            "customer:followup",
            "pool:menu",
            "pool:logs",
            "pool:list",
            "pool:claim"
    );

    public static boolean forSales(String permissionCode) {
        return SALES_PERMISSIONS.contains(permissionCode);
    }

    /**
     * 销售主管：客户与公海池的全部权限 + 能看员工列表（用于指派）+ 租户设置。
     * 数据范围在岗位上是 ALL，因此能看到全部客户。
     */
    public static boolean forSalesManager(String permissionCode) {
        if (permissionCode == null) {
            return false;
        }
        if (permissionCode.startsWith("customer:") || permissionCode.startsWith("pool:")) {
            return true;
        }
        return "iam:menu".equals(permissionCode)
                || "iam:employee:list".equals(permissionCode)
                || "tenant:config".equals(permissionCode)
                || "tenant:config:update".equals(permissionCode);
    }
}
