package com.okcrm.modules.tenant.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.okcrm.platform.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 可售卖模块目录（全局表）。
 *
 * <p>这是「产品价目表」的代码化：每个 {@code moduleKey} 对应一个 Maven 业务模块，
 * 前端菜单与后端接口都以此为最小售卖单元。</p>
 *
 * <p>命名刻意避开 {@code Module}，以免与 {@code java.lang.Module} 混淆。</p>
 */
@Getter
@Setter
@TableName("sys_module")
public class ModuleInfo extends BaseEntity {

    /** 模块标识，与 Maven 模块一一对应 */
    private String moduleKey;

    /** 模块名称 */
    private String name;

    /** 模块说明（对外售卖文案） */
    private String description;

    /** 版本号 */
    private String version;

    /** 是否核心模块：核心模块不可从交付包中剥离 */
    private Boolean core;

    /** 排序 */
    private Integer sortOrder;
}
