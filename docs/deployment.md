# 部署与运维

## 1. 环境要求

| 组件 | 版本 | 说明 |
|---|---|---|
| JDK | 21+ | 编译与运行 |
| MySQL | 8.0+ | 必须 `utf8mb4`，时区建议 `+08:00` |
| Redis | 7+ | 可选；生产多实例部署时建议启用 |
| Node.js | 20+ | 仅前端构建需要 |
| Nginx | 任意 | 前端静态资源与接口转发 |

---

## 2. 必须覆盖的环境变量

**生产环境绝不能使用代码里的默认值**，尤其是这三个：

| 变量 | 说明 | 不设置的后果 |
|---|---|---|
| `JWT_SECRET` | JWT 签名密钥，**至少 32 字节** | 使用默认值 → 任何人都能伪造令牌 |
| `PLATFORM_ADMIN_PASSWORD` | 平台超管密码 | 默认 `admin123456` → 平台管理端可被登录 |
| `TENANT_ADMIN_PASSWORD` | 新开通租户的默认管理员密码 | 同上 |

其余常用变量：

| 变量 | 默认值 | 说明 |
|---|---|---|
| `DB_HOST` / `DB_PORT` / `DB_NAME` | `127.0.0.1` / `3306` / `ok_crm` | 数据库连接 |
| `DB_USERNAME` / `DB_PASSWORD` | `root` / 空 | 数据库账号 |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` | `127.0.0.1` / `6379` / 空 | Redis 连接 |
| `BACKEND_PORT` | `9001` | 后端端口（本机脚本与 Docker 宿主机映射都用它） |
| `POOL_RECYCLE_ENABLED` | `true` | 是否启用公海自动回收定时任务 |
| `POOL_RECYCLE_CRON` | `0 0 2 * * ?` | 回收任务执行时间 |
| `PLATFORM_ADMIN_ENABLED` | `true` | 是否开放平台超管登录入口 |

> 生成一个合格密钥：`openssl rand -base64 48`

---

## 3. 部署方式

### 3.0 零依赖体验模式（demo profile）

不需要 MySQL / Redis，用嵌入式 H2 文件库，数据落在 `./data/`（重启不丢）。适合本地试用与客户演示：

```bash
mvn -B -DskipTests package -pl apps/crm-boot -am
java -jar apps/crm-boot/target/ok-crm.jar --spring.profiles.active=demo
```

配置见 `apps/crm-boot/src/main/resources/application-demo.yml`，要点：

- 关闭了公海自动回收定时任务，避免演示过程中数据自己变化（要验证回收就用「立即执行一次回收」）
- 默认凭据与 dev 一致（`admin` / `admin123456`），**仅限本地**
- **绝不要用于生产**：H2 是单文件嵌入式库，不支持并发写入扩展，也没有备份与主从

### 3.1 Docker Compose 一键部署

```bash
# 准备环境变量
cat > .env <<'EOF'
MYSQL_ROOT_PASSWORD=<强密码>
JWT_SECRET=<openssl rand -base64 48 的输出>
PLATFORM_ADMIN_PASSWORD=<强密码>
TENANT_ADMIN_PASSWORD=<强密码>
EOF

# 拉起 MySQL + Redis + 后端 + 前端
docker compose --profile app up -d --build

# 查看状态
docker compose ps
docker compose logs -f crm-server
```

访问 `http://<服务器地址>`（前端 Nginx 会把 `/api/` 转发给后端容器）。

### 3.2 只部署后端（前端单独托管）

```bash
mvn -B -DskipTests package -pl apps/crm-boot -am

# 想只交付已购买的模块
mvn -B -DskipTests package -pl apps/crm-boot -am -Pedition-basic

java -jar apps/crm-boot/target/ok-crm.jar \
  --spring.profiles.active=prod \
  --okcrm.jwt.secret="$JWT_SECRET" \
  --spring.datasource.password="$DB_PASSWORD"
```

### 3.3 前端构建

```bash
cd web
npm install
npm run build          # 产物在 web/dist
```

把 `web/dist` 交给 Nginx，并按 `web/nginx.conf` 配置 `/api/` 反向代理。

---

## 4. 数据库迁移

项目使用 **Flyway**，应用启动时自动执行 `apps/crm-boot/src/main/resources/db/migration` 下的脚本。

- 已执行的脚本**不可修改**（Flyway 会校验 checksum）；改结构请新增 `V3__xxx.sql`
- 脚本按「MySQL / H2 双兼容」的可移植 SQL 编写，因此集成测试可以在 H2 上跑同一份脚本
- 新增租户级表必须带 `tenant_id` 列；新增全局表要同步加进 `SharedTableIsolationStrategy` 的全局表清单

---

## 5. 多租户与数据隔离

| 项目 | 说明 |
|---|---|
| 隔离方式 | 共享库共享表 + `tenant_id` 行级隔离 |
| 实现位置 | `platform-mybatis` 的 `TenantLineInnerInterceptor` |
| 全局表 | `sys_tenant`、`sys_module`、`sys_permission`、`flyway_schema_history` |
| 平台超管 | 不做自动隔离，跨租户查询必须显式写 `tenant_id` 条件 |
| 定时任务 | 必须用 `TenantContext.callAs(tenantId, ...)` 显式绑定租户 |

**升级到更强隔离**：实现 `TenantIsolationStrategy`（预留 `SHARED_SCHEMA` / `SEPARATE_DATABASE` 两种模式），替换掉 `SharedTableIsolationStrategy` bean 即可，业务代码无需改动。

---

## 6. 公海池回收任务

- 默认每天凌晨 2 点执行，cron 由 `POOL_RECYCLE_CRON` 控制
- **多实例部署时用分布式锁互斥**：`spring.cache.type=redis` 时自动切换为 Redis 锁（`SET NX EX` + Lua 校验持有者），否则退化为进程内锁
- 回收口径（按最后跟进时间 / 按创建时间）与阈值按租户配置，见 `sys_tenant_config`
- 支持手工触发：`POST /api/pool/recycle/run`（本租户）、`POST /api/pool/recycle/run-all`（平台超管，全部租户）

> ⚠️ 如果只部署单实例，可以继续用进程内锁；一旦扩容到 2 个以上实例，**必须**把 `spring.cache.type` 设为 `redis`，否则各节点会各自跑一遍回收任务。

---

## 7. 可观测性

- 健康检查：`GET /api/actuator/health`
- 日志：`prod` profile 输出到 `logs/ok-crm.log`
- 排查权限问题的开关：把 `logging.level.com.okcrm` 设为 `debug`，每次权限校验都会打印 `权限校验: employeeId=..., code=..., granted=...`

---

## 8. 上线检查清单

- [ ] `JWT_SECRET` 已替换为随机强密钥（≥32 字节）
- [ ] `PLATFORM_ADMIN_PASSWORD`、`TENANT_ADMIN_PASSWORD` 已替换
- [ ] `PLATFORM_ADMIN_ENABLED` 按需关闭（不需要平台管理端对外时）
- [ ] 数据库使用独立账号，不使用 root
- [ ] `spring.cache.type=redis`（多实例部署时）
- [ ] Nginx 已配置 HTTPS，且 `/api/` 正确转发
- [ ] `logs/` 目录已挂载并配置轮转
- [ ] 已完成一次「开通租户 → 登录 → 建客户 → 领取 → 回收」的冒烟验证
