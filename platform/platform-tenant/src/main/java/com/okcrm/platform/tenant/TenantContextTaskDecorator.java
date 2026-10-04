package com.okcrm.platform.tenant;

import org.springframework.core.task.TaskDecorator;
import org.springframework.lang.NonNull;

/**
 * 把租户上下文传递给 Spring 异步线程池。
 *
 * <p>注意：本装饰器只覆盖「提交任务的那一刻」的上下文快照。
 * 若任务内部还需要切换租户（例如按租户分片跑批），
 * 必须显式使用 {@link TenantContext#runAs(Long, Runnable)}。</p>
 */
public class TenantContextTaskDecorator implements TaskDecorator {

    @Override
    @NonNull
    public Runnable decorate(@NonNull Runnable runnable) {
        TenantInfo captured = TenantContext.get();
        return () -> {
            TenantInfo previous = TenantContext.get();
            try {
                TenantContext.set(captured);
                runnable.run();
            } finally {
                if (previous == null) {
                    TenantContext.clear();
                } else {
                    TenantContext.set(previous);
                }
            }
        };
    }
}
