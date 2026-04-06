-- ============================================
-- V47: 员工表添加逻辑删除字段
-- ============================================

-- 添加 deleted 字段（如果不存在）
SET @column_exists = 0;
SELECT COUNT(*) INTO @column_exists
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'staff'
  AND COLUMN_NAME = 'deleted';

SET @sql = IF(@column_exists = 0,
    'ALTER TABLE `staff`
        ADD COLUMN `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT ''逻辑删除：1-已删，0-未删'' AFTER `status`',
    'SELECT 1');

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 为 deleted 字段添加索引（如果不存在）
SET @index_exists = 0;
SELECT COUNT(*) INTO @index_exists
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'staff'
  AND INDEX_NAME = 'idx_staff_deleted';

SET @sql = IF(@index_exists = 0,
    'ALTER TABLE `staff` ADD INDEX `idx_staff_deleted` (`deleted`)',
    'SELECT 1');

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;




























