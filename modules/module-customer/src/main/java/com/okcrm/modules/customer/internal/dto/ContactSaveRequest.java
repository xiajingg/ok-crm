package com.okcrm.modules.customer.internal.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 新增/修改联系人请求（新增与修改字段一致，共用一个 record）。
 */
public record ContactSaveRequest(

        @NotBlank(message = "联系人姓名不能为空")
        @Size(max = 64, message = "联系人姓名最长 64 位")
        String name,

        @Size(max = 64)
        String position,

        @Size(max = 32)
        String phone,

        @Email(message = "邮箱格式不正确")
        @Size(max = 128)
        String email,

        Boolean primaryContact,

        @Size(max = 255)
        String remark
) {
}
