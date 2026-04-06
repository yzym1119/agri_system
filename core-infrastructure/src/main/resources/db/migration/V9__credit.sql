-- ============================================
-- V9: 赊销相关表
-- ============================================

-- 赊销台账表
CREATE TABLE IF NOT EXISTS `credit_account` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `customer_id` BIGINT NOT NULL COMMENT '客户ID',
    `order_id` BIGINT NOT NULL COMMENT '订单ID',
    `credit_amount` DECIMAL(10,2) NOT NULL COMMENT '赊销金额（元）',
    `paid_amount` DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '已还金额（元）',
    `remaining_amount` DECIMAL(10,2) NOT NULL COMMENT '剩余金额（元）',
    `due_date` DATE COMMENT '到期日期',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1-未还清，2-已还清，3-已逾期',
    `remark` VARCHAR(500) COMMENT '备注',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_customer_id` (`customer_id`),
    KEY `idx_order_id` (`order_id`),
    KEY `idx_status` (`status`),
    KEY `idx_due_date` (`due_date`),
    CONSTRAINT `fk_credit_customer` FOREIGN KEY (`customer_id`) REFERENCES `customer` (`id`),
    CONSTRAINT `fk_credit_order` FOREIGN KEY (`order_id`) REFERENCES `order` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='赊销台账表';

-- 对账历史表
CREATE TABLE IF NOT EXISTS `credit_statement` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `customer_id` BIGINT NOT NULL COMMENT '客户ID',
    `statement_no` VARCHAR(50) NOT NULL COMMENT '对账单号',
    `statement_date` DATE NOT NULL COMMENT '对账日期',
    `start_date` DATE NOT NULL COMMENT '对账开始日期',
    `end_date` DATE NOT NULL COMMENT '对账结束日期',
    `total_credit_amount` DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '赊销总额（元）',
    `total_paid_amount` DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '已还总额（元）',
    `total_remaining_amount` DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '剩余总额（元）',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1-待确认，2-已确认，3-已争议',
    `confirmed_at` DATETIME COMMENT '确认时间',
    `confirmed_by` BIGINT COMMENT '确认人ID（客户或员工）',
    `remark` VARCHAR(500) COMMENT '备注',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `created_by` BIGINT COMMENT '创建人ID',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_statement_no` (`statement_no`),
    KEY `idx_customer_id` (`customer_id`),
    KEY `idx_statement_date` (`statement_date`),
    KEY `idx_status` (`status`),
    CONSTRAINT `fk_statement_customer` FOREIGN KEY (`customer_id`) REFERENCES `customer` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='对账历史表';











