-- ============================================
-- V3: 客户表
-- ============================================

CREATE TABLE IF NOT EXISTS `customer` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `code` VARCHAR(50) NOT NULL COMMENT '客户编码',
    `name` VARCHAR(100) NOT NULL COMMENT '客户姓名',
    `phone` VARCHAR(20) NOT NULL COMMENT '手机号',
    `type` TINYINT NOT NULL DEFAULT 1 COMMENT '客户类型：1-普通客户，2-VIP客户',
    `vip_level` TINYINT COMMENT 'VIP等级：1-普通VIP，2-银卡VIP，3-金卡VIP，4-钻石VIP（仅VIP客户有效）',
    `vip_start_date` DATE COMMENT 'VIP开始日期',
    `vip_end_date` DATE COMMENT 'VIP结束日期',
    `address` VARCHAR(500) COMMENT '地址',
    `id_card` VARCHAR(18) COMMENT '身份证号',
    `credit_limit` DECIMAL(10,2) DEFAULT 0.00 COMMENT '赊销额度（元）',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1-正常，2-停用',
    `remark` VARCHAR(500) COMMENT '备注',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `created_by` BIGINT COMMENT '创建人ID',
    `updated_by` BIGINT COMMENT '更新人ID',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_customer_code` (`code`),
    UNIQUE KEY `uk_customer_phone` (`phone`),
    KEY `idx_customer_type` (`type`),
    KEY `idx_customer_status` (`status`),
    KEY `idx_customer_vip_level` (`vip_level`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='客户表';











