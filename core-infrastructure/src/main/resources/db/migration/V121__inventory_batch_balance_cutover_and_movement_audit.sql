-- V121: 批次货位余额切换状态与移动审计补强。
-- 余额行不能推断启用状态；每个 store+SKU 必须显式声明权威余额模式。

CREATE TABLE IF NOT EXISTS `inventory_balance_feature_status` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `store_id` BIGINT NOT NULL,
    `product_id` BIGINT NOT NULL,
    `balance_mode` VARCHAR(30) NOT NULL DEFAULT 'LEGACY',
    `cutover_at` DATETIME,
    `cutover_by` BIGINT,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_inventory_balance_feature_store_product` (`store_id`, `product_id`),
    CONSTRAINT `fk_inventory_balance_feature_store` FOREIGN KEY (`store_id`) REFERENCES `store` (`id`),
    CONSTRAINT `fk_inventory_balance_feature_product` FOREIGN KEY (`product_id`) REFERENCES `product` (`id`),
    CONSTRAINT `fk_inventory_balance_feature_operator` FOREIGN KEY (`cutover_by`) REFERENCES `staff` (`id`),
    CONSTRAINT `chk_inventory_balance_feature_mode` CHECK (`balance_mode` IN ('LEGACY', 'BATCH_LOCATION'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='SKU库存余额权威来源切换状态';

ALTER TABLE `inventory_location` MODIFY COLUMN `code` VARCHAR(64) NULL;
ALTER TABLE `inventory_movement` ADD COLUMN `transfer_id` VARCHAR(64) NULL AFTER `movement_type`;
ALTER TABLE `inventory_movement` ADD KEY `idx_inventory_movement_transfer` (`transfer_id`, `occurred_at`);
ALTER TABLE `inventory_movement` DROP CONSTRAINT `chk_inventory_movement_type`;
ALTER TABLE `inventory_movement`
    ADD CONSTRAINT `chk_inventory_movement_type` CHECK (`movement_type` IN
        ('REPLENISHMENT', 'PICK', 'TRANSFER', 'COUNT_ADJUSTMENT', 'RETURN'));

/*!80016 CREATE TRIGGER `trg_inventory_movement_no_update`
    BEFORE UPDATE ON `inventory_movement` FOR EACH ROW
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'inventory_movement is append-only' */;
/*!80016 CREATE TRIGGER `trg_inventory_movement_no_delete`
    BEFORE DELETE ON `inventory_movement` FOR EACH ROW
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'inventory_movement is append-only' */;
