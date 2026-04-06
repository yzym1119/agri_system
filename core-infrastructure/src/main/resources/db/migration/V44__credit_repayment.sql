-- ============================================
-- V44: Credit Repayment Log
-- ============================================

CREATE TABLE IF NOT EXISTS `credit_repayment_log` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `repayment_no` VARCHAR(50) NOT NULL COMMENT '还款流水号',
    `customer_id` BIGINT NOT NULL,
    `store_id` BIGINT NOT NULL COMMENT '收款门店',
    `amount` DECIMAL(10,2) NOT NULL COMMENT '还款金额',
    `payment_method` TINYINT NOT NULL COMMENT '还款方式：1-现金 2-微信 3-支付宝 4-银行转账',
    `transaction_id` VARCHAR(100) COMMENT '外部单号',
    `remark` VARCHAR(500),
    `created_by` BIGINT COMMENT '操作员',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_customer_time` (`customer_id`, `created_at`)
) COMMENT='赊销还款流水表';






