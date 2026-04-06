-- ============================================
-- V20: 库存模块核心修复（Final V3.0）
-- ============================================
-- 修复内容：
-- 1. inventory表：移除version乐观锁，改为write-after-read覆盖更新
-- 2. inventory_batch表：添加同源判定字段（source_type, source_id, source_line_no），成本价不可变
-- 3. inventory_transaction表：添加before_quantity, after_quantity, batch_before_qty, batch_after_qty字段
-- 4. 添加FIFO索引：idx_fifo(store_id, product_id, is_active, expire_date, created_at)
-- 5. 修改批次唯一性约束：基于同源判定

-- ============================================
-- 1. 修复inventory表（移除version乐观锁）
-- ============================================
SET @dbname = DATABASE();
SET @tablename = 'inventory';

-- 删除version字段（如果存在）
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

-- 更新注释
ALTER TABLE `inventory` COMMENT='库存快照表（只读，数据来源为inventory_batch聚合，write-after-read覆盖更新）';

-- ============================================
-- 2. 修复inventory_batch表（添加同源判定字段）
-- ============================================
SET @tablename = 'inventory_batch';

-- 添加source_type字段
SET @colname = 'source_type';
SET @preparedStatement = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS 
     WHERE table_schema = @dbname 
     AND table_name = @tablename 
     AND column_name = @colname) = 0,
    CONCAT('ALTER TABLE `', @tablename, '` ADD COLUMN `', @colname, '` VARCHAR(50) COMMENT ''来源类型：PURCHASE-采购，TRANSFER_IN-调拨入库，ADJUSTMENT-盘盈等'' AFTER `is_active`'),
    'SELECT 1'
));
PREPARE stmt FROM @preparedStatement;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 添加source_id字段
SET @colname = 'source_id';
SET @preparedStatement = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS 
     WHERE table_schema = @dbname 
     AND table_name = @tablename 
     AND column_name = @colname) = 0,
    CONCAT('ALTER TABLE `', @tablename, '` ADD COLUMN `', @colname, '` BIGINT COMMENT ''来源单据ID'' AFTER `source_type`'),
    'SELECT 1'
));
PREPARE stmt FROM @preparedStatement;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 添加source_line_no字段
SET @colname = 'source_line_no';
SET @preparedStatement = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS 
     WHERE table_schema = @dbname 
     AND table_name = @tablename 
     AND column_name = @colname) = 0,
    CONCAT('ALTER TABLE `', @tablename, '` ADD COLUMN `', @colname, '` INT COMMENT ''来源单据行号'' AFTER `source_id`'),
    'SELECT 1'
));
PREPARE stmt FROM @preparedStatement;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 修改cost_price注释（强调不可变）
ALTER TABLE `inventory_batch` MODIFY COLUMN `cost_price` DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '该批次的真实成本价（不可变，不允许修改）';

-- 删除旧的唯一索引（如果存在）
SET @indexname = 'uk_batch_store_product_batch';
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

-- 创建新的唯一索引（基于同源判定）
SET @indexname = 'uk_batch_same_source';
SET @preparedStatement = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS 
     WHERE table_schema = @dbname 
     AND table_name = @tablename 
     AND index_name = @indexname) = 0,
    CONCAT('ALTER TABLE `', @tablename, '` ADD UNIQUE KEY `', @indexname, '` (`store_id`, `product_id`, `batch_no`, `source_type`, `source_id`, `source_line_no`) COMMENT ''同源批次唯一性约束'''),
    'SELECT 1'
));
PREPARE stmt FROM @preparedStatement;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 删除旧的FIFO索引（如果存在）
SET @indexname = 'idx_store_product_active';
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

-- 创建FIFO索引（用于悲观锁查询）
SET @indexname = 'idx_fifo';
SET @preparedStatement = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS 
     WHERE table_schema = @dbname 
     AND table_name = @tablename 
     AND index_name = @indexname) = 0,
    CONCAT('ALTER TABLE `', @tablename, '` ADD KEY `', @indexname, '` (`store_id`, `product_id`, `is_active`, `expire_date`, `created_at`) COMMENT ''FIFO排序索引（用于悲观锁查询）'''),
    'SELECT 1'
));
PREPARE stmt FROM @preparedStatement;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 添加quantity非负约束（CHECK约束在MySQL 8.0.16+支持）
SET @preparedStatement = (SELECT IF(
    (SELECT VERSION()) >= '8.0.16',
    CONCAT('ALTER TABLE `', @tablename, '` ADD CONSTRAINT `chk_quantity_non_negative` CHECK (`quantity` >= 0)'),
    'SELECT 1'
));
PREPARE stmt FROM @preparedStatement;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 更新注释
ALTER TABLE `inventory_batch` COMMENT='库存批次表（核心写表，成本价不可变，同源合并）';

