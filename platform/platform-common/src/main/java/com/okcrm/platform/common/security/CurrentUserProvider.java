package com.okcrm.platform.common.security;

/**
 * 当前登录用户提供者。
 *
 * <p>放在 common 是为了打断循环依赖：审计字段自动填充（platform-mybatis）需要知道
 * 「谁在操作」，但持久层不应该依赖安全模块。真正的实现由 platform-security 提供，
 * 读取 SecurityContext。</p>
 */
public interface CurrentUserProvider {

    /**
     * 当前登录员工 ID；未登录（如定时任务、系统初始化）返回 null。
     */
    Long currentUserId();
}
