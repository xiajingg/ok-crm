-- ============================================================================
-- 初始化种子数据：可售卖模块目录 + 权限点目录
--
-- 权限点由平台统一定义（全局表），租户之间的差异体现在「把哪些权限授予了岗位」。
-- 岗位的默认权限在租户开通时由代码分配，见 PositionService#createDefaultPositions。
--
-- ID 手工指定，方便跨环境一致；不与雪花 ID 冲突（雪花 ID 量级远大于此）。
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1. 可售卖模块（产品价目表）
-- ---------------------------------------------------------------------------
INSERT INTO sys_module (id, module_key, name, description, version, core, sort_order, created_at, deleted)
VALUES (1001, 'tenant', '租户与地区', '多租户底座：企业开通、地区属性、租户级配置与模块授权', '1.0.0', 1, 1, CURRENT_TIMESTAMP, 0),
       (1002, 'iam', '组织与权限', '岗位（即角色）、权限点、员工管理，支持一人多岗与数据权限', '1.0.0', 1, 2, CURRENT_TIMESTAMP, 0),
       (1003, 'customer', '客户管理', '客户档案、联系人、跟进记录、客户归属与分配', '1.0.0', 0, 3, CURRENT_TIMESTAMP, 0),
       (1004, 'pool', '公海池', '客户公海：领取、指派、退回，支持按租户配置超期未跟进自动回收', '1.0.0', 0, 4, CURRENT_TIMESTAMP, 0);


-- ---------------------------------------------------------------------------
-- 2. 权限点（MENU 参与菜单渲染，BUTTON 只参与接口鉴权）
-- ---------------------------------------------------------------------------

-- 租户与地区
INSERT INTO sys_permission (id, code, name, module_key, type, parent_code, path, component, icon, sort_order, status, created_at, deleted)
VALUES (2001, 'tenant:menu', '租户管理', 'tenant', 'MENU', '0', '/platform/tenants', 'platform/tenant/index', 'OfficeBuilding', 10, 1, CURRENT_TIMESTAMP, 0),
       (2002, 'tenant:config', '租户设置', 'tenant', 'MENU', '0', '/settings/tenant', 'settings/tenant/index', 'Setting', 11, 1, CURRENT_TIMESTAMP, 0),
       (2003, 'tenant:config:update', '修改租户配置', 'tenant', 'BUTTON', 'tenant:config', NULL, NULL, NULL, 1, 1, CURRENT_TIMESTAMP, 0);

-- 组织与权限
INSERT INTO sys_permission (id, code, name, module_key, type, parent_code, path, component, icon, sort_order, status, created_at, deleted)
VALUES (2010, 'iam:menu', '组织管理', 'iam', 'MENU', '0', '/org', 'Layout', 'User', 20, 1, CURRENT_TIMESTAMP, 0),
       (2011, 'iam:position:list', '岗位管理', 'iam', 'MENU', 'iam:menu', '/org/positions', 'org/position/index', 'Avatar', 1, 1, CURRENT_TIMESTAMP, 0),
       (2012, 'iam:employee:list', '员工管理', 'iam', 'MENU', 'iam:menu', '/org/employees', 'org/employee/index', 'UserFilled', 2, 1, CURRENT_TIMESTAMP, 0),
       (2013, 'iam:position:create', '新增岗位', 'iam', 'BUTTON', 'iam:position:list', NULL, NULL, NULL, 1, 1, CURRENT_TIMESTAMP, 0),
       (2014, 'iam:position:update', '修改岗位', 'iam', 'BUTTON', 'iam:position:list', NULL, NULL, NULL, 2, 1, CURRENT_TIMESTAMP, 0),
       (2015, 'iam:position:delete', '删除岗位', 'iam', 'BUTTON', 'iam:position:list', NULL, NULL, NULL, 3, 1, CURRENT_TIMESTAMP, 0),
       (2016, 'iam:employee:create', '新增员工', 'iam', 'BUTTON', 'iam:employee:list', NULL, NULL, NULL, 1, 1, CURRENT_TIMESTAMP, 0),
       (2017, 'iam:employee:update', '修改员工', 'iam', 'BUTTON', 'iam:employee:list', NULL, NULL, NULL, 2, 1, CURRENT_TIMESTAMP, 0),
       (2018, 'iam:employee:delete', '删除员工', 'iam', 'BUTTON', 'iam:employee:list', NULL, NULL, NULL, 3, 1, CURRENT_TIMESTAMP, 0);

