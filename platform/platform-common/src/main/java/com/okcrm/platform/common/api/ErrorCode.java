package com.okcrm.platform.common.api;

import lombok.Getter;

/**
 * 全局错误码。
 *
 * <p>编码约定：0 成功；4xx/5xx 与 HTTP 语义对齐；1000+ 为业务错误。</p>
 */
@Getter
public enum ErrorCode {

    SUCCESS(0, "成功"),

    BAD_REQUEST(400, "请求参数不合法"),
    UNAUTHORIZED(401, "未登录或登录已过期"),
    FORBIDDEN(403, "没有操作权限"),
    NOT_FOUND(404, "资源不存在"),
    CONFLICT(409, "数据冲突"),
    INTERNAL_ERROR(500, "系统内部错误"),

    // ---------- 租户 1000+ ----------
    TENANT_NOT_FOUND(1001, "租户不存在"),
    TENANT_DISABLED(1002, "租户已停用"),
    TENANT_CODE_EXISTS(1003, "租户编码已存在"),
    TENANT_CONTEXT_MISSING(1004, "缺少租户上下文"),

    // ---------- 认证与权限 1100+ ----------
    LOGIN_FAILED(1101, "账号或密码错误"),
    ACCOUNT_DISABLED(1102, "账号已停用"),
    TOKEN_INVALID(1103, "令牌无效"),
    TOKEN_EXPIRED(1104, "令牌已过期"),
    PERMISSION_DENIED(1105, "权限不足"),

    // ---------- 组织与员工 1200+ ----------
    POSITION_NOT_FOUND(1201, "岗位不存在"),
    POSITION_CODE_EXISTS(1202, "岗位编码已存在"),
    POSITION_IN_USE(1203, "岗位已被员工引用，无法删除"),
    EMPLOYEE_NOT_FOUND(1204, "员工不存在"),
    EMPLOYEE_USERNAME_EXISTS(1205, "登录账号已存在"),
    EMPLOYEE_DISABLED(1206, "员工已停用"),

    // ---------- 客户 1300+ ----------
    CUSTOMER_NOT_FOUND(1301, "客户不存在"),
    CUSTOMER_ALREADY_OWNED(1302, "该客户已有归属，不能重复领取"),
    CUSTOMER_NOT_IN_POOL(1303, "该客户不在公海池中"),
    CUSTOMER_IN_POOL(1304, "公海客户请先领取再操作"),
    CUSTOMER_NAME_REQUIRED(1305, "客户名称不能为空"),

    // ---------- 模块授权 1400+ ----------
    MODULE_NOT_LICENSED(1401, "当前租户未购买该模块"),
    MODULE_NOT_FOUND(1402, "模块不存在");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
