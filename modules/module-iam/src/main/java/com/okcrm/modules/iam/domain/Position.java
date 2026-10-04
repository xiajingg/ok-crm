package com.okcrm.modules.iam.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.okcrm.platform.common.entity.TenantEntity;
import com.okcrm.platform.common.enums.DataScopeType;
import com.okcrm.platform.common.enums.EnableStatus;
import lombok.Getter;
import lombok.Setter;

/**
 * 岗位 —— <b>岗位即角色</b>。
 *
 * <p>系统里只有「岗位」一个概念，它同时承担两个职责：</p>
 * <ol>
 *   <li>组织语义：员工在企业里的职务（销售、销售主管、客服）</li>
 *   <li>权限语义：该职务对应的权限集合与数据范围</li>
 * </ol>
 *
 * <p><b>为什么这样设计仍然「不后悔」</b>：权限挂在
 * {@link PositionPermission} 中间表上，岗位与权限是多对多。
 * 将来如果真出现「同岗位但权限不同」的诉求，只需要新增 {@code sys_role} 表、
 * 把 {@code sys_position_permission} 换成 {@code sys_position_role}，
 * 业务表（员工、客户）一行都不用改。</p>
 */
@Getter
@Setter
@TableName("sys_position")
public class Position extends TenantEntity {

    /** 岗位编码，租户内唯一 */
    private String code;

    /** 岗位名称 */
    private String name;

    /**
     * 数据权限范围。员工兼任多个岗位时取最宽的一档。
     *
     * @see DataScopeType
     */
    private DataScopeType dataScope;

    private EnableStatus status;

    private Integer sortOrder;

    private String remark;
}
