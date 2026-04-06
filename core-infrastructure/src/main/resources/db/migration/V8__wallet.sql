-- ============================================
-- V8: 客户钱包相关表
-- ============================================

-- 客户钱包表
CREATE TABLE IF NOT EXISTS `customer_wallet` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `customer_id` BIGINT NOT NULL COMMENT '客户ID',
    `wallet_type` TINYINT NOT NULL COMMENT '钱包类型：1-本金，2-专用金，3-积分',
    `balance` DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '余额',
    `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_customer_wallet_type` (`customer_id`, `wallet_type`),
    KEY `idx_customer_id` (`customer_id`),
    CONSTRAINT `fk_wallet_customer` FOREIGN KEY (`customer_id`) REFERENCES `customer` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='客户钱包表';

-- 钱包交易记录表
CREATE TABLE IF NOT EXISTS `wallet_transaction` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `customer_id` BIGINT NOT NULL COMMENT '客户ID',
    `wallet_type` TINYINT NOT NULL COMMENT '钱包类型：1-本金，2-专用金，3-积分',
    `transaction_type` TINYINT NOT NULL COMMENT '交易类型：1-充值，2-消费，3-退款，4-赠送，5-扣除',
    `amount` DECIMAL(10,2) NOT NULL COMMENT '金额（正数为增加，负数为减少）',
    `balance_before` DECIMAL(10,2) NOT NULL COMMENT '交易前余额',
    `balance_after` DECIMAL(10,2) NOT NULL COMMENT '交易后余额',
    `reference_type` VARCHAR(50) COMMENT '关联类型（如：ORDER-订单，RECHARGE-充值）',
    `reference_id` BIGINT COMMENT '关联ID',
    `remark` VARCHAR(500) COMMENT '备注',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `created_by` BIGINT COMMENT '操作人ID',
    PRIMARY KEY (`id`),
    KEY `idx_customer_id` (`customer_id`),
    KEY `idx_wallet_type` (`wallet_type`),
    KEY `idx_transaction_type` (`transaction_type`),
    KEY `idx_reference` (`reference_type`, `reference_id`),
    KEY `idx_created_at` (`created_at`),
    CONSTRAINT `fk_wallet_transaction_customer` FOREIGN KEY (`customer_id`) REFERENCES `customer` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='钱包交易记录表';











