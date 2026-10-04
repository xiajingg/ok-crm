package com.okcrm.modules.tenant.internal.controller;

import com.okcrm.modules.tenant.internal.dto.ModuleGrantRequest;
import com.okcrm.modules.tenant.internal.dto.ModuleResponse;
import com.okcrm.modules.tenant.internal.service.TenantModuleService;
import com.okcrm.platform.common.api.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 平台超管：模块目录与租户授权。
 *
 * <p>这是「按模块独立售卖」的运营入口：销售卖出去一个模块，就在这里给租户开授权。</p>
 */
@Tag(name = "平台-模块授权", description = "可售卖模块目录与租户授权（平台超管）")
@RestController
@RequestMapping("/platform/modules")
@RequiredArgsConstructor
@PreAuthorize("@authz.platformAdmin()")
public class PlatformModuleController {

    private final TenantModuleService tenantModuleService;

    @Operation(summary = "模块目录（产品价目表）")
    @GetMapping("/catalog")
    public Result<List<ModuleResponse>> catalog() {
        return Result.ok(tenantModuleService.listCatalog());
    }

    @Operation(summary = "查询某租户的模块授权明细")
    @GetMapping("/tenant/{tenantId}")
    public Result<List<ModuleResponse>> tenantModules(@PathVariable Long tenantId) {
        return Result.ok(tenantModuleService.listLicensedDetail(tenantId));
    }

    @Operation(summary = "给租户授权模块")
    @PostMapping("/tenant/{tenantId}/grant")
    public Result<Void> grant(@PathVariable Long tenantId,
                              @Valid @RequestBody ModuleGrantRequest request) {
        tenantModuleService.grant(tenantId, request.moduleKeys(), request.expireDate(), request.remark());
        return Result.ok();
    }

    @Operation(summary = "撤销租户的某个模块授权")
    @DeleteMapping("/tenant/{tenantId}/{moduleKey}")
    public Result<Void> revoke(@PathVariable Long tenantId, @PathVariable String moduleKey) {
        tenantModuleService.revoke(tenantId, moduleKey);
        return Result.ok();
    }
}
