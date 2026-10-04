# 模块划分与按模块售卖

这份文档回答两件事：**系统是怎么按模块拆开的**，以及**拆开之后怎么按模块卖钱**。

---

## 一、模块清单

### 平台底座（不单独售卖）

| 模块 | 职责 | 关键内容 |
|---|---|---|
| `platform-common` | 通用能力 | 统一响应体 `Result`、错误码 `ErrorCode`、业务异常、实体基类（`BaseEntity` / `TenantEntity`）、模块标识常量 `ModuleKeys` |
| `platform-tenant` | 租户上下文 | `TenantContext`（跨线程传递）、`TenantIsolationStrategy` SPI、异步上下文装饰器 |
| `platform-mybatis` | 持久层底座 | MyBatis-Plus 装配、`TenantLineInnerInterceptor` 租户拦截器、审计字段自动填充 |
| `platform-security` | 认证与授权 | JWT、`PermissionChecker`（权限）、`ModuleChecker`（模块授权）、`DataScopeChecker`（数据权限）、`@RequiresModule` 拦截器、安全异常处理 |
| `platform-web` | Web 底座 | 全局异常处理、OpenAPI 文档、跨域 |

### 可售卖业务模块

| 模块 | 售卖定位 | 核心能力 | 依赖 |
|---|---|---|---|
| `module-tenant` | 基础包（必需） | 租户与地区、租户级配置、模块授权台账 | 仅平台底座 |
| `module-iam` | 基础包（必需） | 岗位（即角色）、权限点、员工、多岗位兼任 | `module-tenant` |
| `module-customer` | 主产品 | 客户、联系人、跟进记录、客户归属与转移 | `module-iam` |
| `module-pool` | 增值模块 | 公海池领取/指派/退回、流转日志、超期自动回收 | `module-customer`、`module-iam` |

依赖关系是一张**有向无环图**，没有任何循环依赖 —— 这是模块能被单独剥离的前提，也由架构测试守着。

```
module-pool ──▶ module-customer ──▶ module-iam ──▶ module-tenant ──▶ platform-*
```

---

## 二、模块之间怎么解耦

### 规则

每个业务模块内部统一四层，**目录即边界**：

```
com.okcrm.modules.<module>
├── api/        ← 其它模块唯一可以依赖的包
├── internal/   ← 应用层（Controller / AppService / DTO），模块私有
├── domain/     ← 领域模型（实体 / 枚举 / 规则），模块私有
└── infra/      ← 持久层（Mapper），模块私有
```

跨模块通信**只有两种合法方式**：

**1. 领域事件** —— 用于「某事发生后触发的后续反应」，发送方不需要知道谁在听

| 事件 | 发布方 | 监听方 | 做什么 |
|---|---|---|---|
| `TenantCreatedEvent` | `module-tenant` | `module-iam` | 初始化三个默认岗位并分配权限、创建默认管理员账号 |
| `CustomerOwnershipChangedEvent` | `module-customer` | `module-pool` | 写入公海池流转日志 |

**2. 模块 `api` 接口同步调用** —— 用于「必须立刻拿到结果」

| 接口 | 提供方 | 消费方 | 场景 |
|---|---|---|---|
| `EmployeeQueryService` | `module-iam` | `module-customer`、`module-pool` | 分配客户前校验负责人是否存在且在岗 |
| `CustomerQueryService` / `CustomerOwnershipService` | `module-customer` | `module-pool` | 公海池查询与改变客户归属 |
| `TenantQueryService` / `TenantConfigQueryService` | `module-tenant` | 全部业务模块 | 读租户信息与租户配置 |
| `PermissionProvider` / `ModuleLicenseProvider` | 由业务模块实现 | `platform-security` | 反转依赖：平台层通过 SPI 使用业务能力 |

> 最后一行是关键设计：`platform-security` **不能**依赖业务模块（否则业务模块永远无法独立剥离）。所以权限计算与模块授权的能力由业务模块实现，平台层只声明 SPI 接口。

### 强制手段

`apps/crm-boot/src/test/java/com/okcrm/architecture/ModuleBoundaryTest.java` 里 9 条 ArchUnit 规则，`mvn test` 时执行：

1. 每个业务模块的 `internal` / `infra` / `domain` 都不得被模块外的代码依赖（4 条）
2. 每个业务模块的 `domain` 不得依赖自己的 `infra`（4 条）
3. `com.okcrm.platform..` 不得依赖 `com.okcrm.modules..`（1 条）

