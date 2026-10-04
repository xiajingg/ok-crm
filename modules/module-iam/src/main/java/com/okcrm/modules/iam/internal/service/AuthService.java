package com.okcrm.modules.iam.internal.service;

import com.okcrm.modules.iam.api.EmployeeQueryService;
import com.okcrm.modules.iam.domain.Employee;
import com.okcrm.modules.iam.internal.config.PlatformAdminProperties;
import com.okcrm.modules.iam.internal.dto.LoginRequest;
import com.okcrm.modules.iam.internal.dto.LoginResponse;
import com.okcrm.modules.iam.internal.dto.PlatformLoginRequest;
import com.okcrm.modules.iam.internal.dto.ProfileResponse;
import com.okcrm.modules.tenant.api.TenantBrief;
import com.okcrm.modules.tenant.api.TenantQueryService;
import com.okcrm.platform.common.api.ErrorCode;
import com.okcrm.platform.common.constant.CommonConstants;
import com.okcrm.platform.common.enums.EnableStatus;
import com.okcrm.platform.common.exception.BizException;
import com.okcrm.platform.security.jwt.JwtTokenProvider;
import com.okcrm.platform.security.principal.LoginUser;
import com.okcrm.platform.security.principal.LoginUserContext;
import com.okcrm.platform.security.spi.ModuleLicenseProvider;
import com.okcrm.platform.tenant.TenantContext;
import com.okcrm.platform.tenant.TenantInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Set;

/**
 * 认证服务。
 *
 * <p>租户内用户与平台超管是两条完全独立的登录链路：</p>
 * <ul>
 *   <li><b>租户用户</b>：租户编码 + 账号 + 密码，账号来自 {@code sys_employee}</li>
 *   <li><b>平台超管</b>：账号 + 密码来自配置，不属于任何租户</li>
 * </ul>
 * <p>两条链路不共用任何校验代码，从根上杜绝「租户管理员拿到某个权限码就跨租户操作」。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final TenantQueryService tenantQueryService;
    private final EmployeeQueryService employeeQueryService;
    private final EmployeeService employeeService;
    private final PermissionService permissionService;
    private final ModuleLicenseProvider moduleLicenseProvider;
    private final JwtTokenProvider tokenProvider;
    private final PasswordEncoder passwordEncoder;
    private final PlatformAdminProperties platformAdminProperties;

    /**
     * 登录页的租户下拉列表。
     */
    public List<TenantBrief> listLoginTenants() {
        return tenantQueryService.listEnabledForLogin();
    }

    /**
     * 租户内用户登录。
     */
    @Transactional(rollbackFor = Exception.class)
    public LoginResponse login(LoginRequest request) {
        TenantBrief tenant = tenantQueryService.findByCode(request.tenantCode())
                // 租户不存在与密码错误返回同一个提示，避免被用来枚举租户
                .orElseThrow(() -> BizException.of(ErrorCode.LOGIN_FAILED));

        if (!tenant.enabled()) {
            throw BizException.of(ErrorCode.TENANT_DISABLED);
        }

        // 租户上下文必须在这里建立：后续所有查询都要靠它做隔离
        TenantInfo tenantInfo = TenantInfo.of(tenant.id(), tenant.code(), tenant.region());

        return TenantContext.callAs(tenantInfo, () -> {
            Employee employee = employeeService.findByUsername(request.username())
                    .orElseThrow(() -> BizException.of(ErrorCode.LOGIN_FAILED));

            if (!passwordEncoder.matches(request.password(), employee.getPassword())) {
                throw BizException.of(ErrorCode.LOGIN_FAILED);
            }
            if (employee.getStatus() != EnableStatus.ENABLED) {
                throw BizException.of(ErrorCode.ACCOUNT_DISABLED);
            }

            employeeService.touchLastLogin(employee.getId());

            LoginUser loginUser = new LoginUser(
                    employee.getId(),
                    employee.getUsername(),
                    employee.getRealName(),
                    tenant.id(),
                    tenant.code(),
                    false
            );
            String token = tokenProvider.generate(loginUser);
            Set<String> permissions = permissionService.permissionsOf(employee.getId());
            Set<String> modules = moduleLicenseProvider.licensedModules(tenant.id());

            log.info("用户登录成功: tenant={}, username={}", tenant.code(), employee.getUsername());

            return new LoginResponse(
                    token,
                    "Bearer",
                    tokenProvider.getExpireSeconds(),
                    new LoginResponse.UserInfo(employee.getId(), employee.getUsername(),
                            employee.getRealName(), false),
                    toTenantSummary(tenant),
                    permissions,
                    modules
            );
        });
    }

    /**
     * 平台超管登录。
     */
    public LoginResponse platformLogin(PlatformLoginRequest request) {
        if (!platformAdminProperties.isEnabled()) {
            throw BizException.of(ErrorCode.FORBIDDEN, "平台超管登录入口未启用");
        }
        boolean matched = constantTimeEquals(platformAdminProperties.getUsername(), request.username())
                && constantTimeEquals(platformAdminProperties.getPassword(), request.password());
        if (!matched) {
            throw BizException.of(ErrorCode.LOGIN_FAILED);
        }

        LoginUser loginUser = LoginUser.platformAdmin(
                CommonConstants.PLATFORM_ADMIN_ID,
                platformAdminProperties.getUsername(),
                platformAdminProperties.getRealName());

        String token = tokenProvider.generate(loginUser);
        log.info("平台超管登录成功: username={}", loginUser.username());

        return new LoginResponse(
                token,
                "Bearer",
                tokenProvider.getExpireSeconds(),
                new LoginResponse.UserInfo(loginUser.employeeId(), loginUser.username(),
                        loginUser.realName(), true),
                null,
                Set.of(),
                Set.of()
        );
    }

    /**
     * 当前登录用户资料。
     */
    public ProfileResponse currentProfile() {
        LoginUser loginUser = LoginUserContext.require();

        if (loginUser.platformAdmin()) {
            return new ProfileResponse(
                    new LoginResponse.UserInfo(loginUser.employeeId(), loginUser.username(),
                            loginUser.realName(), true),
                    null,
                    Set.of(),
                    Set.of()
            );
        }

        Set<String> permissions = permissionService.permissionsOf(loginUser.employeeId());
        Set<String> modules = moduleLicenseProvider.licensedModules(loginUser.tenantId());
        LoginResponse.TenantSummary tenantSummary = tenantQueryService.findById(loginUser.tenantId())
                .map(AuthService::toTenantSummary)
                .orElse(null);

        return new ProfileResponse(
                new LoginResponse.UserInfo(loginUser.employeeId(), loginUser.username(),
                        loginUser.realName(), false),
                tenantSummary,
                permissions,
                modules
        );
    }

    private static LoginResponse.TenantSummary toTenantSummary(TenantBrief tenant) {
        return new LoginResponse.TenantSummary(
                tenant.id(), tenant.code(), tenant.name(),
                tenant.region(), tenant.regionName(), tenant.timezone(), tenant.currency());
    }

    /**
     * 常量时间比较，避免通过响应时间差异推测账号/密码。
     */
    private static boolean constantTimeEquals(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }
}
