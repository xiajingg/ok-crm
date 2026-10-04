package com.okcrm.modules.iam.internal.dto;

import com.okcrm.platform.common.enums.EnableStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 更新员工请求。账号（username）不可修改。
 */
public record EmployeeUpdateRequest(

        /** 留空表示不修改密码 */
        @Size(min = 6, max = 64, message = "密码长度需在 6-64 位之间")
        String password,

        @Size(max = 64, message = "姓名最长 64 位")
        String realName,

        @Size(max = 32, message = "手机号最长 32 位")
        String phone,

        @Email(message = "邮箱格式不正确")
        @Size(max = 128, message = "邮箱最长 128 位")
        String email,

        EnableStatus status,

        @Size(max = 255, message = "备注最长 255 位")
        String remark,

        /** 传 null 表示不改岗位；传空数组表示解除全部岗位 */
        List<Long> positionIds
) {
}
