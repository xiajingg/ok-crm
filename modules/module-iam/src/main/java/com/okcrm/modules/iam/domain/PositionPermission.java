package com.okcrm.modules.iam.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.okcrm.platform.common.entity.TenantEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 岗位 → 权限点。
 *
 * <p>用 {@code permission_code} 而不是 {@code permission_id} 关联：
 * 权限点是全局表且以 code 为自然键，存 code 可以少一次 join，
 * 前端拿到权限码后也能直接和按钮上的 v-permission 指令比对。</p>
 *
 * <p>这一层中间表正是「岗位即角色」未来可平滑拆分的关键 ——
 * 想拆出独立角色时，把本表换成 {@code sys_position_role} 即可，业务表不受影响。</p>
 */
@Getter
@Setter
@TableName("sys_position_permission")
public class PositionPermission extends TenantEntity {

    private Long positionId;

    private String permissionCode;
}
