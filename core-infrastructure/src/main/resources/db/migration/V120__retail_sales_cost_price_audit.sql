-- V120: 销售批次归属、价格审计与库存决策留痕
-- 快照列可空以兼容历史订单；本迁移不回填或覆盖历史值。
-- 项目部署基线为 MySQL 8.2（CHECK 约束和触发器在 MySQL >= 8.0.16 强制执行）。

ALTER TABLE `order_item` ADD COLUMN `base_unit_id` BIGINT NULL;
ALTER TABLE `order_item` ADD COLUMN `base_quantity` DECIMAL(20,8) NULL;
ALTER TABLE `order_item` ADD COLUMN `unit_cost` DECIMAL(20,8) NULL;

ALTER TABLE `order_item` ADD UNIQUE KEY `uk_order_item_id_product` (`id`, `product_id`);
ALTER TABLE `order_item` ADD KEY `idx_order_item_base_unit` (`base_unit_id`);
ALTER TABLE `order_item`
    ADD CONSTRAINT `fk_order_item_base_unit` FOREIGN KEY (`base_unit_id`, `product_id`) REFERENCES `product_unit` (`id`, `product_id`);
ALTER TABLE `order_item`
    ADD CONSTRAINT `chk_order_item_base_quantity` CHECK (`base_quantity` IS NULL OR `base_quantity` > 0);
ALTER TABLE `order_item`
    ADD CONSTRAINT `chk_order_item_unit_cost` CHECK (`unit_cost` IS NULL OR `unit_cost` > 0);

CREATE TABLE IF NOT EXISTS `order_item_batch_allocation` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `order_item_id` BIGINT NOT NULL,
    `product_id` BIGINT NOT NULL,
    `store_id` BIGINT NOT NULL,
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
    CONSTRAINT `fk_order_item_batch_allocation_item_product` FOREIGN KEY (`order_item_id`, `product_id`) REFERENCES `order_item` (`id`, `product_id`),
    CONSTRAINT `fk_order_item_batch_allocation_batch` FOREIGN KEY (`batch_id`, `product_id`, `store_id`) REFERENCES `inventory_receipt_batch` (`id`, `product_id`, `store_id`),
    CONSTRAINT `fk_order_item_batch_allocation_location` FOREIGN KEY (`location_id`, `store_id`) REFERENCES `inventory_location` (`id`, `store_id`),
    CONSTRAINT `fk_order_item_batch_allocation_movement` FOREIGN KEY (`source_movement_id`, `product_id`, `store_id`) REFERENCES `inventory_movement` (`id`, `product_id`, `store_id`),
    CONSTRAINT `chk_order_item_batch_allocation_quantity` CHECK (`allocated_base_quantity` > 0),
    CONSTRAINT `chk_order_item_batch_allocation_cost` CHECK ((`batch_unit_cost` IS NULL OR `batch_unit_cost` > 0) AND (`allocated_cost` IS NULL OR `allocated_cost` > 0)),
    CONSTRAINT `chk_order_item_batch_allocation_evidence` CHECK (`evidence_level` IN ('SCANNED', 'FULFILLMENT_CONFIRMED', 'RULE_ALLOCATED', 'UNKNOWN'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单行批次归属';

CREATE TABLE IF NOT EXISTS `order_item_price_audit` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `order_item_id` BIGINT NOT NULL,
    `product_id` BIGINT NOT NULL,
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
    CONSTRAINT `fk_order_item_price_audit_item` FOREIGN KEY (`order_item_id`, `product_id`) REFERENCES `order_item` (`id`, `product_id`),
    CONSTRAINT `fk_order_item_price_audit_product` FOREIGN KEY (`product_id`) REFERENCES `product` (`id`),
    CONSTRAINT `fk_order_item_price_audit_unit` FOREIGN KEY (`sale_unit_id`, `product_id`) REFERENCES `product_unit` (`id`, `product_id`),
    CONSTRAINT `fk_order_item_price_audit_operator` FOREIGN KEY (`operator_id`) REFERENCES `staff` (`id`),
    CONSTRAINT `fk_order_item_price_audit_confirmer` FOREIGN KEY (`confirmer_id`) REFERENCES `staff` (`id`),
    CONSTRAINT `chk_order_item_price_audit_prices` CHECK (`suggested_unit_price` >= 0 AND `final_unit_price` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单行价格审计（追加写）';

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='库存风险决策留痕（追加写）';

/*!80016 CREATE TRIGGER `trg_order_item_price_audit_no_update`
    BEFORE UPDATE ON `order_item_price_audit` FOR EACH ROW
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'order_item_price_audit is append-only' */;
/*!80016 CREATE TRIGGER `trg_order_item_price_audit_no_delete`
    BEFORE DELETE ON `order_item_price_audit` FOR EACH ROW
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'order_item_price_audit is append-only' */;
/*!80016 CREATE TRIGGER `trg_inventory_decision_log_no_update`
    BEFORE UPDATE ON `inventory_decision_log` FOR EACH ROW
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'inventory_decision_log is append-only' */;
/*!80016 CREATE TRIGGER `trg_inventory_decision_log_no_delete`
    BEFORE DELETE ON `inventory_decision_log` FOR EACH ROW
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'inventory_decision_log is append-only' */;
