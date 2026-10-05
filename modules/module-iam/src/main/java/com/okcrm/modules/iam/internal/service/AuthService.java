package com.okcrm.modules.iam.internal.service;

import com.okcrm.modules.iam.domain.Employee;
import com.okcrm.modules.iam.internal.dto.DeploymentInfoResponse;
import com.okcrm.modules.iam.internal.dto.LoginRequest;
import com.okcrm.modules.iam.internal.dto.LoginResponse;
import com.okcrm.modules.iam.internal.dto.ProfileResponse;
import com.okcrm.modules.tenant.api.TenantBrief;
import com.okcrm.modules.tenant.api.TenantQueryService;
import com.okcrm.platform.common.api.ErrorCode;
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

import java.util.Set;

/**
 * 认证服务。
 *
 * <p>单企业私有化部署：一套部署只服务一个企业，企业信息由
 * {@code okcrm.setup} 初始化流程写入，因此登录只需要账号密码，
 * 不再有「选择企业」这一步，也没有平台超管这条链路。</p>
 *
 * <p>租户上下文仍然保留并照常建立 —— 这是隔离机制的底座，
 * 客户若日后拆出子公司或做托管部署，不需要改代码。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final TenantQueryService tenantQueryService;
    private final EmployeeService employeeService;
    private final PermissionService permissionService;
    private final ModuleLicenseProvider moduleLicenseProvider;
    private final JwtTokenProvider tokenProvider;
    private final PasswordEncoder passwordEncoder;

    /**
     * 登录。
     */
    @Transactional(rollbackFor = Exception.class)
    public LoginResponse login(LoginRequest request) {
        TenantBrief tenant = currentDeploymentTenant();
        TenantInfo tenantInfo = TenantInfo.of(tenant.id(), tenant.code(), tenant.region());

        // 租户上下文必须在这里建立：后续所有查询都要靠它做隔离
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
     * 当前登录用户资料。
     */
    public ProfileResponse currentProfile() {
        LoginUser loginUser = LoginUserContext.require();

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

    /**
     * 部署信息：登录页展示企业名称用。
     *
     * <p>无需登录即可访问，只返回企业名称与编码。企业还没初始化时返回空对象，
     * 让登录页退化成通用标题而不是报错。</p>
     */
    public DeploymentInfoResponse deploymentInfo() {
        return tenantQueryService.findCurrentDeploymentTenant()
                .map(tenant -> new DeploymentInfoResponse(tenant.name(), tenant.code()))
                .orElseGet(() -> new DeploymentInfoResponse(null, null));
    }

    /**
     * 取当前部署所属的企业。
     *
     * <p>单企业部署下只有一个企业，取不到说明初始化没做，直接给出可操作的提示。</p>
     */
    private TenantBrief currentDeploymentTenant() {
        TenantBrief tenant = tenantQueryService.findCurrentDeploymentTenant()
                .orElseThrow(() -> BizException.of(ErrorCode.TENANT_NOT_FOUND,
                        "本部署尚未初始化企业信息，请检查 okcrm.setup 配置后重启应用"));
        if (!tenant.enabled()) {
            throw BizException.of(ErrorCode.TENANT_DISABLED);
        }
        return tenant;
    }

    private static LoginResponse.TenantSummary toTenantSummary(TenantBrief tenant) {
        return new LoginResponse.TenantSummary(
                tenant.id(), tenant.code(), tenant.name(),
                tenant.region(), tenant.regionName(), tenant.timezone(), tenant.currency());
    }
}
