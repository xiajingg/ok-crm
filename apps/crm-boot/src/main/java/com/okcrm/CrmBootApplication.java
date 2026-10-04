package com.okcrm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * OK-CRM 启动器。
 *
 * <p>这是整个系统唯一的 {@code main} 入口。业务模块以 Maven 依赖的方式被组装进来，
 * 通过 {@code edition-*} profile 可以产出「只含已购买模块」的交付包。</p>
 *
 * <p>MyBatis Mapper 统一用 {@code @Mapper} 注解标记，由 MyBatis Spring Boot Starter
 * 在本包（{@code com.okcrm}）下自动扫描，无需 @MapperScan。</p>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableCaching
@EnableAsync
@EnableScheduling
public class CrmBootApplication {

    public static void main(String[] args) {
        SpringApplication.run(CrmBootApplication.class, args);
    }
}
