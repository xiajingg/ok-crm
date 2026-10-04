package com.okcrm.modules.iam.internal.controller;

import com.okcrm.modules.iam.internal.dto.LoginRequest;
import com.okcrm.modules.iam.internal.dto.LoginResponse;
import com.okcrm.modules.iam.internal.dto.MenuNode;
import com.okcrm.modules.iam.internal.dto.PlatformLoginRequest;
import com.okcrm.modules.iam.internal.dto.ProfileResponse;
import com.okcrm.modules.iam.internal.service.AuthService;
import com.okcrm.modules.iam.internal.service.MenuService;
import com.okcrm.modules.iam.internal.service.PermissionService;
import com.okcrm.modules.tenant.api.TenantBrief;
import com.okcrm.platform.common.api.Result;
import com.okcrm.platform.security.principal.LoginUser;
import com.okcrm.platform.security.principal.LoginUserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

/**
 * 认证与当前用户。
 */
@Tag(name = "认证", description = "登录、当前用户、动态菜单")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final MenuService menuService;
    private final PermissionService permissionService;

    @Operation(summary = "登录页的租户列表", description = "无需登录即可访问")
    @GetMapping("/tenants")
    public Result<List<TenantBrief>> loginTenants() {
        return Result.ok(authService.listLoginTenants());
    }

    @Operation(summary = "租户内用户登录")
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.ok(authService.login(request));
    }

    @Operation(summary = "平台超管登录")
    @PostMapping("/platform-login")
    public Result<LoginResponse> platformLogin(@Valid @RequestBody PlatformLoginRequest request) {
        return Result.ok(authService.platformLogin(request));
    }

    @Operation(summary = "当前登录用户资料")
    @GetMapping("/me")
    public Result<ProfileResponse> me() {
        return Result.ok(authService.currentProfile());
    }

    @Operation(summary = "当前用户的动态菜单",
            description = "后端按「已购模块 ∩ 岗位权限」下发，未购买模块的菜单不会出现")
    @GetMapping("/menus")
    public Result<List<MenuNode>> menus() {
        LoginUser loginUser = LoginUserContext.require();
        Set<String> permissions = loginUser.platformAdmin()
                ? Set.of()
                : permissionService.permissionsOf(loginUser.employeeId());

        return Result.ok(menuService.menuOf(loginUser.tenantId(), loginUser.platformAdmin(), permissions));
    }
}
