package com.okcrm.modules.tenant.internal.setup;

import com.okcrm.modules.tenant.api.event.TenantCreatedEvent;
import com.okcrm.modules.tenant.domain.Tenant;
import com.okcrm.modules.tenant.infra.mapper.TenantMapper;
import com.okcrm.modules.tenant.internal.config.SetupProperties;
import com.okcrm.modules.tenant.internal.service.TenantModuleService;
import com.okcrm.platform.common.enums.EnableStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 部署初始化：首次启动时按配置写入企业信息。
 *
 * <p><b>为什么做成自动执行而不是「让运维跑一段 SQL」</b>：</p>
 * <ul>
 *   <li>SQL 里没法生成 BCrypt 密码、也没法复用「默认岗位 + 权限模板」的业务逻辑，
 *       手工 SQL 极易漏建岗位导致新客户登进去什么都没有</li>
 *   <li>自动执行 + 幂等，让「改配置 → 启动」成为唯一的部署动作，
 *       客户不需要理解数据库结构</li>
 * </ul>
 *
 * <p>整个过程在一个事务里：建企业 → 授权模块 → 发布开通事件（IAM 据此建默认岗位与管理员账号）。
 * 任何一步失败都整体回滚，不会留下半个残废的部署。</p>
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
@ConditionalOnProperty(name = "okcrm.setup.enabled", havingValue = "true", matchIfMissing = true)
public class DeploymentSetupRunner implements ApplicationRunner {

    private final SetupProperties setupProperties;
    private final TenantMapper tenantMapper;
    private final TenantModuleService tenantModuleService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void run(ApplicationArguments args) {
        Long tenantId = setupProperties.getTenantId();

        if (tenantMapper.selectById(tenantId) != null) {
            log.info("企业信息已存在，跳过初始化（id={}）", tenantId);
            return;
        }

        log.info("首次启动，开始初始化企业信息：id={}, name={}",
                tenantId, setupProperties.getTenantName());

        Tenant tenant = new Tenant();
        // 显式指定主键：单企业部署下用它作为固定的租户上下文标识
        tenant.setId(tenantId);
        tenant.setCode(StringUtils.hasText(setupProperties.getTenantCode())
                ? setupProperties.getTenantCode() : "default");
        tenant.setName(StringUtils.hasText(setupProperties.getTenantName())
                ? setupProperties.getTenantName() : "我的企业");
        tenant.setRegion(setupProperties.getRegion());
        tenant.setRegionName(setupProperties.getRegionName());
        tenant.setTimezone(setupProperties.getTimezone());
        tenant.setCurrency(setupProperties.getCurrency());
        tenant.setContactName(setupProperties.getContactName());
        tenant.setContactPhone(setupProperties.getContactPhone());
        tenant.setStatus(EnableStatus.ENABLED);
        tenantMapper.insert(tenant);

        // 本部署包含哪些模块，就授权哪些（模块被编译期裁剪掉时不会出现在 sys_module 里）
        tenantModuleService.grantForNewTenant(tenantId, null);

        // 发布开通事件：IAM 模块据此创建默认岗位（管理员/销售主管/销售）并分配权限
        eventPublisher.publishEvent(new TenantCreatedEvent(
                tenant.getId(), tenant.getCode(), tenant.getName(), tenant.getRegion()));

        log.info("企业初始化完成：{}（{}）", tenant.getName(), tenant.getCode());
    }
}
