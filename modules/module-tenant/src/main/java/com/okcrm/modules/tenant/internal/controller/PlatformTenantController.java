package com.okcrm.modules.tenant.internal.controller;

import com.okcrm.modules.tenant.api.TenantQueryService;
import com.okcrm.modules.tenant.internal.dto.TenantConfigUpdateRequest;
import com.okcrm.modules.tenant.internal.dto.TenantCreateRequest;
import com.okcrm.modules.tenant.internal.dto.TenantResponse;
import com.okcrm.modules.tenant.internal.dto.TenantUpdateRequest;
import com.okcrm.modules.tenant.internal.service.TenantConfigService;
import com.okcrm.modules.tenant.internal.service.TenantService;
import com.okcrm.platform.common.api.PageResult;
import com.okcrm.platform.common.api.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 平台超管：租户开通与管理。
 *
 * <p>整个类的入口都要求平台超管身份。这些接口<b>不做租户隔离</b>
 * （{@code sys_tenant} 是全局表），因此绝不能与租户内接口混用同一套 Controller。</p>
 */
@Tag(name = "平台-租户管理", description = "开通、查询、变更租户（平台超管）")
@RestController
@RequestMapping("/platform/tenants")
@RequiredArgsConstructor
@PreAuthorize("@authz.platformAdmin()")
public class PlatformTenantController {

    private final TenantService tenantService;
    private final TenantConfigService tenantConfigService;
    private final TenantQueryService tenantQueryService;

    @Operation(summary = "分页查询租户")
    @GetMapping
    public Result<PageResult<TenantResponse>> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize) {
        return Result.ok(tenantService.page(keyword, pageNum, pageSize));
    }

    @Operation(summary = "租户详情")
    @GetMapping("/{tenantId}")
    public Result<TenantResponse> detail(@PathVariable Long tenantId) {
        return Result.ok(tenantService.detail(tenantId));
    }

    @Operation(summary = "开通租户", description = "建租户 + 授权模块 + 初始化默认岗位，同一事务完成")
    @PostMapping
    public Result<TenantResponse> create(@Valid @RequestBody TenantCreateRequest request) {
        return Result.ok(tenantService.create(request));
    }

    @Operation(summary = "更新租户信息")
    @PutMapping("/{tenantId}")
    public Result<TenantResponse> update(@PathVariable Long tenantId,
                                         @Valid @RequestBody TenantUpdateRequest request) {
        return Result.ok(tenantService.update(tenantId, request));
    }

    @Operation(summary = "启用/停用租户")
    @PutMapping("/{tenantId}/status")
    public Result<Void> changeStatus(@PathVariable Long tenantId, @RequestParam boolean enabled) {
        tenantService.changeStatus(tenantId, enabled);
        return Result.ok();
    }

    @Operation(summary = "查询租户配置")
    @GetMapping("/{tenantId}/config")
    public Result<Map<String, String>> getConfig(@PathVariable Long tenantId) {
        return Result.ok(tenantConfigService.getAll(tenantId));
    }

    @Operation(summary = "修改租户配置", description = "公海回收规则等按租户可配项")
    @PutMapping("/{tenantId}/config")
    public Result<Void> updateConfig(@PathVariable Long tenantId,
                                     @Valid @RequestBody TenantConfigUpdateRequest request) {
        tenantConfigService.putAll(tenantId, request.configs());
        return Result.ok();
    }

    @Operation(summary = "租户是否可用（内部/联调用）")
    @GetMapping("/{tenantId}/enabled")
    public Result<Boolean> enabled(@PathVariable Long tenantId) {
        return Result.ok(tenantQueryService.isEnabled(tenantId));
    }
}
