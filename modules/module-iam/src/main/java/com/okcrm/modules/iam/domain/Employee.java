package com.okcrm.modules.iam.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.okcrm.platform.common.entity.TenantEntity;
import com.okcrm.platform.common.enums.EnableStatus;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 员工（同时是系统登录账号）。
 *
 * <p>登录账号在租户内唯一：不同租户可以有同名账号，互不干扰。</p>
 */
@Getter
@Setter
@TableName("sys_employee")
public class Employee extends TenantEntity {

    /** 登录账号，租户内唯一 */
    private String username;

    /** BCrypt 密码散列，永不出现在任何响应里 */
    @JsonIgnore
    private String password;

    /** 姓名 */
    private String realName;

    private String phone;

    private String email;

    private EnableStatus status;

    private String remark;

    private LocalDateTime lastLoginAt;
}
