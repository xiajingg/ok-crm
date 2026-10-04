package com.okcrm.platform.mybatis.tenant;

import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.okcrm.platform.common.api.ErrorCode;
import com.okcrm.platform.common.exception.BizException;
import com.okcrm.platform.tenant.TenantContext;
import com.okcrm.platform.tenant.TenantIsolationStrategy;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;

/**
 * MyBatis-Plus 租户处理器：把 {@code TenantContext} 翻译成 SQL 中的 tenant_id 条件。
 *
 * <p>三类情况：</p>
 * <ol>
 *   <li><b>普通租户请求</b>：追加 {@code tenant_id = 当前租户}</li>
 *   <li><b>全局表</b>（sys_tenant / sys_module / sys_permission ...）：不加条件，
 *       由 {@link TenantIsolationStrategy#supportsTable(String)} 判定</li>
 *   <li><b>平台超管跨租户</b>：不加任何自动条件。<b>代价是超管相关查询必须自己写
 *       tenant_id 过滤</b>，这是有意为之 —— 平台超管的 Mapper 与租户内 Mapper 物理分开，
 *       避免「不小心拿到全租户数据」</li>
 * </ol>
 *
 * <p>取不到租户上下文时直接抛异常（fail-fast）。这条很关键：静默返回空数据会让
 * 「定时任务忘了设置租户上下文」这类 bug 变成线上疑难杂症。</p>
 */
public class TenantLineHandlerImpl implements TenantLineHandler {

    private final TenantIsolationStrategy isolationStrategy;

    public TenantLineHandlerImpl(TenantIsolationStrategy isolationStrategy) {
        this.isolationStrategy = isolationStrategy;
    }

    @Override
    public Expression getTenantId() {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw BizException.of(ErrorCode.TENANT_CONTEXT_MISSING);
        }
        return new LongValue(tenantId);
    }

    @Override
    public String getTenantIdColumn() {
        return "tenant_id";
    }

    @Override
    public boolean ignoreTable(String tableName) {
        // 平台超管跨租户操作：不做自动隔离，查询条件由超管专用 Mapper 显式书写
        if (TenantContext.isPlatformAdmin()) {
            return true;
        }
        return !isolationStrategy.supportsTable(tableName);
    }
}
