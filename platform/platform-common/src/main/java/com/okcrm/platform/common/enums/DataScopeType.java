package com.okcrm.platform.common.enums;

import com.baomidou.mybatisplus.annotation.IEnum;

/**
 * 数据权限范围。挂在岗位上，决定该岗位员工能看到哪些客户。
 *
 * <p>MVP 只实现 ALL / SELF 两档：
 * <ul>
 *   <li>{@link #ALL} 全部数据（通常是主管、管理员）</li>
 *   <li>{@link #SELF} 仅本人负责的数据；公海（无归属）客户不受此限制，否则公海池将无人可见</li>
 * </ul>
 * CUSTOM（指定员工集合）已预留枚举位，属 P1。</p>
 */
public enum DataScopeType implements IEnum<String> {

    ALL("ALL", "全部数据"),
    SELF("SELF", "仅本人数据");

    private final String value;
    private final String label;

    DataScopeType(String value, String label) {
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
