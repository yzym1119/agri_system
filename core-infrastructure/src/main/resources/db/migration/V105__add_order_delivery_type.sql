-- ============================================
-- V105: 配送单补充配送方式字段
-- ============================================

DELIMITER $$

DROP PROCEDURE IF EXISTS `add_column_if_not_exists`$$
CREATE PROCEDURE `add_column_if_not_exists`(
    IN p_table_name VARCHAR(128),
    IN p_column_name VARCHAR(128),
    IN p_column_definition TEXT
)
BEGIN
    DECLARE column_exists INT DEFAULT 0;

    SELECT COUNT(*) INTO column_exists
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = p_table_name
      AND COLUMN_NAME = p_column_name;

    IF column_exists = 0 THEN
        SET @sql = CONCAT('ALTER TABLE `', p_table_name, '` ADD COLUMN ', p_column_definition);
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$

DROP PROCEDURE IF EXISTS `add_index_if_not_exists`$$
CREATE PROCEDURE `add_index_if_not_exists`(
    IN p_table_name VARCHAR(128),
    IN p_index_name VARCHAR(128),
    IN p_index_definition TEXT
)
BEGIN
    DECLARE index_exists INT DEFAULT 0;

    SELECT COUNT(*) INTO index_exists
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = p_table_name
      AND INDEX_NAME = p_index_name;

    IF index_exists = 0 THEN
        SET @sql = CONCAT('ALTER TABLE `', p_table_name, '` ADD ', p_index_definition);
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$

DELIMITER ;

CALL add_column_if_not_exists(
    'order_delivery',
    'delivery_type',
    '`delivery_type` TINYINT DEFAULT NULL COMMENT ''配送方式：2-同城配送，3-快递配送'' AFTER `delivery_phone`'
);

SET @sql = '
    UPDATE `order_delivery` od
    JOIN `order` o ON o.id = od.order_id
    SET od.delivery_type = CASE
        WHEN od.delivery_staff_id IS NOT NULL THEN 2
        ELSE 3
    END
    WHERE od.delivery_type IS NULL
      AND o.order_type = 3
';
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CALL add_index_if_not_exists(
    'order_delivery',
    'idx_delivery_type',
    'KEY `idx_delivery_type` (`delivery_type`)'
);

DROP PROCEDURE IF EXISTS `add_column_if_not_exists`;
DROP PROCEDURE IF EXISTS `add_index_if_not_exists`;
