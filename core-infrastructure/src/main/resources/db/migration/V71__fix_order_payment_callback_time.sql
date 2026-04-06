-- ============================================
-- V71: 修复 order_payment 表 callback_time 字段
-- ============================================
-- 问题：V36 迁移脚本可能未正确执行，导致 callback_time 字段缺失
-- 解决方案：确保 callback_time 字段存在

-- 设置数据库名称变量（用于动态SQL）
SET @dbname = DATABASE();
SET @tablename = 'order_payment';
SET @colname = 'callback_time';

-- 检查 callback_time 字段是否存在，如果不存在则添加
SET @preparedStatement = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS 
     WHERE table_schema = @dbname 
     AND table_name = @tablename 
     AND column_name = @colname) = 0,
    CONCAT('ALTER TABLE `', @tablename, '` ADD COLUMN `', @colname, '` DATETIME COMMENT ''外部支付回调时间（微信/支付宝）'' AFTER `outer_trans_no`'),
    'SELECT 1'
));
PREPARE stmt FROM @preparedStatement;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;









