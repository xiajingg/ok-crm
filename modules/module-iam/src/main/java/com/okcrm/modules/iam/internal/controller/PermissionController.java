package com.okcrm.modules.iam.internal.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.okcrm.modules.iam.domain.Permission;
import com.okcrm.modules.iam.infra.mapper.PermissionMapper;
import com.okcrm.modules.iam.internal.dto.PermissionResponse;
import com.okcrm.platform.common.api.Result;
import com.okcrm.platform.common.enums.EnableStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 权限点目录。
 *
 * <p>权限点是全局定义（平台在 Flyway 里维护），所有租户共用同一套目录，
 * 因此这里不需要租户隔离，也不需要额外权限 —— 它只是「有哪些权限可以勾」的清单。</p>
 */
@Tag(name = "组织-权限目录", description = "权限点清单，用于岗位权限树渲染")
@RestController
@RequestMapping("/permissions")
@RequiredArgsConstructor
public class PermissionController {

    private final PermissionMapper permissionMapper;

    @Operation(summary = "权限点清单", description = "可选按模块过滤，便于按模块分组展示")
    @GetMapping("/catalog")
    public Result<List<PermissionResponse>> catalog(@RequestParam(required = false) String moduleKey) {
        var wrapper = Wrappers.<Permission>lambdaQuery()
                .eq(Permission::getStatus, EnableStatus.ENABLED);
        if (moduleKey != null && !moduleKey.isBlank()) {
            wrapper.eq(Permission::getModuleKey, moduleKey);
        }
        wrapper.orderByAsc(Permission::getSortOrder).orderByAsc(Permission::getId);

        return Result.ok(permissionMapper.selectList(wrapper).stream()
                .map(PermissionResponse::from)
                .toList());
    }
}
