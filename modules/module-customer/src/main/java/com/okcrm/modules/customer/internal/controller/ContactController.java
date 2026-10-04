package com.okcrm.modules.customer.internal.controller;

import com.okcrm.modules.customer.internal.dto.ContactResponse;
import com.okcrm.modules.customer.internal.dto.ContactSaveRequest;
import com.okcrm.modules.customer.internal.service.ContactService;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 客户联系人。
 */
@Tag(name = "客户-联系人", description = "客户下的联系人维护")
@RestController
@RequestMapping("/customers/{customerId}/contacts")
@RequiredArgsConstructor
@RequiresModule(ModuleKeys.CUSTOMER)
public class ContactController {

    private final ContactService contactService;

    @Operation(summary = "联系人列表")
    @GetMapping
    @PreAuthorize("@perm.has('customer:list')")
    public Result<List<ContactResponse>> list(@PathVariable Long customerId) {
        return Result.ok(contactService.listByCustomer(customerId));
    }

    @Operation(summary = "新增联系人")
    @PostMapping
    @PreAuthorize("@perm.has('customer:update')")
    public Result<ContactResponse> create(@PathVariable Long customerId,
                                          @Valid @RequestBody ContactSaveRequest request) {
        return Result.ok(contactService.create(customerId, request));
    }

    @Operation(summary = "修改联系人")
    @PutMapping("/{contactId}")
    @PreAuthorize("@perm.has('customer:update')")
    public Result<ContactResponse> update(@PathVariable Long customerId,
                                          @PathVariable Long contactId,
                                          @Valid @RequestBody ContactSaveRequest request) {
        return Result.ok(contactService.update(contactId, request));
    }

    @Operation(summary = "删除联系人")
    @DeleteMapping("/{contactId}")
    @PreAuthorize("@perm.has('customer:update')")
    public Result<Void> delete(@PathVariable Long customerId, @PathVariable Long contactId) {
        contactService.delete(contactId);
        return Result.ok();
    }
}
