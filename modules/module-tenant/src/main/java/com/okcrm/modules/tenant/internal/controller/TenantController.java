package com.okcrm.modules.tenant.internal.controller;

import com.okcrm.modules.tenant.internal.dto.TenantConfigUpdateRequest;
import com.okcrm.modules.tenant.internal.dto.TenantResponse;
import com.okcrm.modules.tenant.internal.dto.TenantUpdateRequest;
import com.okcrm.modules.tenant.internal.service.TenantConfigService;
import com.okcrm.modules.tenant.internal.service.TenantService;
import com.okcrm.platform.common.api.Result;
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
 * 企业设置。
 *
 * <p>单企业私有化部署下，这里是「本企业」的信息入口：企业名称、地区、时区、币种与租户级配置。</p>
 *
 * <p>所有接口都隐式作用于当前部署所属的企业，不接受 tenantId 参数 ——
 * 从接口层面就杜绝了越权读取其它企业的数据。</p>
 */
@Tag(name = "企业设置", description = "本企业信息、已启用模块、租户级配置")
@RestController
@RequestMapping("/tenants")
@RequiredArgsConstructor
public class TenantController {

    private final TenantService tenantService;
    private final TenantConfigService tenantConfigService;
    private final ModuleChecker moduleChecker;

    @Operation(summary = "当前企业信息")
    @GetMapping("/current")
    public Result<TenantResponse> current() {
        return Result.ok(tenantService.detail(TenantContext.requireTenantId()));
    }

    @Operation(summary = "修改企业信息",
            description = "企业编码不可改；改动即时生效，不需要重启")
    @PutMapping("/current")
    @PreAuthorize("@perm.has('tenant:config:update')")
    public Result<TenantResponse> update(@Valid @RequestBody TenantUpdateRequest request) {
        return Result.ok(tenantService.update(TenantContext.requireTenantId(), request));
    }

    @Operation(summary = "当前企业已启用的模块", description = "前端据此渲染模块分组菜单")
    @GetMapping("/current/modules")
    public Result<Set<String>> currentModules() {
        return Result.ok(moduleChecker.licensedModules());
    }

    @Operation(summary = "当前企业配置")
    @GetMapping("/current/config")
    public Result<Map<String, String>> currentConfig() {
        return Result.ok(tenantConfigService.getAll(TenantContext.requireTenantId()));
    }

    @Operation(summary = "修改当前企业配置")
    @PutMapping("/current/config")
    @PreAuthorize("@perm.has('tenant:config:update')")
    public Result<Void> updateConfig(@Valid @RequestBody TenantConfigUpdateRequest request) {
        tenantConfigService.putAll(TenantContext.requireTenantId(), request.configs());
        return Result.ok();
    }
}
