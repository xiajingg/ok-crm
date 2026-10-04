package com.okcrm.modules.pool.internal.controller;

import com.okcrm.modules.customer.api.CustomerBrief;
import com.okcrm.modules.pool.domain.PoolRecycleSettings;
import com.okcrm.modules.pool.internal.dto.PoolAssignRequest;
import com.okcrm.modules.pool.internal.dto.PoolLogResponse;
import com.okcrm.modules.pool.internal.dto.PoolSettingsRequest;
import com.okcrm.modules.pool.internal.dto.RecycleResult;
import com.okcrm.modules.pool.internal.service.PoolRecycleService;
import com.okcrm.modules.pool.internal.service.PoolService;
import com.okcrm.platform.common.api.PageResult;
import com.okcrm.platform.common.api.Result;
import com.okcrm.platform.common.constant.ModuleKeys;
import com.okcrm.platform.security.annotation.RequiresModule;
import com.okcrm.platform.tenant.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 公海池。
 *
 * <p>整个类要求租户已购买 {@code pool} 模块。没买基础版的客户调用这些接口
 * 会得到「当前租户未购买该模块」，而不是 404 —— 这对销售演示很重要，
 * 「功能存在但你没买」比「查无此接口」更容易促成续费。</p>
 */
@Tag(name = "客户-公海池", description = "公海领取、指派、退回、流转日志与回收规则")
@RestController
@RequestMapping("/pool")
@RequiredArgsConstructor
@RequiresModule(ModuleKeys.POOL)
public class PoolController {

    private final PoolService poolService;
    private final PoolRecycleService poolRecycleService;

    @Operation(summary = "公海池客户列表", description = "不做数据权限过滤，所有员工都能看到公海客户")
    @GetMapping("/customers")
    @PreAuthorize("@perm.has('pool:list')")
    public Result<PageResult<CustomerBrief>> page(@RequestParam(required = false) String keyword,
                                                  @RequestParam(defaultValue = "1") long pageNum,
                                                  @RequestParam(defaultValue = "10") long pageSize) {
        return Result.ok(poolService.page(keyword, pageNum, pageSize));
    }

    @Operation(summary = "领取客户", description = "领取前校验单人持有上限；已被他人领走会明确报错")
    @PostMapping("/customers/{customerId}/claim")
    @PreAuthorize("@perm.has('pool:claim')")
    public Result<Void> claim(@PathVariable Long customerId) {
        poolService.claim(customerId);
        return Result.ok();
    }

    @Operation(summary = "指派客户给某员工")
    @PostMapping("/assign")
    @PreAuthorize("@perm.has('pool:assign')")
    public Result<Void> assign(@Valid @RequestBody PoolAssignRequest request) {
        poolService.assign(request.customerId(), request.employeeId(), request.remark());
        return Result.ok();
    }

    @Operation(summary = "退回公海池")
    @PostMapping("/customers/{customerId}/release")
    @PreAuthorize("@perm.has('pool:release')")
    public Result<Void> release(@PathVariable Long customerId,
                                @RequestParam(required = false) String reason) {
        poolService.release(customerId, reason);
        return Result.ok();
    }

    @Operation(summary = "流转日志", description = "不传 customerId 时查询本租户全部流转记录")
    @GetMapping("/logs")
    @PreAuthorize("@perm.has('pool:list')")
    public Result<PageResult<PoolLogResponse>> logs(@RequestParam(required = false) Long customerId,
                                                    @RequestParam(defaultValue = "1") long pageNum,
                                                    @RequestParam(defaultValue = "20") long pageSize) {
        return Result.ok(poolService.logs(customerId, pageNum, pageSize));
    }

    @Operation(summary = "查询公海回收规则")
    @GetMapping("/settings")
    @PreAuthorize("@perm.has('pool:list')")
    public Result<PoolRecycleSettings> settings() {
        return Result.ok(poolService.settings());
    }

    @Operation(summary = "修改公海回收规则", description = "回收天数与口径按租户可配，避免为单个客户改代码")
    @PutMapping("/settings")
    @PreAuthorize("@perm.has('pool:settings:update')")
    public Result<Void> updateSettings(@Valid @RequestBody PoolSettingsRequest request) {
        poolService.updateSettings(request);
        return Result.ok();
    }

    @Operation(summary = "手工触发本租户的回收", description = "用于验证规则是否符合预期，不必等到凌晨")
    @PostMapping("/recycle/run")
    @PreAuthorize("@perm.has('pool:settings:update')")
    public Result<Integer> runRecycle() {
        return Result.ok(poolRecycleService.recycleTenant(TenantContext.requireTenantId()));
    }

    @Operation(summary = "手工触发全部租户回收（平台运维用）",
            description = "需要平台超管身份；带分布式锁，重复调用会被跳过")
    @PostMapping("/recycle/run-all")
    @PreAuthorize("@authz.platformAdmin()")
    public Result<RecycleResult> runRecycleAll() {
        return Result.ok(poolRecycleService.recycleAllTenants());
    }
}
