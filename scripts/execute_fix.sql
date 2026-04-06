-- ============================================
-- 一键修复脚本：修复 V58 校验和并更新权限
-- ============================================
-- 请在 MySQL 中执行此脚本

USE agri_system;

-- 1. 修复 V58 校验和
UPDATE `flyway_schema_history` 
SET `checksum` = 14658361 
WHERE `version` = '58';

-- 2. 更新权限标识
UPDATE `sys_menu` SET `permission` = 'marketing:points:goods:query' WHERE `id` = 1103;
UPDATE `sys_menu` SET `permission` = 'marketing:points:goods:query' WHERE `id` = 1112;
UPDATE `sys_menu` SET `permission` = 'marketing:points:goods:create' WHERE `id` = 1113;
UPDATE `sys_menu` SET `permission` = 'marketing:points:goods:update' WHERE `id` = 1114;
UPDATE `sys_menu` SET `permission` = 'marketing:points:goods:delete' WHERE `id` = 1115;
UPDATE `sys_menu` SET `permission` = 'marketing:points:order:query' WHERE `id` = 1116;
UPDATE `sys_menu` SET `permission` = 'marketing:points:order:audit' WHERE `id` = 1117;
UPDATE `sys_menu` SET `permission` = 'marketing:points:order:verify' WHERE `id` = 1118;

-- 3. 验证结果
SELECT '修复完成！' AS message;
SELECT `version`, `checksum` FROM `flyway_schema_history` WHERE `version` = '58';
SELECT `id`, `name`, `permission` FROM `sys_menu` WHERE `id` IN (1103, 1112, 1113, 1114, 1115, 1116, 1117, 1118) ORDER BY `id`;









