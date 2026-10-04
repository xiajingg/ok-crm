package com.okcrm.platform.common.enums;

import com.baomidou.mybatisplus.annotation.IEnum;

/**
 * 通用启用状态。
 */
public enum EnableStatus implements IEnum<Integer> {

    DISABLED(0, "停用"),
    ENABLED(1, "启用");

    private final Integer value;
    private final String label;

    EnableStatus(Integer value, String label) {
        this.value = value;
        this.label = label;
    }

    @Override
    public Integer getValue() {
        return value;
    }

    public String getLabel() {
        return label;
    }
}
