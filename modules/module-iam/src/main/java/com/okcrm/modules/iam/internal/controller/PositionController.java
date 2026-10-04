package com.okcrm.modules.iam.internal.controller;

import com.okcrm.modules.iam.internal.dto.PositionCreateRequest;
import com.okcrm.modules.iam.internal.dto.PositionResponse;
import com.okcrm.modules.iam.internal.dto.PositionUpdateRequest;
import com.okcrm.modules.iam.internal.service.PositionService;
import com.okcrm.platform.common.api.Result;
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

import java.util.List;

/**
 * 岗位管理（岗位即角色）。
 */
@Tag(name = "组织-岗位管理", description = "岗位即角色：岗位承载权限与数据范围")
@RestController
@RequestMapping("/positions")
@RequiredArgsConstructor
public class PositionController {

    private final PositionService positionService;

    @Operation(summary = "岗位列表")
    @GetMapping
    @PreAuthorize("@perm.has('iam:position:list')")
    public Result<List<PositionResponse>> list(@RequestParam(required = false) String keyword) {
        return Result.ok(positionService.list(keyword));
    }

    @Operation(summary = "岗位详情（含已授权限）")
    @GetMapping("/{positionId}")
    @PreAuthorize("@perm.has('iam:position:list')")
    public Result<PositionResponse> detail(@PathVariable Long positionId) {
        return Result.ok(positionService.detail(positionId));
    }

    @Operation(summary = "新增岗位")
    @PostMapping
    @PreAuthorize("@perm.has('iam:position:create')")
    public Result<PositionResponse> create(@Valid @RequestBody PositionCreateRequest request) {
        return Result.ok(positionService.create(request));
    }

    @Operation(summary = "修改岗位")
    @PutMapping("/{positionId}")
    @PreAuthorize("@perm.has('iam:position:update')")
    public Result<PositionResponse> update(@PathVariable Long positionId,
                                           @Valid @RequestBody PositionUpdateRequest request) {
        return Result.ok(positionService.update(positionId, request));
    }

    @Operation(summary = "删除岗位", description = "被员工引用的岗位不允许删除")
    @DeleteMapping("/{positionId}")
    @PreAuthorize("@perm.has('iam:position:delete')")
    public Result<Void> delete(@PathVariable Long positionId) {
        positionService.delete(positionId);
        return Result.ok();
    }

    @Operation(summary = "单独调整岗位权限", description = "权限树保存；变更后自动失效该岗位下员工的权限缓存")
    @PutMapping("/{positionId}/permissions")
    @PreAuthorize("@perm.has('iam:position:update')")
    public Result<Void> assignPermissions(@PathVariable Long positionId,
                                          @RequestBody List<String> permissionCodes) {
        positionService.assignPermissions(positionId, permissionCodes);
        return Result.ok();
    }
}