-- ============================================
-- 3. 修复inventory_transaction表（添加before/after字段）
-- ============================================
SET @tablename = 'inventory_transaction';

-- 添加before_quantity字段
SET @colname = 'before_quantity';
SET @preparedStatement = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS 
     WHERE table_schema = @dbname 
     AND table_name = @tablename 
     AND column_name = @colname) = 0,
    CONCAT('ALTER TABLE `', @tablename, '` ADD COLUMN `', @colname, '` DECIMAL(10,4) COMMENT ''操作前总库存数量'' AFTER `product_id`'),
    'SELECT 1'
));
PREPARE stmt FROM @preparedStatement;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 修改after_quantity字段（如果字段名是quantity_after，需要重命名）
SET @colname = 'quantity_after';
SET @preparedStatement = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS 
     WHERE table_schema = @dbname 
     AND table_name = @tablename 
     AND column_name = @colname) > 0,
    CONCAT('ALTER TABLE `', @tablename, '` CHANGE COLUMN `', @colname, '` `after_quantity` DECIMAL(10,4) NOT NULL COMMENT ''操作后总库存数量'''),
    'SELECT 1'
));
PREPARE stmt FROM @preparedStatement;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 如果after_quantity字段不存在，则添加
SET @colname = 'after_quantity';
SET @preparedStatement = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS 
     WHERE table_schema = @dbname 
     AND table_name = @tablename 
     AND column_name = @colname) = 0,
    CONCAT('ALTER TABLE `', @tablename, '` ADD COLUMN `', @colname, '` DECIMAL(10,4) NOT NULL COMMENT ''操作后总库存数量'' AFTER `before_quantity`'),
    'SELECT 1'
));
PREPARE stmt FROM @preparedStatement;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 添加batch_before_qty字段
SET @colname = 'batch_before_qty';
SET @preparedStatement = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS 
     WHERE table_schema = @dbname 
     AND table_name = @tablename 
     AND column_name = @colname) = 0,
    CONCAT('ALTER TABLE `', @tablename, '` ADD COLUMN `', @colname, '` DECIMAL(10,4) COMMENT ''操作前批次数量'' AFTER `batch_id`'),
    'SELECT 1'
));
PREPARE stmt FROM @preparedStatement;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 添加batch_after_qty字段
SET @colname = 'batch_after_qty';
SET @preparedStatement = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS 
     WHERE table_schema = @dbname 
     AND table_name = @tablename 
     AND column_name = @colname) = 0,
    CONCAT('ALTER TABLE `', @tablename, '` ADD COLUMN `', @colname, '` DECIMAL(10,4) COMMENT ''操作后批次数量'' AFTER `batch_before_qty`'),
    'SELECT 1'
));
PREPARE stmt FROM @preparedStatement;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 确保cost_price字段存在（记录当时批次的真实成本）
SET @colname = 'cost_price';
SET @preparedStatement = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS 
     WHERE table_schema = @dbname 
     AND table_name = @tablename 
     AND column_name = @colname) = 0,
    CONCAT('ALTER TABLE `', @tablename, '` ADD COLUMN `', @colname, '` DECIMAL(10,2) COMMENT ''当时批次的真实成本价'' AFTER `quantity`'),
    'SELECT 1'
));
PREPARE stmt FROM @preparedStatement;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 更新注释
ALTER TABLE `inventory_transaction` COMMENT='库存流水表（记录每次变动的明细，支持审计与回溯）';

-- ============================================
-- 4. 创建调拨明细表（用于审计）
-- ============================================
CREATE TABLE IF NOT EXISTS `inventory_transfer_detail` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `transfer_id` BIGINT NOT NULL COMMENT '调拨单ID',
    `from_batch_id` BIGINT NOT NULL COMMENT '调出批次ID',
    `to_batch_id` BIGINT NOT NULL COMMENT '调入批次ID',
    `quantity` DECIMAL(10,4) NOT NULL COMMENT '调拨数量',
    `cost_price` DECIMAL(10,2) NOT NULL COMMENT '批次成本价（继承自调出批次）',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_transfer_id` (`transfer_id`),
    KEY `idx_from_batch` (`from_batch_id`),
    KEY `idx_to_batch` (`to_batch_id`),
    CONSTRAINT `fk_transfer_detail_from_batch` FOREIGN KEY (`from_batch_id`) REFERENCES `inventory_batch` (`id`),
    CONSTRAINT `fk_transfer_detail_to_batch` FOREIGN KEY (`to_batch_id`) REFERENCES `inventory_batch` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='库存调拨明细表（用于审计）';


