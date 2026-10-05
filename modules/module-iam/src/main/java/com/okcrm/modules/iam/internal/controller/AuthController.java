package com.okcrm.modules.iam.internal.controller;

import com.okcrm.modules.iam.internal.dto.DeploymentInfoResponse;
import com.okcrm.modules.iam.internal.dto.LoginRequest;
import com.okcrm.modules.iam.internal.dto.LoginResponse;
import com.okcrm.modules.iam.internal.dto.MenuNode;
import com.okcrm.modules.iam.internal.dto.ProfileResponse;
import com.okcrm.modules.iam.internal.service.AuthService;
import com.okcrm.modules.iam.internal.service.MenuService;
import com.okcrm.modules.iam.internal.service.PermissionService;
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
 *
 * <p>单企业私有化部署下没有「选择企业」与「平台超管」两条链路，
 * 因此这里只有登录、当前用户资料与动态菜单三个接口。</p>
 */
@Tag(name = "认证", description = "登录、当前用户、动态菜单")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final MenuService menuService;
    private final PermissionService permissionService;

    @Operation(summary = "部署信息", description = "登录页展示企业名称用，无需登录")
    @GetMapping("/deployment")
    public Result<DeploymentInfoResponse> deployment() {
        return Result.ok(authService.deploymentInfo());
    }

    @Operation(summary = "登录", description = "单企业部署，只需账号密码")
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.ok(authService.login(request));
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
        Set<String> permissions = permissionService.permissionsOf(loginUser.employeeId());
        return Result.ok(menuService.menuOf(loginUser.tenantId(), permissions));
    }
}
