-- V120: 销售批次归属、价格审计与库存决策留痕
-- 快照列可空以兼容历史订单；本迁移不回填或覆盖历史值。

ALTER TABLE `order_item` ADD COLUMN `base_unit_id` BIGINT NULL;
ALTER TABLE `order_item` ADD COLUMN `base_quantity` DECIMAL(20,8) NULL;
ALTER TABLE `order_item` ADD COLUMN `unit_cost` DECIMAL(20,8) NULL;

ALTER TABLE `order_item` ADD KEY `idx_order_item_base_unit` (`base_unit_id`);
ALTER TABLE `order_item`
    ADD CONSTRAINT `fk_order_item_base_unit` FOREIGN KEY (`base_unit_id`) REFERENCES `product_unit` (`id`);
ALTER TABLE `order_item`
    ADD CONSTRAINT `chk_order_item_base_quantity` CHECK (`base_quantity` IS NULL OR `base_quantity` >= 0);
ALTER TABLE `order_item`
    ADD CONSTRAINT `chk_order_item_unit_cost` CHECK (`unit_cost` IS NULL OR `unit_cost` >= 0);

CREATE TABLE IF NOT EXISTS `order_item_batch_allocation` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `order_item_id` BIGINT NOT NULL,
    `batch_id` BIGINT,
    `location_id` BIGINT,
    `allocated_base_quantity` DECIMAL(20,8) NOT NULL,
    `batch_unit_cost` DECIMAL(20,8),
    `allocated_cost` DECIMAL(20,8),
    `evidence_level` VARCHAR(30) NOT NULL,
    `allocation_rule` VARCHAR(50),
    `allocation_rule_version` VARCHAR(50),
    `source_movement_id` BIGINT,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `created_by` BIGINT,
    PRIMARY KEY (`id`),
    KEY `idx_order_item_batch_allocation_item` (`order_item_id`),
    KEY `idx_order_item_batch_allocation_batch` (`batch_id`),
    KEY `idx_order_item_batch_allocation_evidence` (`evidence_level`),
    CONSTRAINT `fk_order_item_batch_allocation_item` FOREIGN KEY (`order_item_id`) REFERENCES `order_item` (`id`),
    CONSTRAINT `fk_order_item_batch_allocation_batch` FOREIGN KEY (`batch_id`) REFERENCES `inventory_receipt_batch` (`id`),
    CONSTRAINT `fk_order_item_batch_allocation_location` FOREIGN KEY (`location_id`) REFERENCES `inventory_location` (`id`),
    CONSTRAINT `fk_order_item_batch_allocation_movement` FOREIGN KEY (`source_movement_id`) REFERENCES `inventory_movement` (`id`),
    CONSTRAINT `chk_order_item_batch_allocation_quantity` CHECK (`allocated_base_quantity` > 0),
    CONSTRAINT `chk_order_item_batch_allocation_cost` CHECK (`batch_unit_cost` IS NULL OR `batch_unit_cost` >= 0),
    CONSTRAINT `chk_order_item_batch_allocation_evidence` CHECK (`evidence_level` IN ('SCANNED', 'FULFILLMENT_CONFIRMED', 'RULE_ALLOCATED', 'UNKNOWN'))
);

CREATE TABLE IF NOT EXISTS `order_item_price_audit` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `order_item_id` BIGINT NOT NULL,
    `product_id` BIGINT,
    `sale_unit_id` BIGINT,
    `suggested_unit_price` DECIMAL(20,8) NOT NULL,
    `final_unit_price` DECIMAL(20,8) NOT NULL,
    `price_difference` DECIMAL(20,8) NOT NULL,
    `price_rule_version` VARCHAR(50),
    `change_reason` VARCHAR(500),
    `operator_id` BIGINT,
    `confirmer_id` BIGINT,
    `decided_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_order_item_price_audit_item` (`order_item_id`),
    KEY `idx_order_item_price_audit_product_time` (`product_id`, `decided_at`),
    CONSTRAINT `fk_order_item_price_audit_item` FOREIGN KEY (`order_item_id`) REFERENCES `order_item` (`id`),
    CONSTRAINT `fk_order_item_price_audit_product` FOREIGN KEY (`product_id`) REFERENCES `product` (`id`),
    CONSTRAINT `fk_order_item_price_audit_unit` FOREIGN KEY (`sale_unit_id`) REFERENCES `product_unit` (`id`),
    CONSTRAINT `fk_order_item_price_audit_operator` FOREIGN KEY (`operator_id`) REFERENCES `staff` (`id`),
    CONSTRAINT `fk_order_item_price_audit_confirmer` FOREIGN KEY (`confirmer_id`) REFERENCES `staff` (`id`),
    CONSTRAINT `chk_order_item_price_audit_prices` CHECK (`suggested_unit_price` >= 0 AND `final_unit_price` >= 0)
);

CREATE TABLE IF NOT EXISTS `inventory_decision_log` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `risk_kind` VARCHAR(40) NOT NULL,
    `entity_type` VARCHAR(40) NOT NULL,
    `entity_id` BIGINT NOT NULL,
    `suggested_action` VARCHAR(50),
    `decision_action` VARCHAR(50) NOT NULL,
    `decision_reason` VARCHAR(1000) NOT NULL,
    `operator_id` BIGINT,
    `confirmer_id` BIGINT,
    `decided_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_inventory_decision_entity` (`entity_type`, `entity_id`),
    KEY `idx_inventory_decision_risk_time` (`risk_kind`, `decided_at`),
    CONSTRAINT `fk_inventory_decision_operator` FOREIGN KEY (`operator_id`) REFERENCES `staff` (`id`),
    CONSTRAINT `fk_inventory_decision_confirmer` FOREIGN KEY (`confirmer_id`) REFERENCES `staff` (`id`)
);
