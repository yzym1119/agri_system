-- 钱包授权码表 (POS钱包支付验证用)
CREATE TABLE IF NOT EXISTS `wallet_auth_code` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `customer_id` BIGINT NOT NULL COMMENT '客户ID',
    `order_id` BIGINT DEFAULT NULL COMMENT '关联订单ID',
    `auth_code` VARCHAR(10) NOT NULL COMMENT '6位授权码',
    `amount` DECIMAL(12,2) NOT NULL COMMENT '授权金额',
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '0=待使用,1=已使用,2=已过期',
    `expire_at` DATETIME NOT NULL COMMENT '过期时间',
    `used_at` DATETIME DEFAULT NULL COMMENT '使用时间',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    INDEX `idx_auth_code` (`auth_code`),
    INDEX `idx_customer_id` (`customer_id`),
    INDEX `idx_status_expire` (`status`, `expire_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='钱包授权码';
