-- ============================================
-- Flyway 修复脚本：清理 V20 失败的迁移记录
-- ============================================
-- 使用说明：
-- 1. 如果 V20 迁移失败，Flyway 会在 flyway_schema_history 表中标记为失败
-- 2. 执行此脚本可以删除失败的记录，允许重新执行迁移
-- 3. 执行前请确保数据库状态正确（部分迁移可能已经执行）
--
-- 执行方式：
-- mysql -u root -p agri_system < scripts/repair_flyway_v20.sql
-- 或者在 MySQL 客户端中直接执行此脚本
-- ============================================

USE agri_system;

-- 查看当前 V20 的状态
SELECT `version`, `description`, `type`, `script`, `installed_on`, `success`, `installed_rank`
FROM `flyway_schema_history` 
WHERE `version` = '20' 
ORDER BY `installed_rank` DESC;

-- 删除 V20 的失败记录
DELETE FROM `flyway_schema_history` 
WHERE `version` = '20' 
AND `success` = 0;

-- 如果 V20 已经部分执行，也可以删除所有相关记录（包括成功的）
-- 取消下面的注释以启用（谨慎使用）：
-- DELETE FROM `flyway_schema_history` WHERE `version` = '20';

-- 查看修复后的状态
SELECT `version`, `description`, `type`, `script`, `installed_on`, `success` 
FROM `flyway_schema_history` 
WHERE `version` = '20' 
ORDER BY `installed_rank` DESC 
LIMIT 5;

-- 如果上面的查询返回空结果，说明修复成功，可以重新启动应用让 Flyway 重新执行迁移








