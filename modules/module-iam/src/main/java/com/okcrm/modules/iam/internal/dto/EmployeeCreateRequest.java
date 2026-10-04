package com.okcrm.modules.iam.internal.dto;

import com.okcrm.platform.common.enums.EnableStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 新增员工请求。员工与岗位的关联通过 {@link #positionIds} 一次性提交。
 */
public record EmployeeCreateRequest(

        @NotBlank(message = "登录账号不能为空")
        @Size(max = 64, message = "登录账号最长 64 位")
        String username,

        @NotBlank(message = "初始密码不能为空")
        @Size(min = 6, max = 64, message = "密码长度需在 6-64 位之间")
        String password,

        @NotBlank(message = "姓名不能为空")
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

        /** 关联的岗位 ID 列表；支持一人多岗 */
        List<Long> positionIds
) {
}
