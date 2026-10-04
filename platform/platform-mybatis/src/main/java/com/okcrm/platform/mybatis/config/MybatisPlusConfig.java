package com.okcrm.platform.mybatis.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.okcrm.platform.common.security.CurrentUserProvider;
import com.okcrm.platform.mybatis.tenant.TenantLineHandlerImpl;
import com.okcrm.platform.tenant.SharedTableIsolationStrategy;
import com.okcrm.platform.tenant.TenantIsolationStrategy;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashSet;

/**
 * MyBatis-Plus 装配。
 */
@Configuration
public class MybatisPlusConfig {

    /**
     * 租户隔离策略。默认是行级隔离；将来要升级成 Schema / 独立库隔离，
     * 只需在这里换一个实现，业务代码零改动。
     */
    @Bean
    public TenantIsolationStrategy tenantIsolationStrategy(TenantProperties properties) {
        return new SharedTableIsolationStrategy(new HashSet<>(properties.getExtraGlobalTables()));
    }

    /**
     * 拦截器链。
     *
     * <p><b>顺序有硬性要求</b>：多租户拦截器必须放在分页拦截器之前，
     * 否则分页的 count 语句不会带上 tenant_id 条件，会统计到其他租户的数据。</p>
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor(TenantIsolationStrategy isolationStrategy) {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        // 1. 多租户：自动追加 tenant_id 条件
        interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(new TenantLineHandlerImpl(isolationStrategy)));

        // 2. 分页：不指定 DbType，由连接自动探测（兼容 MySQL 与测试用 H2）
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor();
        pagination.setMaxLimit(500L);
        pagination.setOverflow(false);
        interceptor.addInnerInterceptor(pagination);

        return interceptor;
    }

    @Bean
    public MetaObjectHandler auditMetaObjectHandler(ObjectProvider<CurrentUserProvider> currentUserProvider) {
        return new AuditMetaObjectHandler(currentUserProvider);
    }
}
