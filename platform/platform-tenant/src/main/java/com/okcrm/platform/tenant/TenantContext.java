package com.okcrm.platform.tenant;

import com.alibaba.ttl.TransmittableThreadLocal;
import com.okcrm.platform.common.api.ErrorCode;
import com.okcrm.platform.common.exception.BizException;

import java.util.function.Supplier;

/**
 * 租户上下文。整个系统唯一的租户信息出口，业务代码不要自己去解析 token 或请求头。
 *
 * <p>使用 {@link TransmittableThreadLocal} 而非普通 ThreadLocal：普通 ThreadLocal 在
 * 线程池（{@code @Async}、定时任务、CompletableFuture）中会丢失，导致跨租户串数据。
 * 配合 {@link TenantContextTaskDecorator} 可覆盖 Spring 的 {@code @Async} 场景。</p>
 */
public final class TenantContext {

    private static final TransmittableThreadLocal<TenantInfo> HOLDER = new TransmittableThreadLocal<>();

    private TenantContext() {
    }

    public static void set(TenantInfo info) {
        HOLDER.set(info);
    }

    public static TenantInfo get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }

    /**
     * 当前租户 ID。平台超管返回 null。
     */
    public static Long getTenantId() {
        TenantInfo info = HOLDER.get();
        return info == null ? null : info.tenantId();
    }

    /**
     * 取租户 ID，取不到直接抛业务异常。用于所有「必须有租户」的业务入口。
     */
    public static Long requireTenantId() {
        Long tenantId = getTenantId();
        if (tenantId == null) {
            throw BizException.of(ErrorCode.TENANT_CONTEXT_MISSING);
        }
        return tenantId;
    }

    public static boolean isPlatformAdmin() {
        TenantInfo info = HOLDER.get();
        return info != null && info.platformAdmin();
    }

    public static boolean isPresent() {
        return HOLDER.get() != null;
    }

    /**
     * 以指定租户身份执行，执行完恢复原上下文。
     *
     * <p><b>定时任务必须用这个包一层</b>：公海池回收、报表统计等跨租户批处理任务
     * 在遍历租户时，若忘记设置上下文，轻则查不到数据，重则跨租户误写。</p>
     */
    public static void runAs(Long tenantId, Runnable runnable) {
        callAs(tenantId, () -> {
            runnable.run();
            return null;
        });
    }

    /**
     * 以指定租户身份执行并返回结果，执行完恢复原上下文。
     */
    public static <T> T callAs(Long tenantId, Supplier<T> supplier) {
        TenantInfo previous = HOLDER.get();
        return callAs(TenantInfo.of(tenantId,
                previous == null ? null : previous.tenantCode(),
                previous == null ? null : previous.region()), supplier);
    }

    /**
     * 以完整的租户信息执行并返回结果（需要 tenantCode 时用这个重载）。
     */
    public static <T> T callAs(TenantInfo info, Supplier<T> supplier) {
        TenantInfo previous = HOLDER.get();
        try {
            HOLDER.set(info);
            return supplier.get();
        } finally {
            if (previous == null) {
                HOLDER.remove();
            } else {
                HOLDER.set(previous);
            }
        }
    }

    /**
     * 以平台超管身份执行（跨租户）。
     */
    public static <T> T callAsPlatformAdmin(Supplier<T> supplier) {
        TenantInfo previous = HOLDER.get();
        try {
            HOLDER.set(TenantInfo.ofPlatformAdmin());
            return supplier.get();
        } finally {
            if (previous == null) {
                HOLDER.remove();
            } else {
                HOLDER.set(previous);
            }
        }
    }
}
