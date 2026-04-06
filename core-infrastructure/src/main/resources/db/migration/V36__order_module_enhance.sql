-- ============================================
-- V36: 订单模块增强（订单模块开发）
-- ============================================

-- 辅助存储过程：安全添加列（如果不存在）
DELIMITER $$

DROP PROCEDURE IF EXISTS `add_column_if_not_exists`$$
CREATE PROCEDURE `add_column_if_not_exists`(
    IN table_name VARCHAR(128),
    IN column_name VARCHAR(128),
    IN column_definition TEXT
)
BEGIN
    DECLARE column_exists INT DEFAULT 0;
    
    SELECT COUNT(*) INTO column_exists
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = table_name
      AND COLUMN_NAME = column_name;
    
    IF column_exists = 0 THEN
        SET @sql = CONCAT('ALTER TABLE `', table_name, '` ADD COLUMN ', column_definition);
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$

DROP PROCEDURE IF EXISTS `add_index_if_not_exists`$$
CREATE PROCEDURE `add_index_if_not_exists`(
    IN table_name VARCHAR(128),
    IN index_name VARCHAR(128),
    IN index_definition TEXT
)
BEGIN
    DECLARE index_exists INT DEFAULT 0;
    
    SELECT COUNT(*) INTO index_exists
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = table_name
      AND INDEX_NAME = index_name;
    
    IF index_exists = 0 THEN
        SET @sql = CONCAT('ALTER TABLE `', table_name, '` ADD ', index_definition);
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$

DELIMITER ;

-- 订单明细表：添加专用金标识字段（来源于商品快照）
CALL add_column_if_not_exists('order_item', 'allow_special_fund', 
    '`allow_special_fund` TINYINT NOT NULL DEFAULT 0 COMMENT ''是否允许使用专用金：0-不允许，1-允许（来源于商品快照）'' AFTER `commission_amount`');

-- 订单表：更新订单状态枚举说明
-- 订单状态：1-待支付(WAIT_PAY), 2-已支付(PAID), 3-待自提(WAIT_PICKUP), 4-待发货(WAIT_SHIP), 
--           5-已发货(SHIPPED), 6-已完成(FINISHED), 7-已取消(CANCELLED), 8-已退款(REFUNDED)
-- 配送状态：1-待审核(WAIT_AUDIT), 2-已审核(AUDITED), 3-配送中(SHIPPING), 4-已送达(DELIVERED), 5-已拒收(REJECTED)

-- 订单配送表：添加物流公司和快递单号字段（用于快递发货）
-- 先添加 logistics_company
CALL add_column_if_not_exists('order_delivery', 'logistics_company', 
    '`logistics_company` VARCHAR(100) COMMENT ''物流公司名称（快递发货时必填）'' AFTER `delivery_no`');

-- 再添加 tracking_no（如果 logistics_company 存在则在其后，否则在 delivery_no 后）
-- 使用存储过程动态判断位置
DELIMITER $$

DROP PROCEDURE IF EXISTS `add_tracking_no_column`$$
CREATE PROCEDURE `add_tracking_no_column`()
BEGIN
    DECLARE logistics_company_exists INT DEFAULT 0;
    DECLARE tracking_no_exists INT DEFAULT 0;
    DECLARE column_position VARCHAR(100);
    
    -- 检查 tracking_no 是否已存在
    SELECT COUNT(*) INTO tracking_no_exists
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'order_delivery'
      AND COLUMN_NAME = 'tracking_no';
    
    -- 如果已存在则跳过
    IF tracking_no_exists = 0 THEN
        -- 检查 logistics_company 是否存在
        SELECT COUNT(*) INTO logistics_company_exists
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'order_delivery'
          AND COLUMN_NAME = 'logistics_company';
        
        -- 根据 logistics_company 是否存在决定位置
        IF logistics_company_exists > 0 THEN
            SET column_position = 'AFTER `logistics_company`';
        ELSE
            SET column_position = 'AFTER `delivery_no`';
        END IF;
        
        SET @sql = CONCAT('ALTER TABLE `order_delivery` ADD COLUMN `tracking_no` VARCHAR(100) COMMENT ''快递单号（快递发货时必填）'' ', column_position);
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$

DELIMITER ;

CALL add_tracking_no_column();
DROP PROCEDURE IF EXISTS `add_tracking_no_column`;

-- 添加快递单号唯一索引
CALL add_index_if_not_exists('order_delivery', 'uk_tracking_no', 
    'UNIQUE KEY `uk_tracking_no` (`tracking_no`)');

-- 订单支付明细表：添加外部支付回调时间字段
CALL add_column_if_not_exists('order_payment', 'callback_time', 
    '`callback_time` DATETIME COMMENT ''外部支付回调时间（微信/支付宝）'' AFTER `outer_trans_no`');

-- 客户优惠券表：添加使用金额字段（支持部分使用）
CALL add_column_if_not_exists('customer_coupon', 'used_amount', 
    '`used_amount` DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT ''已使用金额（元，支持部分使用）'' AFTER `amount`');

-- 清理临时存储过程
DROP PROCEDURE IF EXISTS `add_column_if_not_exists`;
DROP PROCEDURE IF EXISTS `add_index_if_not_exists`;

