-- ============================================
-- V6_1: 库存模块增强（批次表 + FIFO + 强一致性）
-- ============================================
-- 说明：
-- 1. inventory表改为总库存快照表（仅用于列表页展示，数据来源必须是inventory_batch的聚合结果）
-- 2. 新增inventory_batch表（核心批次表，所有写操作都在此表）
-- 3. 增强inventory_transaction表（添加batch_id字段）

-- ============================================
-- 1. 修改inventory表为快照表
-- ============================================

-- 删除旧索引（如果存在）
SET @dbname = DATABASE();
SET @tablename = 'inventory';
SET @indexname = 'uk_inventory_store_product';
SET @preparedStatement = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS 
     WHERE table_schema = @dbname 
     AND table_name = @tablename 
     AND index_name = @indexname) > 0,
    CONCAT('ALTER TABLE `', @tablename, '` DROP INDEX `', @indexname, '`'),
    'SELECT 1'
));
PREPARE stmt FROM @preparedStatement;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @indexname = 'idx_expiry_date';
SET @preparedStatement = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS 
     WHERE table_schema = @dbname 
     AND table_name = @tablename 
     AND index_name = @indexname) > 0,
    CONCAT('ALTER TABLE `', @tablename, '` DROP INDEX `', @indexname, '`'),
    'SELECT 1'
));
PREPARE stmt FROM @preparedStatement;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 删除旧字段（如果存在）
SET @colname = 'unit';
SET @preparedStatement = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS 
     WHERE table_schema = @dbname 
     AND table_name = @tablename 
     AND column_name = @colname) > 0,
    CONCAT('ALTER TABLE `', @tablename, '` DROP COLUMN `', @colname, '`'),
    'SELECT 1'
));
PREPARE stmt FROM @preparedStatement;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @colname = 'production_date';
SET @preparedStatement = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS 
     WHERE table_schema = @dbname 
     AND table_name = @tablename 
     AND column_name = @colname) > 0,
    CONCAT('ALTER TABLE `', @tablename, '` DROP COLUMN `', @colname, '`'),
    'SELECT 1'
));
PREPARE stmt FROM @preparedStatement;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @colname = 'expiry_date';
SET @preparedStatement = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS 
     WHERE table_schema = @dbname 
     AND table_name = @tablename 
     AND column_name = @colname) > 0,
    CONCAT('ALTER TABLE `', @tablename, '` DROP COLUMN `', @colname, '`'),
    'SELECT 1'
));
PREPARE stmt FROM @preparedStatement;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @colname = 'batch_no';
SET @preparedStatement = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS 
     WHERE table_schema = @dbname 
     AND table_name = @tablename 
     AND column_name = @colname) > 0,
    CONCAT('ALTER TABLE `', @tablename, '` DROP COLUMN `', @colname, '`'),
    'SELECT 1'
));
PREPARE stmt FROM @preparedStatement;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @colname = 'version';
SET @preparedStatement = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS 
     WHERE table_schema = @dbname 
     AND table_name = @tablename 
     AND column_name = @colname) > 0,
    CONCAT('ALTER TABLE `', @tablename, '` DROP COLUMN `', @colname, '`'),
    'SELECT 1'
));
PREPARE stmt FROM @preparedStatement;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 修改唯一索引为(store_id, product_id)，确保每个门店每个商品只有一条快照记录
-- 先检查索引是否已存在，如果不存在则添加
SET @indexname = 'uk_inventory_store_product';
SET @preparedStatement = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS 
     WHERE table_schema = @dbname 
     AND table_name = @tablename 
     AND index_name = @indexname) = 0,
    CONCAT('ALTER TABLE `', @tablename, '` ADD UNIQUE KEY `', @indexname, '` (`store_id`, `product_id`)'),
    'SELECT 1'
));
PREPARE stmt FROM @preparedStatement;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 添加注释说明
ALTER TABLE `inventory` COMMENT='库存快照表（只读，数据来源为inventory_batch聚合）';

-- ============================================
-- 2. 创建批次表（核心写表）
-- ============================================
CREATE TABLE IF NOT EXISTS `inventory_batch` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `store_id` BIGINT NOT NULL COMMENT '门店ID',
    `product_id` BIGINT NOT NULL COMMENT '商品ID',
    `batch_no` VARCHAR(50) NOT NULL COMMENT '批次号（手动输入或系统生成）',
    `production_date` DATE COMMENT '生产日期',
    `expire_date` DATE COMMENT '有效期至',
    `quantity` DECIMAL(10,4) NOT NULL DEFAULT 0.0000 COMMENT '当前剩余数量',
    `cost_price` DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '该批次的成本价（移动加权平均）',
    `is_active` TINYINT NOT NULL DEFAULT 1 COMMENT '是否有效：1-有效，0-归档/售罄',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_batch_store_product_batch` (`store_id`, `product_id`, `batch_no`),
    KEY `idx_store_product_active` (`store_id`, `product_id`, `is_active`),
    KEY `idx_expire_date` (`expire_date`),
    KEY `idx_created_at` (`created_at`),
    CONSTRAINT `fk_batch_store` FOREIGN KEY (`store_id`) REFERENCES `store` (`id`),
    CONSTRAINT `fk_batch_product` FOREIGN KEY (`product_id`) REFERENCES `product` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='库存批次表（核心写表）';

-- ============================================
-- 3. 增强库存流水表（添加batch_id字段）
-- ============================================
SET @tablename = 'inventory_transaction';

-- 检查并添加 batch_id 字段
SET @colname = 'batch_id';
SET @preparedStatement = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS 
     WHERE table_schema = @dbname 
     AND table_name = @tablename 
     AND column_name = @colname) = 0,
    CONCAT('ALTER TABLE `', @tablename, '` ADD COLUMN `', @colname, '` BIGINT COMMENT ''批次ID（关联inventory_batch表）'' AFTER `product_id`'),
    'SELECT 1'
));
PREPARE stmt FROM @preparedStatement;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 检查并添加 cost_price 字段
SET @colname = 'cost_price';
SET @preparedStatement = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS 
     WHERE table_schema = @dbname 
     AND table_name = @tablename 
     AND column_name = @colname) = 0,
    CONCAT('ALTER TABLE `', @tablename, '` ADD COLUMN `', @colname, '` DECIMAL(10,2) COMMENT ''成本价（记录本次变动的成本价）'' AFTER `quantity`'),
    'SELECT 1'
));
PREPARE stmt FROM @preparedStatement;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 检查并添加 idx_batch_id 索引
SET @indexname = 'idx_batch_id';
SET @preparedStatement = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS 
     WHERE table_schema = @dbname 
     AND table_name = @tablename 
     AND index_name = @indexname) = 0,
    CONCAT('ALTER TABLE `', @tablename, '` ADD KEY `', @indexname, '` (`batch_id`)'),
    'SELECT 1'
));
PREPARE stmt FROM @preparedStatement;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 检查并添加外键约束
SET @constraintname = 'fk_transaction_batch';
SET @preparedStatement = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS 
     WHERE table_schema = @dbname 
     AND table_name = @tablename 
     AND constraint_name = @constraintname) = 0,
    CONCAT('ALTER TABLE `', @tablename, '` ADD CONSTRAINT `', @constraintname, '` FOREIGN KEY (`batch_id`) REFERENCES `inventory_batch` (`id`)'),
    'SELECT 1'
));
PREPARE stmt FROM @preparedStatement;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 修改注释
ALTER TABLE `inventory_transaction` COMMENT='库存流水表（记录每次变动的批次、数量、成本价）';
