package com.okcrm.modules.customer.internal.controller;

import com.okcrm.modules.customer.internal.dto.BatchAssignRequest;
import com.okcrm.modules.customer.internal.dto.CustomerCreateRequest;
import com.okcrm.modules.customer.internal.dto.CustomerResponse;
import com.okcrm.modules.customer.internal.dto.CustomerUpdateRequest;
import com.okcrm.modules.customer.internal.dto.TransferRequest;
import com.okcrm.modules.customer.internal.service.CustomerService;
import com.okcrm.platform.common.api.PageResult;
import com.okcrm.platform.common.api.Result;
import com.okcrm.platform.common.constant.ModuleKeys;
import com.okcrm.platform.security.annotation.RequiresModule;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 客户管理。
 *
 * <p>整个类都要求租户已购买 {@code customer} 模块 —— 这是「按模块独立售卖」
 * 在接口层的闸门，未购买时返回「未购买该模块」而不是 404。</p>
 *
 * <p>数据权限自动生效：只有「全部数据」范围的岗位能看到所有客户，
 * 其它岗位只能看到自己负责的客户。</p>
 */
@Tag(name = "客户-客户管理", description = "客户 CRUD、归属分配与转移")
@RestController
@RequestMapping("/customers")
@RequiredArgsConstructor
@RequiresModule(ModuleKeys.CUSTOMER)
public class CustomerController {

    private final CustomerService customerService;

    @Operation(summary = "客户分页",
            description = "poolOnly=true 时查公海池（不做数据权限过滤，否则公海对谁都不可见）")
    @GetMapping
    @PreAuthorize("@perm.has('customer:list')")
    public Result<PageResult<CustomerResponse>> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) Long ownerId,
            @RequestParam(defaultValue = "false") boolean poolOnly,
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize) {
        return Result.ok(customerService.page(keyword, level, ownerId, poolOnly, pageNum, pageSize));
    }

    @Operation(summary = "客户详情")
    @GetMapping("/{customerId}")
    @PreAuthorize("@perm.has('customer:list')")
    public Result<CustomerResponse> detail(@PathVariable Long customerId) {
        return Result.ok(customerService.detail(customerId));
    }

    @Operation(summary = "新增客户", description = "ownerId 留空则直接进入公海池")
    @PostMapping
    @PreAuthorize("@perm.has('customer:create')")
    public Result<CustomerResponse> create(@Valid @RequestBody CustomerCreateRequest request) {
        return Result.ok(customerService.create(request));
    }

    @Operation(summary = "修改客户", description = "不含归属字段，归属变更请用 transfer 接口")
    @PutMapping("/{customerId}")
    @PreAuthorize("@perm.has('customer:update')")
    public Result<CustomerResponse> update(@PathVariable Long customerId,
                                           @Valid @RequestBody CustomerUpdateRequest request) {
        return Result.ok(customerService.update(customerId, request));
    }

    @Operation(summary = "删除客户")
    @DeleteMapping("/{customerId}")
    @PreAuthorize("@perm.has('customer:delete')")
    public Result<Void> delete(@PathVariable Long customerId) {
        customerService.delete(customerId);
        return Result.ok();
    }

    @Operation(summary = "分配/转移客户归属", description = "每一次变更都会写入公海池流转日志")
    @PutMapping("/{customerId}/transfer")
    @PreAuthorize("@perm.has('customer:transfer')")
    public Result<Void> transfer(@PathVariable Long customerId,
                                 @Valid @RequestBody TransferRequest request) {
        customerService.transfer(customerId, request.ownerId(), request.remark());
        return Result.ok();
    }

    @Operation(summary = "批量分配客户")
    @PostMapping("/batch-assign")
    @PreAuthorize("@perm.has('customer:transfer')")
    public Result<Integer> batchAssign(@Valid @RequestBody BatchAssignRequest request) {
        return Result.ok(customerService.batchAssign(request.customerIds(), request.ownerId(), "批量分配"));
    }

    @Operation(summary = "退回公海池")
    @PutMapping("/{customerId}/release")
    @PreAuthorize("@perm.has('customer:transfer')")
    public Result<Void> release(@PathVariable Long customerId,
                                @RequestParam(required = false) String reason) {
        customerService.releaseToPoolByUser(customerId, reason == null ? "手动退回" : reason);
        return Result.ok();
    }
}
