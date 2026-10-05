-- ============================================================================
-- 单企业私有化部署改造
--
-- 背景：产品方向由「多租户 SaaS」调整为「每个企业单独部署」，
--       平台管理端（租户管理、模块授权）整体下线，企业信息改由
--       启动时的部署初始化流程写入，并在「企业设置」页维护。
--
-- 本脚本做两件事：
--   1. 删除「租户管理」菜单及其岗位授权 —— 它指向的平台管理端页面已删除，
--      留着会让前端菜单指向不存在的组件（点进去白屏）
--   2. 把「租户设置」改名为「企业设置」，与新定位一致
--
-- 注意：Flyway 走的是原生 JDBC，不经过 MyBatis 的租户拦截器，
--       因此这里的 DELETE 会作用于全部企业记录 —— 这正是本脚本的意图。
-- ============================================================================

-- 1. 移除「租户管理」菜单
DELETE FROM sys_position_permission WHERE permission_code = 'tenant:menu';
DELETE FROM sys_permission WHERE code = 'tenant:menu';

-- 2. 术语对齐：租户 → 企业
UPDATE sys_permission SET name = '企业设置' WHERE code = 'tenant:config';
UPDATE sys_permission SET name = '修改企业设置' WHERE code = 'tenant:config:update';
