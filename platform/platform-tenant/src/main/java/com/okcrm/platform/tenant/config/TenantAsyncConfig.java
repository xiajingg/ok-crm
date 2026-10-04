package com.okcrm.platform.tenant.config;

import com.okcrm.platform.tenant.TenantContextTaskDecorator;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.lang.NonNull;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步线程池配置。
 *
 * <p>关键点是挂上 {@link TenantContextTaskDecorator}：否则 {@code @Async} 方法里
 * {@code TenantContext} 会丢失，表现为「异步逻辑查不到数据」或更糟的「写到别的租户」。</p>
 */
@Configuration
public class TenantAsyncConfig implements AsyncConfigurer {

    @Override
    @NonNull
    public Executor getAsyncExecutor() {
        TaskDecorator tenantDecorator = new TenantContextTaskDecorator();

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(200);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("okcrm-async-");
        executor.setTaskDecorator(tenantDecorator);
        // 队列满时由调用线程执行，避免静默丢任务
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
