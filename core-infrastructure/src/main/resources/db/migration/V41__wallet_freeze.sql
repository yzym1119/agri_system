-- ============================================
-- V41: 钱包增强（冻结字段）
-- ============================================

-- 1. 增强钱包表，添加冻结金额字段
SET @column_exists = 0;
SELECT COUNT(*) INTO @column_exists
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'customer_wallet'
  AND COLUMN_NAME = 'frozen_balance';

SET @sql = IF(@column_exists = 0,
    'ALTER TABLE `customer_wallet`
        ADD COLUMN `frozen_balance` DECIMAL(10,2) DEFAULT 0.00 COMMENT ''冻结金额/积分'' AFTER `balance`',
    'SELECT 1');

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2. 检查并创建 VIP 每月权益发放记录表（防重发）
SET @table_exists = 0;
SELECT COUNT(*) INTO @table_exists
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'vip_monthly_record';

SET @sql = IF(@table_exists = 0,
    'CREATE TABLE IF NOT EXISTS `vip_monthly_record` (
        `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT ''主键ID'',
        `customer_id` BIGINT NOT NULL COMMENT ''客户ID'',
        `year_month` VARCHAR(7) NOT NULL COMMENT ''发放月份，如 2023-10'',
        `vip_level` TINYINT NOT NULL COMMENT ''VIP等级'',
        `grant_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT ''发放时间'',
        PRIMARY KEY (`id`),
        UNIQUE KEY `uk_customer_month` (`customer_id`, `year_month`),
        KEY `idx_customer_id` (`customer_id`),
        KEY `idx_year_month` (`year_month`),
        CONSTRAINT `fk_vip_monthly_customer` FOREIGN KEY (`customer_id`) REFERENCES `customer` (`id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT=''VIP每月权益发放记录表''',
    'SELECT 1');

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;







