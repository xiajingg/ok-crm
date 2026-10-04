# OK-CRM

> 多租户、多地区的客户关系管理系统。**模块化单体架构**，可按模块独立授权售卖。

[![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](LICENSE)
![Java](https://img.shields.io/badge/Java-21-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5-green)
![Vue](https://img.shields.io/badge/Vue-3-42b883)

---

## 这个项目解决什么问题

面向**多个不同地区的企业**提供一套 CRM：每家企业（租户）独立登录、独立数据、独立岗位与权限体系；员工可以在企业内被分配岗位，客户可以归属到员工，也可以放进「公海池」由全公司竞争领取。

与常见的后台管理系统不同，这个项目在架构上做了三件额外的事：

| 能力 | 说明 |
|---|---|
| **多租户行级隔离** | 共享库共享表 + `tenant_id`，由 MyBatis-Plus 拦截器自动改写 SQL，业务代码零感知；隔离策略可插拔，将来可平滑升级到 Schema / 独立库隔离 |
| **模块化 + 按模块售卖** | 业务代码物理隔离在独立 Maven 模块中，模块之间只能通过 `api` 包通信；用 Maven profile 裁剪交付包，用运行时授权台账控制每个租户买了哪些模块 |
| **模块边界由 CI 强制** | 9 条 ArchUnit 架构测试，破坏模块边界会直接让构建失败 |

---

## 功能范围

### 已实现（MVP）

**多租户底座**
- 企业（租户）开通 / 停用 / 编辑，租户带地区、时区、币种属性
- 平台超管跨租户管理端（与租户内接口物理分离，杜绝越权）
- 租户级配置（KV，按企业独立）

**组织与权限（岗位即角色）**
- 岗位管理：岗位同时承载「职务」与「权限 + 数据范围」
- 权限点目录按模块分组，前端权限树勾选
- 员工管理：一人可兼任多岗位，权限取并集、数据范围取最宽
- 数据权限：`全部数据` / `仅本人数据`，在客户查询上自动生效
- 登录鉴权（JWT）、动态菜单下发

**客户管理**
- 客户档案、联系人、跟进记录
- 客户归属：单一负责人 + 归属转移（每次变更留痕）
- 按关键词 / 级别 / 负责人检索，受数据权限约束

**公海池**
- 公海客户列表、员工领取、主管指派、退回公海
- 流转日志：领取 / 指派 / 转移 / 退回 / 系统回收，全量可追溯
- 超期未跟进自动回收：天数与时间口径（最后跟进 / 创建时间）按租户可配
- 单人持有上限校验；定时任务带分布式锁，多实例部署互斥

### 路线图（P1 / P2）

- **P1**：客户导入导出、跟进提醒待办、部门层级与部门级数据权限
- **P2（规划为可独立售卖的模块）**：报表看板、商机与合同、审批流、营销自动化、企业微信/钉钉集成、**AI 增强**（智能跟进摘要、客户画像、话术推荐）

---

## 技术选型

| 层次 | 选型 | 理由 |
|---|---|---|
| 后端 | Java 21 + Spring Boot 3.5 | 生态成熟，ToB 交付标准 |
| 架构 | Maven 多模块（Modular Monolith） | 满足模块化诉求，又不引入微服务的分布式复杂度 |
| 持久层 | MyBatis-Plus + MySQL 8 + Flyway | `TenantLineInnerInterceptor` 让多租户隔离变成配置而非业务代码 |
| 缓存 / 锁 | Redis（可选）+ Spring Cache | 开发期用进程内实现，生产切 Redis，业务代码不变 |
| 认证 | Spring Security + JWT | 无状态，配合 `@PreAuthorize` 做方法级鉴权 |
| 前端 | Vue 3 + Vite + TypeScript + Element Plus | 国内 ToB 后台事实标准，组件齐全 |
| 接口文档 | springdoc-openapi | 启动后访问 `/api/swagger-ui.html` |

---

## 架构

### 模块划分

```
ok-crm/
├── platform/                  平台底座（不单独售卖）
│   ├── platform-common        统一响应、业务异常、实体基类、模块标识常量
│   ├── platform-tenant        租户上下文 + 可插拔隔离策略
│   ├── platform-mybatis       MP 配置、租户拦截器、审计字段自动填充
│   ├── platform-security      JWT、权限校验、数据权限、模块授权闸门
│   └── platform-web           全局异常处理、OpenAPI 文档
│
├── modules/                   可售卖业务模块
│   ├── module-tenant          租户与地区、租户配置、模块授权
│   ├── module-iam             岗位（即角色）、权限点、员工
│   ├── module-customer        客户、联系人、跟进记录、客户归属
│   └── module-pool            公海池：领取、指派、退回、自动回收
│
├── apps/crm-boot              唯一启动器，按 profile 组装模块
└── web                        Vue 3 管理后台
```

**每个业务模块内部统一四层**（目录即边界）：

```
com.okcrm.modules.customer
├── api/        对外契约：DTO、领域事件、QueryService 接口  ← 其它模块唯一可依赖处
├── internal/   应用层：Controller、AppService、DTO
├── domain/     领域模型：实体、枚举、领域规则
└── infra/      持久层：MyBatis Mapper
```

### 模块之间怎么通信

只有两种方式，都写在架构测试里强制约束：

1. **领域事件**（`ApplicationEventPublisher`）—— 用于「某事发生后触发的后续反应」
   - 租户开通 → IAM 模块初始化默认岗位与管理员账号
   - 客户归属变更 → 公海池模块写入流转日志
2. **模块 `api` 接口同步调用** —— 用于「必须立刻拿到结果」
   - 客户分配前校验负责人是否存在且在岗

**禁止**跨模块直接查表、或引用对方的 `internal` / `infra` / `domain` 包。这条由 `ModuleBoundaryTest` 里的 ArchUnit 规则强制。

### 多租户是怎么落地的

- 请求进入时，JWT 过滤器一次性写入 `SecurityContext`（身份）与 `TenantContext`（租户）
- `TenantLineInnerInterceptor` 读取 `TenantContext`，自动给 SQL 追加 `tenant_id = ?`
- 全局表（`sys_tenant` / `sys_module` / `sys_permission`）显式排除
- 取不到租户上下文时**直接抛异常**而不是静默返回空数据 —— 让「定时任务忘了设上下文」这类 bug 立刻暴露
- 定时任务用 `TenantContext.callAs(tenantId, ...)` 显式绑定租户，异步线程池挂 `TaskDecorator` 传递上下文

### 按模块独立售卖是怎么落地的

三层配合，缺一不可：

| 层 | 机制 | 效果 |
|---|---|---|
| 编译期 | `apps/crm-boot` 的 Maven profile | `-Pedition-basic` 产出的交付包里**物理上不含**未购买的模块 |
| 运行期 | `sys_tenant_module` 授权台账 + `@RequiresModule` 拦截器 | 未购买时接口返回 **1401「当前租户未购买该模块」**，而不是 404 或 500 |
| 前端 | 后端按「已购模块 ∩ 岗位权限」动态下发菜单 | 未购买的模块菜单自动消失，前端不需要维护两套代码 |

```bash
# 完整版（默认）
mvn -pl apps/crm-boot -am package

# 基础版：组织 + 客户，不含公海池
mvn -pl apps/crm-boot -am package -Pedition-basic

# 专业版：基础版 + 公海池
mvn -pl apps/crm-boot -am package -Pedition-pro
```

---

## 快速开始

### 一键启动（推荐）

仓库自带启动脚本，会把后端和前端一起拉起来：

```bash
# 1. 打包（只需一次）
mvn -B -DskipTests package -pl apps/crm-boot -am

# 2. 安装前端依赖（只需一次）
cd web && npm install && cd ..

# 3a. 连真实 MySQL 启动
export DB_PASSWORD=你的MySQL密码
./scripts/start-local.sh mysql

# 3b. 或者零依赖启动（嵌入式 H2，不需要 MySQL）
./scripts/start-local.sh demo
```

脚本会自动等后端就绪、检查端口占用，并把前端的接口代理指向后端。按 `Ctrl-C` 一起停。

密码也可以写在仓库根目录的 `.env.local`（该文件已被 `.gitignore` 忽略）：

```
DB_PASSWORD=你的MySQL密码
```

**首次使用 MySQL 时先建库**（Flyway 会自动建表）：

```bash
mysql -uroot -p -e "CREATE DATABASE ok_crm DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
```

> ⚠️ 后台请用 **http://localhost:5173** 访问，不要用 `127.0.0.1:5173` ——
> Vite 默认只监听 IPv6 的 `localhost`。

### 方式零：手动零依赖启动（不需要 MySQL / Redis）

适合本地试用、给客户演示、录屏。用的是嵌入式 H2 文件库，数据落在 `./data/`，重启不丢。

```bash
# 1. 打包
mvn -B -DskipTests package -pl apps/crm-boot -am

# 2. 以 demo profile 启动（默认端口 9001，被占用时用 --server.port 换）
java -jar apps/crm-boot/target/ok-crm.jar --spring.profiles.active=demo

# 3. 前端（另开一个终端）
cd web && npm install
VITE_API_TARGET=http://127.0.0.1:9001 npm run dev
```

打开 http://localhost:5173 ，用 `admin` / `admin123456` 以**平台管理端**登录 → 「租户管理」开通一个企业 → 再用同一组账号以**企业登录**进入。

> 想从零开始：删掉 `data/` 目录即可（里面只有一个本地 H2 库文件）。
>
> 后端启动后也可以直接看接口文档：http://localhost:9001/api/swagger-ui.html

### 方式一：Docker Compose（完整环境）

```bash
git clone <repo-url> ok-crm && cd ok-crm

# 只启动 MySQL + Redis
docker compose up -d

# 后端（另开一个终端）
mvn -pl apps/crm-boot -am spring-boot:run -Dspring-boot.run.profiles=dev

# 前端（再开一个终端）
cd web && npm install && npm run dev
```

打开 http://localhost:5173

也可以整套容器化运行（包含前后端）：

```bash
docker compose --profile app up -d --build
# 访问 http://localhost
```

### 方式二：本地手动跑

前置条件：JDK 21、Maven 3.9+、Node 20+、MySQL 8、Redis（可选）

```bash
# 1. 建库（Flyway 会自动建表）
mysql -uroot -p -e "CREATE DATABASE ok_crm DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

# 2. 配置数据库连接（或用环境变量 DB_HOST / DB_PORT / DB_USERNAME / DB_PASSWORD）
#    默认值见 apps/crm-boot/src/main/resources/application-dev.yml

# 3. 启动后端
mvn -pl apps/crm-boot -am spring-boot:run -Dspring-boot.run.profiles=dev

# 4. 启动前端
cd web && npm install && npm run dev
```

> **开发环境不需要 Redis**：`dev` profile 用进程内缓存与进程内锁，`prod` profile 才切到 Redis。

### 默认账号

| 入口 | 账号 | 密码 | 说明 |
|---|---|---|---|
| 平台管理端 | `admin` | `admin123456` | 用于开通租户、授权模块 |
| 租户管理员 | `admin` | `admin123456` | 开通租户时自动创建 |

> ⚠️ 生产环境必须通过环境变量覆盖：`PLATFORM_ADMIN_PASSWORD`、`TENANT_ADMIN_PASSWORD`、`JWT_SECRET`。

### 第一次使用的推荐流程

1. 用**平台管理端**登录 → 「租户管理」→ 开通一个租户（自动生成 3 个默认岗位 + 1 个管理员账号）
2. 用**租户管理员**登录（选择刚开通的企业）
3. 「组织管理 → 员工管理」新增一个销售，关联「销售」岗位
4. 「客户管理」新增一个客户，负责人留空 → 客户进入公海池
5. 「公海池」里领取该客户 → 客户归属到你名下，「流转日志」里出现一条领取记录
6. 「公海池 → 回收规则」启用自动回收、天数设为 1、口径设为「按创建时间」→ 点「立即执行一次回收」→ 客户被打回公海，日志里出现 RECYCLE

---

## 常见问题

**端口被占用** —— 后端默认端口是 **9001**。开发机上常有别的程序占着它（比如 rustfs 会占 9000/9001）。先查是谁：

```bash
lsof -i:9001
```

换端口启动，并让前端代理跟着改：

```bash
java -jar apps/crm-boot/target/ok-crm.jar --spring.profiles.active=demo --server.port=9002
VITE_API_TARGET=http://127.0.0.1:9002 npm run dev
```

用启动脚本时更简单，它会自己检查端口并把占用进程打出来：

```bash
BACKEND_PORT=9002 ./scripts/start-local.sh mysql
```

> ⚠️ 不要用 `pkill -f java` 清端口 —— 会连带杀掉机器上其它 Java 程序。

**接口返回 401 但令牌明明是对的** —— 检查请求路径有没有重复带 context-path。后端 `server.servlet.context-path` 是 `/api`，所以完整地址是 `/api/auth/login`，不要再拼一次。

**权限校验看起来没生效** —— 把 `logging.level.com.okcrm` 设为 `debug`，每次权限校验都会打印 `权限校验: employeeId=..., code=..., granted=...`。如果日志里 `granted=false` 但接口仍返回 200，说明有兜底异常处理器吞掉了 `AccessDeniedException`（`SecurityExceptionAdvice` 必须保持最高优先级）。

**Redis 报 MISCONF / 接口变慢但不错** —— 典型报错：

```
MISCONF Redis is configured to save RDB snapshots, but it's currently unable to persist to disk.
Commands that may modify the data set are disabled (stop-writes-on-bgsave-error option)
```

这是 Redis 自己写盘失败后**拒绝所有写命令**，跟本应用无关（读命令仍可用，所以业务不会挂）。

应用的应对是**缓存降级**：`CacheConfig` 里的 `CacheErrorHandler` 会把缓存读写失败降级为「直接查库」，
只在日志里留 WARN/ERROR，接口照常返回正确结果 —— 缓存是优化，不是正确性依赖。

排查 Redis 侧：

```bash
# 1. 看 Redis 的工作目录配置（为空或指向不存在的目录就是它的问题）
redis-cli CONFIG GET dir

# 2. 看最近一次快照是否成功
redis-cli INFO persistence | grep rdb_last_bgsave_status

# 3. 临时放开写限制（Redis 重启后失效；仅建议用于「只当缓存用」的实例）
redis-cli CONFIG SET stop-writes-on-bgsave-error no
```

根治：确保 `dir` 指向一个存在的可写目录（Homebrew 默认 `/opt/homebrew/var/db/redis`），
或干脆给缓存实例关掉 RDB 持久化（`save ""`）。

> 另外注意：应用日志里出现 `缓存读取失败，已降级为直接查库` 说明缓存**没在生效**。
> 这时要看具体原因 —— 本项目踩过一次 `GenericJackson2JsonRedisSerializer`
> 对 `Set<String>` 「写得进读不出」的坑，因此值序列化改用 JDK 序列化。

---

## 接口文档

后端启动后访问 **http://localhost:9001/api/swagger-ui.html**

调用顺序：`POST /api/auth/platform-login` 或 `POST /api/auth/login` 拿 token → 右上角 Authorize 填入 → 之后所有请求自动带 `Authorization` 头。

---

## 测试

```bash
mvn test
```

21 个测试，全部基于 H2 内存库，**不需要 MySQL / Redis**：

- `ModuleBoundaryTest`（9 个）—— ArchUnit 模块边界与分层约束
- `CrmFlowIntegrationTest`（12 个）—— 真实 HTTP 栈的端到端主链路：
  平台登录 → 开通租户 → 租户登录 → 默认岗位与权限校验 → 建员工关联岗位 → 建客户进公海 → 领取并留痕 → 重复领取被拒 → 超期自动回收 → 数据权限隔离 → 岗位权限 403 → 模块未购买 1401

---

## 项目结构速查

| 想改什么 | 去哪里 |
|---|---|
| 新增一个可售卖模块 | 新建 `modules/module-xxx`，在 `platform-common` 的 `ModuleKeys` 加常量，Flyway 里往 `sys_module` 加一行，权限点的 `module_key` 填同一个值 |
| 新增一个接口 | 对应模块的 `internal/controller`，方法上标 `@RequiresPermission`（如需要）与 `@PreAuthorize` |
| 新增一张租户级表 | 实体继承 `TenantEntity`，Flyway 脚本里带 `tenant_id` 列 |
| 新增一张全局表 | 实体继承 `BaseEntity`，并把表名加入 `SharedTableIsolationStrategy` 的全局表清单 |
| 调整公海回收规则 | 后端 `TenantConfigKeys`，前端 `web/src/views/pool/settings.vue` |
| 换隔离策略（行级 → Schema） | 实现 `TenantIsolationStrategy` 并替换 bean，业务代码零改动 |

---

## 已知限制

诚实地列出来，避免误用：

- **唯一约束与逻辑删除的取舍**：带 `deleted` 的表上，数据库级唯一约束会导致「删除后无法用同样的键重建」，因此只有 `sys_tenant` / `sys_module` / `sys_tenant_config` / `sys_tenant_module` 保留数据库唯一约束，其余（岗位编码、员工账号）唯一性由 Service 层校验。高并发下理论存在竞态，生产环境建议按需补唯一索引。
- **数据权限只实现两档**（全部 / 仅本人）。部门层级与「指定员工集合」属 P1。
- **权限缓存**（`@Cacheable`）在授权变更时会按租户/员工精确失效；但模块授权闸门刻意不走缓存，每次都查库 —— 收费相关的判断不接受脏读。
- **前端未做单元测试**，验证依赖后端集成测试 + 手工走查。
- **公开仓库**：按「先全公开跑起来，商业化后再拆」的策略，仓库中不包含任何真实客户数据、定价或商业计划。

---

## License

[Apache-2.0](LICENSE)
