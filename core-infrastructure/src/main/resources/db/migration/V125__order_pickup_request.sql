-- V125: 库房取货请求与销售批次归属关联（零售事实链 Task5）
-- 支持两步式履约：货架余额不足或操作员选择库房取货时先创建取货请求，
-- 拣货完成（扫码/显式确认）后再写入实际批次移动与成本证据。
-- order_pickup_request.order_id 仅逻辑关联订单（不同基线可能缺少 order 表，故不建外键）。
-- order_item_batch_allocation.pickup_request_id 允许为 NULL：货架单批次直接命中无需取货请求。
-- 项目部署基线为 MySQL 8.2（CHECK 约束在 MySQL >= 8.0.16 强制执行）。

CREATE TABLE IF NOT EXISTS `order_pickup_request` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `order_id` BIGINT NOT NULL,
    `order_item_id` BIGINT NOT NULL,
    `store_id` BIGINT NOT NULL,
    `product_id` BIGINT NOT NULL,
    `requested_base_quantity` DECIMAL(20,8) NOT NULL,
    `source_location_id` BIGINT,
    `status` VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `created_by` BIGINT,
    `fulfilled_at` DATETIME,
    `fulfilled_by` BIGINT,
    PRIMARY KEY (`id`),
    KEY `idx_order_pickup_request_order` (`order_id`),
    KEY `idx_order_pickup_request_item` (`order_item_id`),
    KEY `idx_order_pickup_request_status` (`store_id`, `status`),
    CONSTRAINT `fk_order_pickup_request_item` FOREIGN KEY (`order_item_id`) REFERENCES `order_item` (`id`),
    CONSTRAINT `fk_order_pickup_request_item_product` FOREIGN KEY (`order_item_id`, `product_id`) REFERENCES `order_item` (`id`, `product_id`),
    CONSTRAINT `fk_order_pickup_request_product` FOREIGN KEY (`product_id`) REFERENCES `product` (`id`),
    CONSTRAINT `fk_order_pickup_request_location` FOREIGN KEY (`source_location_id`, `store_id`) REFERENCES `inventory_location` (`id`, `store_id`),
    CONSTRAINT `fk_order_pickup_request_creator` FOREIGN KEY (`created_by`) REFERENCES `staff` (`id`),
    CONSTRAINT `fk_order_pickup_request_fulfiller` FOREIGN KEY (`fulfilled_by`) REFERENCES `staff` (`id`),
    CONSTRAINT `chk_order_pickup_request_quantity` CHECK (`requested_base_quantity` > 0),
    CONSTRAINT `chk_order_pickup_request_status` CHECK (`status` IN ('PENDING', 'FULFILLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='库房取货请求';

ALTER TABLE `order_item_batch_allocation` ADD COLUMN `pickup_request_id` BIGINT NULL AFTER `location_id`;
ALTER TABLE `order_item_batch_allocation` ADD KEY `idx_order_item_batch_allocation_pickup` (`pickup_request_id`);
ALTER TABLE `order_item_batch_allocation`
    ADD CONSTRAINT `fk_order_item_batch_allocation_pickup` FOREIGN KEY (`pickup_request_id`) REFERENCES `order_pickup_request` (`id`);
