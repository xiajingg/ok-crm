package com.okcrm.platform.security.principal;

import com.okcrm.platform.common.security.CurrentUserProvider;
import org.springframework.stereotype.Component;

/**
 * {@link CurrentUserProvider} 的实现：从 SecurityContext 取当前员工。
 *
 * <p>持久层（审计字段自动填充）通过 SPI 使用它，从而不必依赖安全模块。</p>
 */
@Component
public class CurrentUserProviderImpl implements CurrentUserProvider {

    @Override
    public Long currentUserId() {
        return LoginUserContext.employeeId();
    }
}
