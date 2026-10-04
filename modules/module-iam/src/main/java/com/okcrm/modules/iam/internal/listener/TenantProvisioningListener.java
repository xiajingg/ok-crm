package com.okcrm.modules.iam.internal.listener;

import com.okcrm.modules.iam.domain.Employee;
import com.okcrm.modules.iam.domain.EmployeePosition;
import com.okcrm.modules.iam.domain.Position;
import com.okcrm.modules.iam.infra.mapper.EmployeeMapper;
import com.okcrm.modules.iam.infra.mapper.EmployeePositionMapper;
import com.okcrm.modules.iam.infra.mapper.PositionMapper;
import com.okcrm.modules.iam.internal.config.TenantProvisioningProperties;
import com.okcrm.modules.iam.internal.service.PositionService;
import com.okcrm.modules.tenant.api.event.TenantCreatedEvent;
import com.okcrm.platform.common.enums.EnableStatus;
import com.okcrm.platform.tenant.TenantContext;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 租户开通事件监听：初始化该租户的组织底座。
 *
 * <p><b>为什么用同步监听而不是异步</b>：开通租户是「要么全成功要么全失败」的动作。
 * 如果岗位初始化失败却已经建好了租户，就会留下一个没有管理员、无法登录的残缺租户。
 * 同步监听让它和建租户处于同一事务，失败一起回滚。</p>
 *
 * <p><b>务必显式设置租户上下文</b>：事件监听发生在建租户之后，
 * 此时上下文里还没有这个新租户（甚至完全没有租户上下文），
 * 不设置就会触发「缺少租户上下文」的 fail-fast 异常。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TenantProvisioningListener {

    private final PositionService positionService;
    private final PositionMapper positionMapper;
    private final EmployeeMapper employeeMapper;
    private final EmployeePositionMapper employeePositionMapper;
    private final PasswordEncoder passwordEncoder;
    private final TenantProvisioningProperties provisioningProperties;

    @EventListener
    public void onTenantCreated(TenantCreatedEvent event) {
        log.info("收到租户开通事件，开始初始化组织底座: tenantCode={}, tenantId={}",
                event.tenantCode(), event.tenantId());

        TenantContext.runAs(event.tenantId(), () -> {
            positionService.createDefaultPositions(event.tenantId());
            createDefaultAdmin(event);
        });
    }

    /**
     * 创建一个挂在「管理员」岗位下的默认账号，否则新租户开出来没人能登录。
     */
    private void createDefaultAdmin(TenantCreatedEvent event) {
        String username = provisioningProperties.getDefaultAdminUsername();

        Long exists = employeeMapper.selectCount(Wrappers.<Employee>lambdaQuery()
                .eq(Employee::getUsername, username));
        if (exists != null && exists > 0) {
            return;
        }

        Employee admin = new Employee();
        admin.setUsername(username);
        admin.setPassword(passwordEncoder.encode(provisioningProperties.getDefaultAdminPassword()));
        admin.setRealName(provisioningProperties.getDefaultAdminRealName());
        admin.setStatus(EnableStatus.ENABLED);
        admin.setRemark("租户开通时自动创建");
        employeeMapper.insert(admin);

        Position adminPosition = positionMapper.selectOne(Wrappers.<Position>lambdaQuery()
                .eq(Position::getCode, "admin"));
        if (adminPosition != null) {
            EmployeePosition relation = new EmployeePosition();
            relation.setEmployeeId(admin.getId());
            relation.setPositionId(adminPosition.getId());
            employeePositionMapper.insert(relation);
        }

        log.warn("租户 {} 已创建默认管理员账号 [{}]，初始密码为配置项 okcrm.tenant-provisioning.default-admin-password，"
                        + "请提醒客户首次登录后立即修改", event.tenantCode(), username);
    }
}
