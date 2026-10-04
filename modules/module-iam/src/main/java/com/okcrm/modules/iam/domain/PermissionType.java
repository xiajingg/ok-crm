package com.okcrm.modules.iam.domain;

import com.baomidou.mybatisplus.annotation.IEnum;

/**
 * 权限节点类型。
 */
public enum PermissionType implements IEnum<String> {

    /** 菜单：参与前端路由与侧边栏渲染 */
    MENU("MENU", "菜单"),

    /** 按钮：只参与接口鉴权，不渲染 */
    BUTTON("BUTTON", "按钮");

    private final String value;
    private final String label;

    PermissionType(String value, String label) {
        this.value = value;
        this.label = label;
    }

    @Override
    public String getValue() {
        return value;
    }

    public String getLabel() {
        return label;
    }
}
