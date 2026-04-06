-- ============================================
-- 修复 Flyway V58 迁移校验和
-- ============================================
-- 此脚本用于修复 V58 迁移文件的校验和不匹配问题
-- 执行方式：在数据库中直接执行此 SQL

-- 更新 V58 迁移的校验和
-- 新的校验和是根据修复后的 V58 文件计算得出的
UPDATE `flyway_schema_history` 
SET `checksum` = 14658361 
WHERE `version` = '58' AND `description` LIKE '%客户管理与营销模块权限%';

-- 验证更新结果
SELECT `version`, `description`, `checksum`, `installed_on` 
FROM `flyway_schema_history` 
WHERE `version` = '58';









