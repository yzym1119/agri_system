-- ============================================
-- V40: 优惠券模板与营销规则完善
-- ============================================

-- 1. 优惠券模板表
CREATE TABLE IF NOT EXISTS `coupon_template` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `name` VARCHAR(100) NOT NULL COMMENT '模板名称',
    `type` TINYINT DEFAULT 1 COMMENT '类型：1-满减券，2-折扣券',
    `amount` DECIMAL(10,2) NOT NULL COMMENT '面额/折扣率',
    `min_order_amount` DECIMAL(10,2) DEFAULT 0.00 COMMENT '使用门槛（元）',
    `limit_category_id` BIGINT DEFAULT NULL COMMENT '限制分类ID（为空则全场通用）',
    `limit_product_id` BIGINT DEFAULT NULL COMMENT '限制商品ID（为空则不限）',
    `validity_type` TINYINT NOT NULL COMMENT '有效期类型：1-固定日期范围，2-领取后有效天数',
    `valid_start_time` DATETIME DEFAULT NULL COMMENT '固定有效期开始',
    `valid_end_time` DATETIME DEFAULT NULL COMMENT '固定有效期结束',
    `valid_days` INT DEFAULT 30 COMMENT '领取后有效天数',
    `total_count` INT DEFAULT -1 COMMENT '发放总量限制（-1不限）',
    `status` TINYINT DEFAULT 1 COMMENT '状态：1-启用，0-停用',
    `remark` VARCHAR(500) COMMENT '备注',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_status` (`status`),
    KEY `idx_limit_category` (`limit_category_id`),
    KEY `idx_limit_product` (`limit_product_id`),
    CONSTRAINT `fk_template_limit_category` FOREIGN KEY (`limit_category_id`) REFERENCES `product_category` (`id`),
    CONSTRAINT `fk_template_limit_product` FOREIGN KEY (`limit_product_id`) REFERENCES `product` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='优惠券模板表';

-- 2. 完善充值营销规则表（添加优惠券模板关联）
-- 检查字段是否存在，如果不存在则添加
SET @column_exists = 0;
SELECT COUNT(*) INTO @column_exists
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'marketing_recharge_rule'
  AND COLUMN_NAME = 'gift_template_id';

SET @sql = IF(@column_exists = 0,
    'ALTER TABLE `marketing_recharge_rule`
        ADD COLUMN `gift_template_id` BIGINT DEFAULT NULL COMMENT ''赠送优惠券模板ID'' AFTER `gift_special_fund`,
        ADD COLUMN `gift_template_count` INT DEFAULT 1 COMMENT ''赠送数量'' AFTER `gift_template_id`,
        ADD KEY `idx_gift_template` (`gift_template_id`),
        ADD CONSTRAINT `fk_recharge_rule_template` FOREIGN KEY (`gift_template_id`) REFERENCES `coupon_template` (`id`)',
    'SELECT 1');

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 3. 完善VIP周期权益表（修改字段关联到模板表）
-- 使用存储过程安全处理字段修改
DELIMITER $$

DROP PROCEDURE IF EXISTS `modify_vip_rights_table`$$
CREATE PROCEDURE `modify_vip_rights_table`()
BEGIN
    DECLARE table_exists INT DEFAULT 0;
    DECLARE column_exists INT DEFAULT 0;
    DECLARE fk_exists INT DEFAULT 0;
    DECLARE idx_exists INT DEFAULT 0;
    
    -- 检查表是否存在
    SELECT COUNT(*) INTO table_exists
    FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'marketing_vip_rights';
    
    IF table_exists > 0 THEN
        -- 表已存在，检查并修改字段
        -- 检查 template_coupon_id 字段是否存在
        SELECT COUNT(*) INTO column_exists
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'marketing_vip_rights'
          AND COLUMN_NAME = 'template_coupon_id';
        
        -- 检查 template_id 字段是否存在
        SELECT COUNT(*) INTO @template_id_exists
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'marketing_vip_rights'
          AND COLUMN_NAME = 'template_id';
        
        -- 如果存在 template_coupon_id，重命名为 template_id
        IF column_exists > 0 AND @template_id_exists = 0 THEN
            -- 删除旧外键（如果存在）
            SELECT COUNT(*) INTO fk_exists
            FROM information_schema.KEY_COLUMN_USAGE
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'marketing_vip_rights'
              AND CONSTRAINT_NAME = 'fk_vip_rights_template_coupon';
            
            IF fk_exists > 0 THEN
                SET @sql = 'ALTER TABLE `marketing_vip_rights` DROP FOREIGN KEY `fk_vip_rights_template_coupon`';
                PREPARE stmt FROM @sql;
                EXECUTE stmt;
                DEALLOCATE PREPARE stmt;
            END IF;
            
            -- 删除旧索引（如果存在）
            SELECT COUNT(*) INTO idx_exists
            FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'marketing_vip_rights'
              AND INDEX_NAME = 'idx_template_coupon';
            
            IF idx_exists > 0 THEN
                SET @sql = 'ALTER TABLE `marketing_vip_rights` DROP INDEX `idx_template_coupon`';
                PREPARE stmt FROM @sql;
                EXECUTE stmt;
                DEALLOCATE PREPARE stmt;
            END IF;
            
            -- 重命名字段
            SET @sql = 'ALTER TABLE `marketing_vip_rights` 
                        CHANGE COLUMN `template_coupon_id` `template_id` BIGINT DEFAULT NULL COMMENT ''赠送的优惠券模板ID''';
            PREPARE stmt FROM @sql;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
        END IF;
        
        -- 检查并添加 template_count 字段
        SELECT COUNT(*) INTO @count_exists
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'marketing_vip_rights'
          AND COLUMN_NAME = 'template_count';
        
        IF @count_exists = 0 THEN
            SET @sql = 'ALTER TABLE `marketing_vip_rights` 
                        ADD COLUMN `template_count` INT DEFAULT 1 COMMENT ''赠送数量'' AFTER `template_id`';
            PREPARE stmt FROM @sql;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
        END IF;
        
        -- 检查并添加索引和外键
        SELECT COUNT(*) INTO idx_exists
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'marketing_vip_rights'
          AND INDEX_NAME = 'idx_template_id';
        
        IF idx_exists = 0 THEN
            SET @sql = 'ALTER TABLE `marketing_vip_rights` ADD KEY `idx_template_id` (`template_id`)';
            PREPARE stmt FROM @sql;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
        END IF;
        
        SELECT COUNT(*) INTO fk_exists
        FROM information_schema.KEY_COLUMN_USAGE
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'marketing_vip_rights'
          AND CONSTRAINT_NAME = 'fk_vip_rights_template';
        
        IF fk_exists = 0 THEN
            SET @sql = 'ALTER TABLE `marketing_vip_rights` 
                        ADD CONSTRAINT `fk_vip_rights_template` FOREIGN KEY (`template_id`) REFERENCES `coupon_template` (`id`)';
            PREPARE stmt FROM @sql;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
        END IF;
        
        -- 删除 coupon_valid_days 字段（如果存在，因为有效期由模板管理）
        SELECT COUNT(*) INTO @valid_days_exists
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'marketing_vip_rights'
          AND COLUMN_NAME = 'coupon_valid_days';
        
        IF @valid_days_exists > 0 THEN
            SET @sql = 'ALTER TABLE `marketing_vip_rights` DROP COLUMN `coupon_valid_days`';
            PREPARE stmt FROM @sql;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
        END IF;
    ELSE
        -- 表不存在，创建表
        CREATE TABLE IF NOT EXISTS `marketing_vip_rights` (
            `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
            `vip_level` TINYINT NOT NULL COMMENT '关联VIP等级',
            `benefit_type` TINYINT NOT NULL COMMENT '权益类型：1-送券，2-送专用金',
            `benefit_value` DECIMAL(10,2) DEFAULT 0.00 COMMENT '专用金金额（元）',
            `template_id` BIGINT DEFAULT NULL COMMENT '赠送的优惠券模板ID',
            `template_count` INT DEFAULT 1 COMMENT '赠送数量',
            `status` TINYINT DEFAULT 1 COMMENT '状态：1-启用，0-停用',
            `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
            `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
            PRIMARY KEY (`id`),
            KEY `idx_vip_level` (`vip_level`),
            KEY `idx_status_type` (`status`, `benefit_type`),
            KEY `idx_template_id` (`template_id`),
            CONSTRAINT `fk_vip_rights_template` FOREIGN KEY (`template_id`) REFERENCES `coupon_template` (`id`)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='VIP每月周期权益表';
    END IF;
END$$

DELIMITER ;

CALL `modify_vip_rights_table`();
DROP PROCEDURE IF EXISTS `modify_vip_rights_table`;

-- 4. 修改 customer_coupon 表，添加模板ID追溯字段
SET @column_exists = 0;
SELECT COUNT(*) INTO @column_exists
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'customer_coupon'
  AND COLUMN_NAME = 'template_id';

SET @sql = IF(@column_exists = 0,
    'ALTER TABLE `customer_coupon`
        ADD COLUMN `template_id` BIGINT COMMENT ''来源模板ID'' AFTER `customer_id`,
        ADD KEY `idx_template_id` (`template_id`),
        ADD CONSTRAINT `fk_coupon_template` FOREIGN KEY (`template_id`) REFERENCES `coupon_template` (`id`)',
    'SELECT 1');

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

