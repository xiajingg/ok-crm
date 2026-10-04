package com.okcrm.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 模块边界约束测试。
 *
 * <p><b>为什么必须第一天就上</b>：单人开发最容易「图省事直接调对方的 Service」，
 * 一旦破例一次，后面就会不断破例，两三周后「模块化」就只剩目录结构了 ——
 * 那时候再想按模块拆开售卖，成本等于重写。</p>
 *
 * <p>这些规则挂在 {@code mvn verify} 上，破坏边界会直接让构建失败。</p>
 *
 * <p>规则约定：跨模块只能依赖对方的 {@code api} 包；
 * {@code internal}（应用层）、{@code infra}（持久层）、{@code domain}（领域模型）
 * 都是模块私有实现。</p>
 *
 * <p>注意：{@code @DisplayName} 只能标在类或方法上，标在字段上会编译失败，
 * 因此这里用 ArchUnit 自己的 {@code .as(...)} 提供可读描述。</p>
 */
@AnalyzeClasses(packages = "com.okcrm", importOptions = ImportOption.DoNotIncludeTests.class)
class ModuleBoundaryTest {

    // ------------------------------------------------------------ 跨模块只能依赖 api

    @ArchTest
    static final ArchRule 租户模块只能通过api被访问 = crossModuleOnlyViaApi("tenant");

    @ArchTest
    static final ArchRule IAM模块只能通过api被访问 = crossModuleOnlyViaApi("iam");

    @ArchTest
    static final ArchRule 客户模块只能通过api被访问 = crossModuleOnlyViaApi("customer");

    @ArchTest
    static final ArchRule 公海池模块只能通过api被访问 = crossModuleOnlyViaApi("pool");

    // ------------------------------------------------------------ 模块内部分层

    @ArchTest
    static final ArchRule 租户模块domain不依赖infra = domainNotDependOnInfra("tenant");

    @ArchTest
    static final ArchRule IAM模块domain不依赖infra = domainNotDependOnInfra("iam");

    @ArchTest
    static final ArchRule 客户模块domain不依赖infra = domainNotDependOnInfra("customer");

    @ArchTest
    static final ArchRule 公海池模块domain不依赖infra = domainNotDependOnInfra("pool");

    // ------------------------------------------------------------ 平台层不得反向依赖业务

    @ArchTest
    static final ArchRule 平台底座不得依赖业务模块 =
            noClasses()
                    .that().resideInAPackage("com.okcrm.platform..")
                    .should().dependOnClassesThat().resideInAPackage("com.okcrm.modules..")
                    .as("平台层必须通过 SPI 反转依赖（如 PermissionProvider / ModuleLicenseProvider）；"
                            + "一旦反向依赖业务模块，业务模块就再也没法独立剥离或售卖");

    // ------------------------------------------------------------ 规则构造

    /**
     * 除本模块之外的任何代码，都不得依赖本模块的 internal / infra / domain。
     */
    private static ArchRule crossModuleOnlyViaApi(String moduleName) {
        String modulePackage = "com.okcrm.modules." + moduleName;
        return noClasses()
                .that().resideOutsideOfPackage(modulePackage + "..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        modulePackage + ".internal..",
                        modulePackage + ".infra..",
                        modulePackage + ".domain..")
                .as("模块 " + moduleName + " 的对外契约只有 api 包；"
                        + "依赖其内部实现会导致模块无法独立演进与按模块售卖");
    }

    private static ArchRule domainNotDependOnInfra(String moduleName) {
        String modulePackage = "com.okcrm.modules." + moduleName;
        return noClasses()
                .that().resideInAPackage(modulePackage + ".domain..")
                .should().dependOnClassesThat().resideInAPackage(modulePackage + ".infra..")
                .as("领域模型不应该知道持久化细节；依赖方向必须是 infra -> domain");
    }
}
