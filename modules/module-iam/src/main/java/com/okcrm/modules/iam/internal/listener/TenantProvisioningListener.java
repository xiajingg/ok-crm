package com.okcrm.modules.iam.internal.listener;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.okcrm.modules.iam.domain.Employee;
import com.okcrm.modules.iam.domain.EmployeePosition;
import com.okcrm.modules.iam.domain.Position;
import com.okcrm.modules.iam.infra.mapper.EmployeeMapper;
import com.okcrm.modules.iam.infra.mapper.EmployeePositionMapper;
import com.okcrm.modules.iam.infra.mapper.PositionMapper;
import com.okcrm.modules.iam.internal.config.AdminSetupProperties;
import com.okcrm.modules.iam.internal.service.PositionService;
import com.okcrm.modules.tenant.api.event.TenantCreatedEvent;
import com.okcrm.platform.common.enums.EnableStatus;
import com.okcrm.platform.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;

/**
 * 企业初始化事件监听：为该企业创建组织底座。
 *
 * <p><b>为什么用同步监听而不是异步</b>：初始化是「要么全成功要么全失败」的动作。
 * 如果岗位创建失败却已经建好了企业记录，就会留下一个没有管理员、无法登录的残缺部署。
 * 同步监听让它和建企业处于同一事务，失败一起回滚。</p>
 *
 * <p><b>务必显式设置租户上下文</b>：事件监听发生在建企业之后，
 * 此时上下文里还没有这个企业（甚至完全没有租户上下文），
 * 不设置就会触发「缺少租户上下文」的 fail-fast 异常。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TenantProvisioningListener {

    /** 随机密码用的字符集：去掉了 0/O、1/l/I 这类容易看错的字符 */
    private static final String PASSWORD_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
    private static final int PASSWORD_LENGTH = 12;

    private final PositionService positionService;
    private final PositionMapper positionMapper;
    private final EmployeeMapper employeeMapper;
    private final EmployeePositionMapper employeePositionMapper;
    private final PasswordEncoder passwordEncoder;
    private final AdminSetupProperties adminSetupProperties;

    @EventListener
    public void onTenantCreated(TenantCreatedEvent event) {
        log.info("收到企业初始化事件，开始创建组织底座: tenantCode={}, tenantId={}",
                event.tenantCode(), event.tenantId());

        TenantContext.runAs(event.tenantId(), () -> {
            positionService.createDefaultPositions(event.tenantId());
            createDefaultAdmin(event);
        });
    }

    /**
     * 创建一个挂在「管理员」岗位下的默认账号，否则新部署开出来没人能登录。
     */
    private void createDefaultAdmin(TenantCreatedEvent event) {
        String username = adminSetupProperties.getUsername();

        Long exists = employeeMapper.selectCount(Wrappers.<Employee>lambdaQuery()
                .eq(Employee::getUsername, username));
        if (exists != null && exists > 0) {
            log.info("管理员账号 {} 已存在，跳过创建", username);
            return;
        }

        // 没配密码就随机生成：避免所有客户共用同一个默认密码
        String rawPassword = adminSetupProperties.getPassword();
        boolean generated = !StringUtils.hasText(rawPassword);
        if (generated) {
            rawPassword = randomPassword();
        }

        Employee admin = new Employee();
        admin.setUsername(username);
        admin.setPassword(passwordEncoder.encode(rawPassword));
        admin.setRealName(adminSetupProperties.getRealName());
        admin.setStatus(EnableStatus.ENABLED);
        admin.setRemark("部署初始化时自动创建");
        employeeMapper.insert(admin);

        Position adminPosition = positionMapper.selectOne(Wrappers.<Position>lambdaQuery()
                .eq(Position::getCode, "admin"));
        if (adminPosition != null) {
            EmployeePosition relation = new EmployeePosition();
            relation.setEmployeeId(admin.getId());
            relation.setPositionId(adminPosition.getId());
            employeePositionMapper.insert(relation);
        }

        if (generated) {
            // 只在这里打印一次：密码不入库明文，运维必须从日志里取
            log.warn("""

                    ============================================================
                     已创建初始管理员账号（企业：{}）
                       账号：{}
                       密码：{}
                     请立即登录并在「组织管理 → 员工管理」里修改密码。
                    ============================================================
                    """, event.tenantName(), username, rawPassword);
        } else {
            log.warn("已创建初始管理员账号 {}（密码取自 okcrm.setup.admin.password 配置，"
                    + "建议改由随机生成或部署后立即修改）", username);
        }
    }

    private static String randomPassword() {
        SecureRandom random = new SecureRandom();
        StringBuilder builder = new StringBuilder(PASSWORD_LENGTH);
        for (int i = 0; i < PASSWORD_LENGTH; i++) {
            builder.append(PASSWORD_CHARS.charAt(random.nextInt(PASSWORD_CHARS.length())));
        }
        return builder.toString();
    }
}
