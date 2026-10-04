package com.okcrm.modules.iam.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.okcrm.platform.common.entity.BaseEntity;
import com.okcrm.platform.common.enums.EnableStatus;
import lombok.Getter;
import lombok.Setter;

/**
 * 权限点（同时充当菜单节点）。
 *
 * <p>{@code sys_permission} 是<b>全局表</b>：权限点在代码/Flyway 里定义，
 * 所有租户共享同一套目录，租户之间差异体现在「哪些权限点被授予了岗位」。</p>
 *
 * <p>{@link #moduleKey} 是「按模块独立售卖」的关键字段：</p>
 * <ul>
 *   <li>前端菜单按 module_key 分组，未购买模块的菜单自动隐藏</li>
 *   <li>权限点粒度与模块对齐，避免「权限点爆炸」和「无法按模块切分」</li>
 * </ul>
 */
@Getter
@Setter
@TableName("sys_permission")
public class Permission extends BaseEntity {

    /** 权限码，如 customer:create。全局唯一 */
    private String code;

    /** 权限名称 */
    private String name;

    /** 所属模块，见 {@code ModuleKeys} */
    private String moduleKey;

    /** 节点类型：菜单 / 按钮 */
    private PermissionType type;

    /** 父节点权限码；根节点为 "0" */
    private String parentCode;

    /** 前端路由路径（仅菜单有） */
    private String path;

    /** 前端组件路径（仅菜单有） */
    private String component;

    /** 菜单图标 */
    private String icon;

    private Integer sortOrder;

    private EnableStatus status;
}
