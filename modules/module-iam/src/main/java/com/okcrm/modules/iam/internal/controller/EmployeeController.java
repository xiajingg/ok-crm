package com.okcrm.modules.iam.internal.controller;

import com.okcrm.modules.iam.api.EmployeeBrief;
import com.okcrm.modules.iam.internal.dto.EmployeeCreateRequest;
import com.okcrm.modules.iam.internal.dto.EmployeeResponse;
import com.okcrm.modules.iam.internal.dto.EmployeeUpdateRequest;
import com.okcrm.modules.iam.internal.service.EmployeeService;
import com.okcrm.platform.common.api.PageResult;
import com.okcrm.platform.common.api.Result;
import com.okcrm.platform.common.enums.EnableStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
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

import java.util.List;

/**
 * 员工管理（员工 = 人 + 登录账号）。
 */
@Tag(name = "组织-员工管理", description = "员工 CRUD、关联岗位、重置密码")
@RestController
@RequestMapping("/employees")
@RequiredArgsConstructor
public class EmployeeController {

    private final EmployeeService employeeService;

    @Operation(summary = "员工分页列表")
    @GetMapping
    @PreAuthorize("@perm.has('iam:employee:list')")
    public Result<PageResult<EmployeeResponse>> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) EnableStatus status,
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize) {
        return Result.ok(employeeService.page(keyword, status, pageNum, pageSize));
    }

    @Operation(summary = "员工详情")
    @GetMapping("/{employeeId}")
    @PreAuthorize("@perm.has('iam:employee:list')")
    public Result<EmployeeResponse> detail(@PathVariable Long employeeId) {
        return Result.ok(employeeService.detail(employeeId));
    }

    /**
     * 下拉选项：客户分配、负责人选择等场景使用。
     * 只要求登录，不要求员工管理权限 —— 销售也需要知道同事是谁。
     */
    @Operation(summary = "在职员工选项（用于负责人下拉）")
    @GetMapping("/options")
    public Result<List<EmployeeBrief>> options() {
        return Result.ok(employeeService.listEnabled());
    }

    @Operation(summary = "新增员工")
    @PostMapping
    @PreAuthorize("@perm.has('iam:employee:create')")
    public Result<EmployeeResponse> create(@Valid @RequestBody EmployeeCreateRequest request) {
        return Result.ok(employeeService.create(request));
    }

    @Operation(summary = "修改员工", description = "positionIds 传 null 表示不改岗位，传空数组表示解除全部岗位")
    @PutMapping("/{employeeId}")
    @PreAuthorize("@perm.has('iam:employee:update')")
    public Result<EmployeeResponse> update(@PathVariable Long employeeId,
                                           @Valid @RequestBody EmployeeUpdateRequest request) {
        return Result.ok(employeeService.update(employeeId, request));
    }

    @Operation(summary = "删除员工")
    @DeleteMapping("/{employeeId}")
    @PreAuthorize("@perm.has('iam:employee:delete')")
    public Result<Void> delete(@PathVariable Long employeeId) {
        employeeService.delete(employeeId);
        return Result.ok();
    }

    @Operation(summary = "启用/停用员工")
    @PutMapping("/{employeeId}/status")
    @PreAuthorize("@perm.has('iam:employee:update')")
    public Result<Void> changeStatus(@PathVariable Long employeeId, @RequestParam boolean enabled) {
        employeeService.changeStatus(employeeId, enabled);
        return Result.ok();
    }

    @Operation(summary = "重置密码")
    @PutMapping("/{employeeId}/password")
    @PreAuthorize("@perm.has('iam:employee:update')")
    public Result<Void> resetPassword(@PathVariable Long employeeId,
                                      @RequestParam @Size(min = 6, max = 64) String password) {
        employeeService.resetPassword(employeeId, password);
        return Result.ok();
    }
}
