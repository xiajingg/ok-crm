package com.okcrm.modules.tenant.internal.controller;

import com.okcrm.modules.tenant.api.TenantBrief;
import com.okcrm.modules.tenant.api.TenantQueryService;
import com.okcrm.modules.tenant.internal.dto.TenantConfigUpdateRequest;
import com.okcrm.modules.tenant.internal.service.TenantConfigService;
import com.okcrm.platform.common.api.ErrorCode;
import com.okcrm.platform.common.api.Result;
import com.okcrm.platform.common.exception.BizException;
import com.okcrm.platform.security.checker.ModuleChecker;
import com.okcrm.platform.tenant.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Set;

/**
 * 租户内：查看本租户信息、已购模块与配置。
 *
 * <p>所有接口都隐式作用于「当前登录租户」，不需要也不接受 tenantId 参数 ——
 * 这样从接口层面就杜绝了「传别人租户 ID 越权读取」。</p>
 */
@Tag(name = "租户-当前租户", description = "本租户信息、已购模块、租户配置")
@RestController
@RequestMapping("/tenants")
@RequiredArgsConstructor
public class TenantController {

    private final TenantQueryService tenantQueryService;
    private final TenantConfigService tenantConfigService;
    private final ModuleChecker moduleChecker;

    @Operation(summary = "当前租户信息")
    @GetMapping("/current")
    public Result<TenantBrief> current() {
        Long tenantId = TenantContext.requireTenantId();
        return Result.ok(tenantQueryService.findById(tenantId)
                .orElseThrow(() -> BizException.of(ErrorCode.TENANT_NOT_FOUND)));
    }

    @Operation(summary = "当前租户已购模块", description = "前端据此渲染模块分组菜单")
    @GetMapping("/current/modules")
    public Result<Set<String>> currentModules() {
        return Result.ok(moduleChecker.licensedModules());
    }

    @Operation(summary = "当前租户配置")
    @GetMapping("/current/config")
    public Result<Map<String, String>> currentConfig() {
        return Result.ok(tenantConfigService.getAll(TenantContext.requireTenantId()));
    }

    @Operation(summary = "修改当前租户配置")
    @PutMapping("/current/config")
    @PreAuthorize("@perm.has('tenant:config:update')")
    public Result<Void> updateConfig(@Valid @RequestBody TenantConfigUpdateRequest request) {
        tenantConfigService.putAll(TenantContext.requireTenantId(), request.configs());
        return Result.ok();
    }
}
