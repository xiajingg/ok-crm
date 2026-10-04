package com.okcrm.platform.common.constant;

/**
 * 可售卖模块标识。
 *
 * <p>放在平台层是因为它是一个<b>全局约定</b>而不是某个业务模块的私有概念：
 * 每个 {@code moduleKey} 必须与一个 Maven 业务模块一一对应，
 * 并且是「编译期裁剪 / 运行期授权 / 前端菜单分组」三者共用的同一个键。</p>
 *
 * <p>放在这里还有个现实原因：客户模块、公海池模块都要用
 * {@code @RequiresModule(ModuleKeys.POOL)} 声明依赖的模块，
 * 若把常量放在租户模块里，会让这两个模块多出一条对本不相关模块的依赖。</p>
 *
 * <p>新增可售卖模块时的三步：</p>
 * <ol>
 *   <li>这里加常量</li>
 *   <li>{@code sys_module} 表加一行（Flyway 脚本）</li>
 *   <li>该模块的权限点 {@code module_key} 填同一个值</li>
 * </ol>
 */
public final class ModuleKeys {

    private ModuleKeys() {
    }

    /** 租户与地区管理（基础包） */
    public static final String TENANT = "tenant";

    /** 组织与权限：岗位、员工（基础包） */
    public static final String IAM = "iam";

    /** 客户管理与归属 */
    public static final String CUSTOMER = "customer";

    /** 公海池与自动回收 */
    public static final String POOL = "pool";
}
