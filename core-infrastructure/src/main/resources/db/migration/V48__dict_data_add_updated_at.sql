-- ============================================
-- V48: sys_dict_data表添加updated_at字段
-- ============================================

SET @column_exists = 0;
SELECT COUNT(*) INTO @column_exists
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'sys_dict_data'
  AND COLUMN_NAME = 'updated_at';

SET @sql = IF(@column_exists = 0,
    'ALTER TABLE `sys_dict_data`
        ADD COLUMN `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT ''更新时间'' AFTER `created_at`',
    'SELECT 1');

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;






