package com.okcrm.modules.iam.internal.setup;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.okcrm.modules.iam.domain.Employee;
import com.okcrm.modules.iam.infra.mapper.EmployeeMapper;
import com.okcrm.modules.iam.internal.config.AdminSetupProperties;
import com.okcrm.modules.tenant.api.TenantQueryService;
import com.okcrm.platform.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 管理员密码重置。
 *
 * <p>存在的理由：初始密码是<b>随机生成、只在首次建库时打印一次</b>的，
 * 错过了就进不去系统。而「客户忘了管理员密码」是必然会发生的支持场景 ——
 * 没有这个入口，运维只能让客户重装数据库，代价完全不成比例。</p>
 *
 * <p>用法（一次性）：</p>
 * <pre>
 *   ADMIN_RESET_PASSWORD=新密码 ./scripts/start-local.sh mysql
 *   # 或
 *   java -jar ok-crm.jar --okcrm.setup.admin.reset-password=新密码 --spring.profiles.active=prod
 * </pre>
 *
 * <p><b>每次启动都会重置</b>，所以用完必须把配置删掉 —— 日志里会打 WARN 提醒。</p>
 *
 * <p>执行顺序在 {@code DeploymentSetupRunner}（@Order(1)）之后，
 * 保证企业记录已存在、能建立租户上下文。</p>
 */
@Slf4j
@Component
@Order(2)
@RequiredArgsConstructor
public class AdminPasswordResetRunner implements ApplicationRunner {

    private static final int MIN_PASSWORD_LENGTH = 6;

    private final AdminSetupProperties adminSetupProperties;
    private final TenantQueryService tenantQueryService;
    private final EmployeeMapper employeeMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void run(ApplicationArguments args) {
        String newPassword = adminSetupProperties.getResetPassword();
        if (!StringUtils.hasText(newPassword)) {
            return;
        }
        if (newPassword.length() < MIN_PASSWORD_LENGTH) {
            log.error("okcrm.setup.admin.reset-password 长度不足 {} 位，已忽略本次重置",
                    MIN_PASSWORD_LENGTH);
            return;
        }

        String username = adminSetupProperties.getUsername();
        tenantQueryService.findCurrentDeploymentTenant().ifPresentOrElse(
                tenant -> TenantContext.runAs(tenant.id(), () -> resetPassword(username, newPassword)),
                () -> log.warn("当前部署还没有企业记录，跳过密码重置"));
    }

    private void resetPassword(String username, String newPassword) {
        Employee admin = employeeMapper.selectOne(Wrappers.<Employee>lambdaQuery()
                .eq(Employee::getUsername, username));
        if (admin == null) {
            log.warn("未找到账号 {}，跳过密码重置", username);
            return;
        }

        Employee update = new Employee();
        update.setId(admin.getId());
        update.setPassword(passwordEncoder.encode(newPassword));
        employeeMapper.updateById(update);

        log.warn("""

                ============================================================
                 已按 okcrm.setup.admin.reset-password 重置账号「{}」的密码。
                 ⚠️ 每次启动都会重置 —— 请确认后把该项从配置里删掉
                    （环境变量 ADMIN_RESET_PASSWORD / .env.local）。
                ============================================================
                """, username);
    }
}
