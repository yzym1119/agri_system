-- ============================================
-- 修复 Flyway V58 迁移校验和并更新权限标识
-- ============================================
-- 此脚本用于：
-- 1. 修复 V58 迁移文件的校验和不匹配问题
-- 2. 更新积分商城权限标识，使其与后端控制器一致
-- 
-- 执行方式：在 MySQL 数据库中直接执行此 SQL

-- ============================================
-- 步骤 1: 修复 V58 迁移的校验和
-- ============================================
UPDATE `flyway_schema_history` 
SET `checksum` = 14658361 
WHERE `version` = '58' AND `description` LIKE '%客户管理与营销模块权限%';

-- ============================================
-- 步骤 2: 更新积分商城权限标识
-- ============================================
-- 更新积分商城相关权限标识，使其与后端控制器一致
UPDATE `sys_menu` SET `permission` = 'marketing:points:goods:query' WHERE `id` = 1103;
UPDATE `sys_menu` SET `permission` = 'marketing:points:goods:query' WHERE `id` = 1112;
UPDATE `sys_menu` SET `permission` = 'marketing:points:goods:create' WHERE `id` = 1113;
UPDATE `sys_menu` SET `permission` = 'marketing:points:goods:update' WHERE `id` = 1114;
UPDATE `sys_menu` SET `permission` = 'marketing:points:goods:delete' WHERE `id` = 1115;
UPDATE `sys_menu` SET `permission` = 'marketing:points:order:query' WHERE `id` = 1116;
UPDATE `sys_menu` SET `permission` = 'marketing:points:order:audit' WHERE `id` = 1117;
UPDATE `sys_menu` SET `permission` = 'marketing:points:order:verify' WHERE `id` = 1118;

-- ============================================
-- 步骤 3: 验证更新结果
-- ============================================
-- 验证 Flyway 历史记录
SELECT `version`, `description`, `checksum`, `installed_on` 
FROM `flyway_schema_history` 
WHERE `version` = '58';

-- 验证权限标识更新
SELECT `id`, `name`, `permission` 
FROM `sys_menu` 
WHERE `id` IN (1103, 1112, 1113, 1114, 1115, 1116, 1117, 1118)
ORDER BY `id`;









