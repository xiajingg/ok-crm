package com.okcrm.modules.customer.internal.controller;

import com.okcrm.modules.customer.internal.dto.FollowUpCreateRequest;
import com.okcrm.modules.customer.internal.dto.FollowUpResponse;
import com.okcrm.modules.customer.internal.service.FollowUpService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 客户跟进记录。
 *
 * <p>新增跟进会同步刷新客户的「最后跟进时间」，公海池回收规则依赖它。</p>
 */
@Tag(name = "客户-跟进记录", description = "跟进记录维护，驱动公海回收的时间口径")
@RestController
@RequestMapping("/customers/{customerId}/follow-ups")
@RequiredArgsConstructor
@RequiresModule(ModuleKeys.CUSTOMER)
public class FollowUpController {

    private final FollowUpService followUpService;

    @Operation(summary = "跟进记录分页")
    @GetMapping
    @PreAuthorize("@perm.has('customer:list')")
    public Result<PageResult<FollowUpResponse>> page(@PathVariable Long customerId,
                                                     @RequestParam(defaultValue = "1") long pageNum,
                                                     @RequestParam(defaultValue = "10") long pageSize) {
        return Result.ok(followUpService.pageByCustomer(customerId, pageNum, pageSize));
    }

    @Operation(summary = "新增跟进记录", description = "会同步刷新客户的最后跟进时间")
    @PostMapping
    @PreAuthorize("@perm.has('customer:followup')")
    public Result<FollowUpResponse> create(@PathVariable Long customerId,
                                           @Valid @RequestBody FollowUpCreateRequest request) {
        // 以路径参数为准，避免 body 与 path 不一致造成越权写入
        FollowUpCreateRequest normalized = new FollowUpCreateRequest(
                customerId, request.type(), request.content(),
                request.followedAt(), request.nextFollowUpAt());
        return Result.ok(followUpService.create(normalized));
    }

    @Operation(summary = "删除跟进记录")
    @DeleteMapping("/{followUpId}")
    @PreAuthorize("@perm.has('customer:followup')")
    public Result<Void> delete(@PathVariable Long customerId, @PathVariable Long followUpId) {
        followUpService.delete(followUpId);
        return Result.ok();
    }
}
