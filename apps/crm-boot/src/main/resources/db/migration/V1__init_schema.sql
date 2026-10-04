-- ============================================================================
-- OK-CRM 初始化建表
--
-- 兼容性约束（同一份脚本要同时跑在 MySQL 8 与 H2 的 MySQL 兼容模式上）：
--   1. 不使用 ENGINE / DEFAULT CHARSET 等 MySQL 专有表选项
--   2. 不使用列内 COMMENT（H2 不支持）
--   3. 唯一约束统一写 CONSTRAINT xxx UNIQUE (...)，不用内联 UNIQUE KEY
--   4. 普通索引一律单独 CREATE INDEX，不用内联 KEY
--   5. 不使用 ON UPDATE CURRENT_TIMESTAMP，时间戳全部由应用层写入
--
-- 主键策略：全部由 MyBatis-Plus 雪花算法生成，因此不使用 AUTO_INCREMENT。
--
-- 逻辑删除与唯一约束的取舍（重要）：
--   带逻辑删除（deleted 字段）的表上，唯一约束会导致「删除后无法用同样的键重新创建」。
--   因此只有以下表保留数据库级唯一约束，其余唯一性由 Service 层校验：
--     sys_tenant(code) / sys_module(module_key)
--     sys_tenant_config(tenant_id, config_key)       —— 配置项从不删除
--     sys_tenant_module(tenant_id, module_key)       —— 撤销走「置停用」而非删除
-- ============================================================================


-- ---------------------------------------------------------------------------
-- 平台底座
-- ---------------------------------------------------------------------------

-- 租户（企业）。全局表，不参与租户隔离
CREATE TABLE sys_tenant
(
    id            BIGINT       NOT NULL,
    code          VARCHAR(64)  NOT NULL,
    name          VARCHAR(128) NOT NULL,
    region        VARCHAR(32)  NOT NULL,
    region_name   VARCHAR(64),
    timezone      VARCHAR(64),
    currency      VARCHAR(16),
    contact_name  VARCHAR(64),
    contact_phone VARCHAR(32),
    expire_date   DATE,
    status        TINYINT      NOT NULL DEFAULT 1,
    created_at    DATETIME,
    created_by    BIGINT,
    updated_at    DATETIME,
    updated_by    BIGINT,
    deleted       TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_sys_tenant_code UNIQUE (code)
);

-- 可售卖模块目录（产品价目表）。全局表
CREATE TABLE sys_module
(
    id          BIGINT       NOT NULL,
    module_key  VARCHAR(64)  NOT NULL,
    name        VARCHAR(64)  NOT NULL,
    description VARCHAR(500),
    version     VARCHAR(32),
    core        TINYINT      NOT NULL DEFAULT 0,
    sort_order  INT                   DEFAULT 0,
    created_at  DATETIME,
    created_by  BIGINT,
    updated_at  DATETIME,
    updated_by  BIGINT,
    deleted     TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_sys_module_key UNIQUE (module_key)
);