-- 客户管理
INSERT INTO sys_permission (id, code, name, module_key, type, parent_code, path, component, icon, sort_order, status, created_at, deleted)
VALUES (2020, 'customer:menu', '客户管理', 'customer', 'MENU', '0', '/customers', 'customer/index', 'UserFilled', 30, 1, CURRENT_TIMESTAMP, 0),
       (2021, 'customer:list', '查看客户', 'customer', 'BUTTON', 'customer:menu', NULL, NULL, NULL, 1, 1, CURRENT_TIMESTAMP, 0),
       (2022, 'customer:create', '新增客户', 'customer', 'BUTTON', 'customer:menu', NULL, NULL, NULL, 2, 1, CURRENT_TIMESTAMP, 0),
       (2023, 'customer:update', '修改客户', 'customer', 'BUTTON', 'customer:menu', NULL, NULL, NULL, 3, 1, CURRENT_TIMESTAMP, 0),
       (2024, 'customer:delete', '删除客户', 'customer', 'BUTTON', 'customer:menu', NULL, NULL, NULL, 4, 1, CURRENT_TIMESTAMP, 0),
       (2025, 'customer:transfer', '分配/转移客户', 'customer', 'BUTTON', 'customer:menu', NULL, NULL, NULL, 5, 1, CURRENT_TIMESTAMP, 0),
       (2026, 'customer:followup', '记录跟进', 'customer', 'BUTTON', 'customer:menu', NULL, NULL, NULL, 6, 1, CURRENT_TIMESTAMP, 0);

-- 公海池
INSERT INTO sys_permission (id, code, name, module_key, type, parent_code, path, component, icon, sort_order, status, created_at, deleted)
VALUES (2030, 'pool:menu', '公海池', 'pool', 'MENU', '0', '/pool', 'pool/index', 'Grid', 40, 1, CURRENT_TIMESTAMP, 0),
       (2031, 'pool:logs', '流转日志', 'pool', 'MENU', 'pool:menu', '/pool/logs', 'pool/logs', 'Document', 1, 1, CURRENT_TIMESTAMP, 0),
       (2032, 'pool:settings', '回收规则', 'pool', 'MENU', 'pool:menu', '/pool/settings', 'pool/settings', 'Setting', 2, 1, CURRENT_TIMESTAMP, 0),
       (2033, 'pool:list', '查看公海', 'pool', 'BUTTON', 'pool:menu', NULL, NULL, NULL, 1, 1, CURRENT_TIMESTAMP, 0),
       (2034, 'pool:claim', '领取客户', 'pool', 'BUTTON', 'pool:menu', NULL, NULL, NULL, 2, 1, CURRENT_TIMESTAMP, 0),
       (2035, 'pool:assign', '指派客户', 'pool', 'BUTTON', 'pool:menu', NULL, NULL, NULL, 3, 1, CURRENT_TIMESTAMP, 0),
       (2036, 'pool:release', '退回公海', 'pool', 'BUTTON', 'pool:menu', NULL, NULL, NULL, 4, 1, CURRENT_TIMESTAMP, 0),
       (2037, 'pool:settings:update', '修改回收规则', 'pool', 'BUTTON', 'pool:settings', NULL, NULL, NULL, 1, 1, CURRENT_TIMESTAMP, 0);
