-- ============================================
-- V70: 库存精度升级与原始单位记录
-- ============================================
-- 目标：将库存数量字段精度提升至 DECIMAL(18, 8)，以应对任意换算比例（如 1箱=3瓶）
-- 同时增加原始单位记录字段，便于审计和对账

-- 设置数据库名称变量（用于动态SQL）
SET @dbname = DATABASE();

-- 1. 提升 inventory 表数量字段精度
ALTER TABLE `inventory` 
    MODIFY COLUMN `quantity` DECIMAL(18, 8) DEFAULT 0.00000000 NOT NULL COMMENT '库存数量（主单位，8位小数）';

-- 2. 提升 inventory_batch 表数量字段精度
ALTER TABLE `inventory_batch` 
    MODIFY COLUMN `quantity` DECIMAL(18, 8) DEFAULT 0.00000000 NOT NULL COMMENT '批次数量（主单位，8位小数）';

-- 3. 提升 inventory_transaction 表数量字段精度，并添加原始单位记录字段
ALTER TABLE `inventory_transaction` 
    MODIFY COLUMN `quantity` DECIMAL(18, 8) NOT NULL COMMENT '数量（正数为增加，负数为减少，主单位，8位小数）',
    MODIFY COLUMN `before_quantity` DECIMAL(18, 8) COMMENT '操作前总库存数量（主单位，8位小数）',
    MODIFY COLUMN `after_quantity` DECIMAL(18, 8) COMMENT '操作后总库存数量（主单位，8位小数）',
    MODIFY COLUMN `batch_before_qty` DECIMAL(18, 8) COMMENT '操作前批次数量（主单位，8位小数）',
    MODIFY COLUMN `batch_after_qty` DECIMAL(18, 8) COMMENT '操作后批次数量（主单位，8位小数）';

-- 4. 添加原始单位记录字段（用于审计和对账）
-- 检查 original_unit 字段是否存在，如果不存在则添加
SET @tablename = 'inventory_transaction';
SET @colname = 'original_unit';
SET @preparedStatement = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS 
     WHERE table_schema = @dbname 
     AND table_name = @tablename 
     AND column_name = @colname) = 0,
    CONCAT('ALTER TABLE `', @tablename, '` ADD COLUMN `', @colname, '` VARCHAR(20) COMMENT ''业务发生时的原始单位（如：瓶、件）'' AFTER `unit`'),
    'SELECT 1'
));
PREPARE stmt FROM @preparedStatement;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 检查 original_quantity 字段是否存在，如果不存在则添加
SET @colname = 'original_quantity';
SET @preparedStatement = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS 
     WHERE table_schema = @dbname 
     AND table_name = @tablename 
     AND column_name = @colname) = 0,
    CONCAT('ALTER TABLE `', @tablename, '` ADD COLUMN `', @colname, '` DECIMAL(10, 4) COMMENT ''业务发生时的原始数量（保留4位小数，用于对账）'' AFTER `original_unit`'),
    'SELECT 1'
));
PREPARE stmt FROM @preparedStatement;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 5. 更新表注释
ALTER TABLE `inventory` COMMENT='库存快照表（8位小数精度，支持任意换算比例）';
ALTER TABLE `inventory_batch` COMMENT='库存批次表（8位小数精度，支持任意换算比例）';
ALTER TABLE `inventory_transaction` COMMENT='库存流水表（8位小数精度，记录原始单位和数量用于审计）';

