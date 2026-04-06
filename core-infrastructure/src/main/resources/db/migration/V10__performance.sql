-- ============================================
-- V10: 员工绩效相关表
-- ============================================

-- 员工绩效记录表
CREATE TABLE IF NOT EXISTS `staff_performance` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `staff_id` BIGINT NOT NULL COMMENT '员工ID',
    `performance_type` TINYINT NOT NULL COMMENT '绩效类型：1-推荐行为，2-服务行为',
    `order_id` BIGINT COMMENT '关联订单ID',
    `customer_id` BIGINT COMMENT '关联客户ID',
    `product_id` BIGINT COMMENT '关联商品ID',
    `performance_score` INT COMMENT '绩效分数',
    `commission_amount` DECIMAL(10,2) DEFAULT 0.00 COMMENT '提成金额（元）',
    `performance_date` DATE NOT NULL COMMENT '绩效日期',
    `remark` VARCHAR(500) COMMENT '备注',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `created_by` BIGINT COMMENT '创建人ID',
    PRIMARY KEY (`id`),
    KEY `idx_staff_id` (`staff_id`),
    KEY `idx_performance_type` (`performance_type`),
    KEY `idx_order_id` (`order_id`),
    KEY `idx_customer_id` (`customer_id`),
    KEY `idx_performance_date` (`performance_date`),
    CONSTRAINT `fk_performance_staff` FOREIGN KEY (`staff_id`) REFERENCES `staff` (`id`),
    CONSTRAINT `fk_performance_order` FOREIGN KEY (`order_id`) REFERENCES `order` (`id`),
    CONSTRAINT `fk_performance_customer` FOREIGN KEY (`customer_id`) REFERENCES `customer` (`id`),
    CONSTRAINT `fk_performance_product` FOREIGN KEY (`product_id`) REFERENCES `product` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='员工绩效记录表';