-- 权限点目录（同时充当菜单）。全局表：权限由平台定义，全租户共享
CREATE TABLE sys_permission
(
    id          BIGINT       NOT NULL,
    code        VARCHAR(128) NOT NULL,
    name        VARCHAR(64)  NOT NULL,
    module_key  VARCHAR(64)  NOT NULL,
    type        VARCHAR(16)  NOT NULL,
    parent_code VARCHAR(128)          DEFAULT '0',
    path        VARCHAR(255),
    component   VARCHAR(255),
    icon        VARCHAR(64),
    sort_order  INT                   DEFAULT 0,
    status      TINYINT      NOT NULL DEFAULT 1,
    created_at  DATETIME,
    created_by  BIGINT,
    updated_at  DATETIME,
    updated_by  BIGINT,
    deleted     TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX idx_sys_permission_module ON sys_permission (module_key);
CREATE INDEX idx_sys_permission_parent ON sys_permission (parent_code);

-- 租户级配置（KV）。租户表
CREATE TABLE sys_tenant_config
(
    id           BIGINT        NOT NULL,
    tenant_id    BIGINT        NOT NULL,
    config_key   VARCHAR(128)  NOT NULL,
    config_value VARCHAR(1000),
    remark       VARCHAR(255),
    created_at   DATETIME,
    created_by   BIGINT,
    updated_at   DATETIME,
    updated_by   BIGINT,
    deleted      TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_sys_tenant_config UNIQUE (tenant_id, config_key)
);

-- 租户已授权模块。租户表
CREATE TABLE sys_tenant_module
(
    id          BIGINT      NOT NULL,
    tenant_id   BIGINT      NOT NULL,
    module_key  VARCHAR(64) NOT NULL,
    status      TINYINT     NOT NULL DEFAULT 1,
    expire_date DATE,
    remark      VARCHAR(255),
    created_at  DATETIME,
    created_by  BIGINT,
    updated_at  DATETIME,
    updated_by  BIGINT,
    deleted     TINYINT     NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_sys_tenant_module UNIQUE (tenant_id, module_key)
);


-- ---------------------------------------------------------------------------
-- 组织与权限（岗位即角色）
-- ---------------------------------------------------------------------------

-- 岗位 = 角色。租户表
CREATE TABLE sys_position
(
    id          BIGINT      NOT NULL,
    tenant_id   BIGINT      NOT NULL,
    code        VARCHAR(64) NOT NULL,
    name        VARCHAR(64) NOT NULL,
    data_scope  VARCHAR(16) NOT NULL DEFAULT 'SELF',
    status      TINYINT     NOT NULL DEFAULT 1,
    sort_order  INT                  DEFAULT 0,
    remark      VARCHAR(255),
    created_at  DATETIME,
    created_by  BIGINT,
    updated_at  DATETIME,
    updated_by  BIGINT,
    deleted     TINYINT     NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX idx_sys_position_tenant ON sys_position (tenant_id);

-- 岗位 → 权限点。租户表
-- 刻意不加唯一约束：权限保存采用「先删后插」，逻辑删除的旧行会占用唯一键
CREATE TABLE sys_position_permission
(
    id              BIGINT       NOT NULL,
    tenant_id       BIGINT       NOT NULL,
    position_id     BIGINT       NOT NULL,
    permission_code VARCHAR(128) NOT NULL,
    created_at      DATETIME,
    created_by      BIGINT,
    updated_at      DATETIME,
    updated_by      BIGINT,
    deleted         TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX idx_sys_pos_perm_position ON sys_position_permission (position_id);

-- 员工（同时是登录账号）。租户表
-- 唯一性（租户内账号不重复）由 Service 层校验，原因见文件头的说明
CREATE TABLE sys_employee
(
    id            BIGINT      NOT NULL,
    tenant_id     BIGINT      NOT NULL,
    username      VARCHAR(64) NOT NULL,
    password      VARCHAR(128) NOT NULL,
    real_name     VARCHAR(64),
    phone         VARCHAR(32),
    email         VARCHAR(128),
    status        TINYINT     NOT NULL DEFAULT 1,
    remark        VARCHAR(255),
    last_login_at DATETIME,
    created_at    DATETIME,
    created_by    BIGINT,
    updated_at    DATETIME,
    updated_by    BIGINT,
    deleted       TINYINT     NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX idx_sys_employee_tenant_username ON sys_employee (tenant_id, username);

-- 员工 ↔ 岗位（支持一人多岗，权限取并集）。租户表
CREATE TABLE sys_emp_position
(
    id          BIGINT  NOT NULL,
    tenant_id   BIGINT  NOT NULL,
    employee_id BIGINT  NOT NULL,
    position_id BIGINT  NOT NULL,
    created_at  DATETIME,
    created_by  BIGINT,
    updated_at  DATETIME,
    updated_by  BIGINT,
    deleted     TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX idx_sys_emp_pos_employee ON sys_emp_position (employee_id);
CREATE INDEX idx_sys_emp_pos_position ON sys_emp_position (position_id);


-- ---------------------------------------------------------------------------
-- 客户
-- ---------------------------------------------------------------------------

-- 客户主表。owner_id 为 NULL 表示在公海池中。租户表
CREATE TABLE crm_customer
(
    id                BIGINT       NOT NULL,
    tenant_id         BIGINT       NOT NULL,
    name              VARCHAR(128) NOT NULL,
    industry          VARCHAR(64),
    level             VARCHAR(8),
    source            VARCHAR(64),
    phone             VARCHAR(32),
    address           VARCHAR(255),
    owner_id          BIGINT,
    owner_assigned_at DATETIME,
    last_follow_up_at DATETIME,
    enter_pool_at     DATETIME,
    pool_reason       VARCHAR(255),
    tags              VARCHAR(255),
    remark            VARCHAR(1000),
    created_at        DATETIME,
    created_by        BIGINT,
    updated_at        DATETIME,
    updated_by        BIGINT,
    deleted           TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX idx_crm_customer_tenant_owner ON crm_customer (tenant_id, owner_id);
CREATE INDEX idx_crm_customer_tenant_pool ON crm_customer (tenant_id, enter_pool_at);

-- 客户联系人。租户表
CREATE TABLE crm_contact
(
    id              BIGINT      NOT NULL,
    tenant_id       BIGINT      NOT NULL,
    customer_id     BIGINT      NOT NULL,
    name            VARCHAR(64) NOT NULL,
    position        VARCHAR(64),
    phone           VARCHAR(32),
    email           VARCHAR(128),
    primary_contact TINYINT     NOT NULL DEFAULT 0,
    remark          VARCHAR(255),
    created_at      DATETIME,
    created_by      BIGINT,
    updated_at      DATETIME,
    updated_by      BIGINT,
    deleted         TINYINT     NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX idx_crm_contact_customer ON crm_contact (customer_id);

-- 跟进记录。租户表。写入时会同步刷新 crm_customer.last_follow_up_at
CREATE TABLE crm_followup
(
    id                BIGINT       NOT NULL,
    tenant_id         BIGINT       NOT NULL,
    customer_id       BIGINT       NOT NULL,
    employee_id       BIGINT,
    type              VARCHAR(32),
    content           VARCHAR(1000) NOT NULL,
    followed_at       DATETIME,
    next_follow_up_at DATETIME,
    created_at        DATETIME,
    created_by        BIGINT,
    updated_at        DATETIME,
    updated_by        BIGINT,
    deleted           TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX idx_crm_followup_customer ON crm_followup (customer_id);
CREATE INDEX idx_crm_followup_followed_at ON crm_followup (followed_at);

-- 公海池流转日志。租户表。由 CustomerOwnershipChangedEvent 驱动写入
CREATE TABLE crm_pool_log
(
    id            BIGINT       NOT NULL,
    tenant_id     BIGINT       NOT NULL,
    customer_id   BIGINT       NOT NULL,
    customer_name VARCHAR(128),
    action        VARCHAR(32)  NOT NULL,
    from_owner_id BIGINT,
    to_owner_id   BIGINT,
    reason        VARCHAR(255),
    operator_id   BIGINT,
    created_at    DATETIME,
    created_by    BIGINT,
    updated_at    DATETIME,
    updated_by    BIGINT,
    deleted       TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX idx_crm_pool_log_customer ON crm_pool_log (customer_id);
CREATE INDEX idx_crm_pool_log_created_at ON crm_pool_log (created_at);
