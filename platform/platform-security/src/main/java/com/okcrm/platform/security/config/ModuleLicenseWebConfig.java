package com.okcrm.platform.security.config;

import com.okcrm.platform.security.checker.ModuleChecker;
import com.okcrm.platform.security.web.ModuleLicenseInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 注册模块授权拦截器。
 */
@Configuration
@RequiredArgsConstructor
public class ModuleLicenseWebConfig implements WebMvcConfigurer {

    private final ModuleChecker moduleChecker;

    @Override
    public void addInterceptors(@NonNull InterceptorRegistry registry) {
        registry.addInterceptor(new ModuleLicenseInterceptor(moduleChecker))
                .addPathPatterns("/**");
    }
}