破坏边界 → 构建失败。这条防线必须第一天就上：单人开发最容易「图省事直接调对方的 Service」，破例一次就会不断破例，两三周后模块化就只剩目录结构了。

---

## 三、按模块售卖的落地

「按模块售卖」需要三层配合，任何一层缺失都会漏。

### 第 1 层：编译期裁剪（决定「客户拿到的包里有什么」）

`apps/crm-boot/pom.xml` 用 Maven profile 决定打包哪些业务模块：

```bash
# 完整版（默认，activeByDefault）
mvn -pl apps/crm-boot -am package

# 基础版：租户 + 组织 + 客户，不含公海池
mvn -pl apps/crm-boot -am package -Pedition-basic

# 专业版：基础版 + 公海池
mvn -pl apps/crm-boot -am package -Pedition-pro
```

原理：`full` profile 标了 `activeByDefault`，**显式激活任意 edition 会自动关闭它**，因此不需要写任何「排除」逻辑。

交付给只买了基础版的客户时，包里**物理上不存在** `module-pool` 的 class —— 不是「藏起来」，是真的没有。

### 第 2 层：运行期授权（决定「已部署的实例对哪些租户开放」）

SaaS 形态下多个租户共用一个部署包，靠 `sys_tenant_module` 表区分各自买了什么：

```
sys_tenant_module(tenant_id, module_key, status, expire_date)
```

接口层的闸门是 `@RequiresModule` 注解 + `ModuleLicenseInterceptor`：

```java
@RequiresModule(ModuleKeys.POOL)
@RestController
@RequestMapping("/pool")
public class PoolController { ... }
```

未购买时返回 **1401「当前租户未购买该模块」**，而不是 404 或 500 —— 这对销售演示很重要：「功能存在但你没买」比「查无此接口」更容易促成续费。

> **为什么不直接用 `@PreAuthorize("@module.licensed('pool')")`？**
> 因为 Spring Security 的规则是「**方法级注解覆盖类级注解**」。
> 只要方法上写了 `@PreAuthorize("@perm.has('pool:list')")`，类上的模块授权判断就会被整条丢掉 ——
> 模块闸门形同虚设。这个坑极其隐蔽：接口全部正常，只是「没买也能用」。
> 所以模块授权与角色权限被拆成两套互不覆盖的机制：前者用 MVC 拦截器，后者用 `@PreAuthorize`。

### 第 3 层：前端菜单（决定「客户看到什么」）

前端菜单**不写死**，由后端 `/api/auth/menus` 按「已购模块 ∩ 岗位权限」动态下发：

- 未购买的模块 → 后端不下发对应菜单 → 前端连路由都不会注册
- 岗位没权限的菜单 → 同样不下发

因此**不需要维护两套前端代码**，这是模块能真正拆开卖的关键一环。

---

## 四、新增一个可售卖模块的完整步骤

假设要新增「报表看板」模块 `module-report`：

1. **建模块**：`modules/module-report`，`pom.xml` 依赖所需模块，父 POM 的 `<modules>` 里登记
2. **注册模块标识**：`platform-common` 的 `ModuleKeys` 加常量 `REPORT = "report"`
3. **登记价目表**：新增 Flyway 脚本往 `sys_module` 插一行（`module_key = 'report'`）
4. **定义权限点**：往 `sys_permission` 插入该模块的菜单与按钮，`module_key` 填 `report`
5. **写接口**：Controller 类上标 `@RequiresModule(ModuleKeys.REPORT)`，方法上标 `@PreAuthorize("@perm.has('report:xxx')")`
6. **加 profile**：`apps/crm-boot/pom.xml` 里给需要包含该模块的 edition 补上依赖
7. **给默认岗位授权**：按需调整 `DefaultPermissionTemplates`（新租户默认是否包含该模块）
8. **跑测试**：`mvn test` —— 架构测试会检查你的模块边界是否干净

---

## 五、模块授权台账的使用

平台超管通过以下接口管理（`/api/platform/modules`）：

| 操作 | 接口 |
|---|---|
| 查看产品价目表 | `GET /catalog` |
| 查看某租户的授权明细 | `GET /tenant/{tenantId}` |
| 授权模块 | `POST /tenant/{tenantId}/grant` |
| 撤销模块 | `DELETE /tenant/{tenantId}/{moduleKey}` |

**撤销用「置为停用」而不是删除**：`sys_tenant_module` 上有 `(tenant_id, module_key)` 唯一约束，若走逻辑删除，被删的那行仍占用唯一键，后续重新授权会插入失败。置为停用既避开这个坑，也保留了「这个客户曾经买过什么」的销售线索。
