-- ============================================
-- V65: 确保客户优惠券表存在 used_amount 字段
-- ============================================

-- 检查并添加 used_amount 字段（如果不存在）
SET @column_exists = 0;
SELECT COUNT(*) INTO @column_exists
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'customer_coupon'
  AND COLUMN_NAME = 'used_amount';

SET @sql = IF(@column_exists = 0,
    'ALTER TABLE `customer_coupon`
        ADD COLUMN `used_amount` DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT ''已使用金额（元，支持部分使用）'' AFTER `amount`',
    'SELECT 1');

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;









