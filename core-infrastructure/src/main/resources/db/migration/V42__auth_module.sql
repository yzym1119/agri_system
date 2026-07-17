-- ============================================
-- V42: 认证授权模块
-- ============================================

-- 1. 员工表添加密码字段
SET @column_exists = 0;
SELECT COUNT(*) INTO @column_exists
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'staff'
  AND COLUMN_NAME = 'password';

SET @sql = IF(@column_exists = 0,
    'ALTER TABLE `staff`
        ADD COLUMN `password` VARCHAR(255) COMMENT ''密码（BCrypt加密）'' AFTER `phone`',
    'SELECT 1');

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2. 确保customer_identity表存在（如果不存在则创建）
SET @table_exists = 0;
SELECT COUNT(*) INTO @table_exists
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'customer_identity';

SET @sql = IF(@table_exists = 0,
    'CREATE TABLE IF NOT EXISTS `customer_identity` (
        `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT ''主键ID'',
        `customer_id` BIGINT NOT NULL COMMENT ''客户ID'',
        `identity_type` VARCHAR(20) NOT NULL COMMENT ''身份类型：WECHAT_OPENID-微信OpenID，PHONE-手机号，OTHER-其他'',
        `identity_value` VARCHAR(200) NOT NULL COMMENT ''身份值（OpenID/手机号等）'',
        `session_key` VARCHAR(255) COMMENT ''微信SessionKey（仅微信登录时使用）'',
        `status` TINYINT NOT NULL DEFAULT 1 COMMENT ''状态：1-正常，2-停用'',
        `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT ''创建时间'',
        `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT ''更新时间'',
        PRIMARY KEY (`id`),
        UNIQUE KEY `uk_identity_type_value` (`identity_type`, `identity_value`),
        KEY `idx_customer_id` (`customer_id`),
        CONSTRAINT `fk_identity_customer` FOREIGN KEY (`customer_id`) REFERENCES `customer` (`id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT=''客户身份表''',
    'SELECT 1');

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 3. 客户身份表添加session_key字段（如果不存在）
SET @column_exists = 0;
SELECT COUNT(*) INTO @column_exists
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'customer_identity'
  AND COLUMN_NAME = 'session_key';

SET @sql = IF(@column_exists = 0,
    'ALTER TABLE `customer_identity`
        ADD COLUMN `session_key` VARCHAR(255) COMMENT ''微信SessionKey（仅微信登录时使用）'' AFTER `identity_value`',
    'SELECT 1');

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;





