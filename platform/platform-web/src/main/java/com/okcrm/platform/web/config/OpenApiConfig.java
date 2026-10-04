package com.okcrm.platform.web.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI / Swagger 文档配置。
 *
 * <p>访问地址：{@code /api/swagger-ui.html}</p>
 */
@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI okCrmOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("OK-CRM API")
                        .description("""
                                多租户多地区 CRM 接口文档。

                                使用方式：
                                1. 调用 `POST /auth/login` 获取 token（需提供租户编码 tenantCode）
                                2. 点击右上角 Authorize，填入 token
                                3. 之后所有请求会自动带上 Authorization 头

                                多租户说明：所有业务接口自动按当前登录租户隔离，
                                平台超管接口位于 `/platform/**` 下，需超管身份。
                                """)
                        .version("1.0.0")
                        .contact(new Contact().name("OK-CRM"))
                        .license(new License().name("Apache-2.0").url("https://www.apache.org/licenses/LICENSE-2.0")))
                .components(new Components().addSecuritySchemes(SECURITY_SCHEME_NAME,
                        new SecurityScheme()
                                .name(SECURITY_SCHEME_NAME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME));
    }
}
