package com.okcrm.platform.security.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明接口所属的可售卖模块。未授权时抛业务异常（错误码 1401「当前租户未购买该模块」）。
 *
 * <pre>{@code
 * @RequiresModule(ModuleKeys.POOL)
 * @RestController
 * public class PoolController { ... }
 * }</pre>
 *
 * <p><b>为什么不用 {@code @PreAuthorize("@module.licensed('pool')")}</b>：
 * Spring Security 的规则是「方法级注解覆盖类级注解」，
 * 一旦方法上写了 {@code @PreAuthorize("@perm.has('pool:list')")}，
 * 类上的模块授权判断就会被整条丢掉 —— 于是模块闸门形同虚设。
 * 这个坑非常隐蔽：接口全部正常，只是「没买也能用」。</p>
 *
 * <p>本注解由 {@code ModuleLicenseInterceptor} 在进入 Controller 之前检查，
 * 与 {@code @PreAuthorize} 是两个独立的关注点，互相不会覆盖：</p>
 * <ul>
 *   <li>{@code @RequiresModule} —— 这个租户买没买这个模块（商业授权）</li>
 *   <li>{@code @PreAuthorize} —— 这个人有没有这个权限（岗位权限）</li>
 * </ul>
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface RequiresModule {

    /** 模块标识，取值见 {@code ModuleKeys} */
    String value();
}
