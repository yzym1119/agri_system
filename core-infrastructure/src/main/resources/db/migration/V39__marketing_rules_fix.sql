-- ============================================
-- V39: 营销规则与周期性权益
-- ============================================

-- 1. 充值营销规则表（一次性奖励）
CREATE TABLE IF NOT EXISTS `marketing_recharge_rule` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `name` VARCHAR(100) NOT NULL COMMENT '规则名称',
    `min_recharge_amount` DECIMAL(10,2) NOT NULL COMMENT '门槛金额（元）',
    `target_vip_level` TINYINT DEFAULT NULL COMMENT '升级到的VIP等级',
    `gift_duration_days` INT DEFAULT 365 COMMENT '赠送VIP时长(天)，默认1年',
    `gift_special_fund` DECIMAL(10,2) DEFAULT 0.00 COMMENT '赠送专用金（元）',
    `status` TINYINT DEFAULT 1 COMMENT '状态：1-启用，0-停用',
    `sort_order` INT DEFAULT 0 COMMENT '优先级（数字越小优先级越高）',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_status_sort` (`status`, `sort_order`),
    KEY `idx_min_amount` (`min_recharge_amount`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='充值一次性奖励规则表';

-- 2. VIP 周期权益表（每月赠送）
CREATE TABLE IF NOT EXISTS `marketing_vip_rights` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `vip_level` TINYINT NOT NULL COMMENT '关联VIP等级',
    `benefit_type` TINYINT NOT NULL COMMENT '权益类型：1-每月送券，2-每月送专用金',
    `benefit_value` DECIMAL(10,2) DEFAULT 0.00 COMMENT '数值（如金额，元）',
    `template_coupon_id` BIGINT DEFAULT NULL COMMENT '赠送的优惠券模板ID（关联customer_coupon逻辑）',
    `coupon_valid_days` INT DEFAULT 30 COMMENT '赠送券/金的有效期(天)',
    `status` TINYINT DEFAULT 1 COMMENT '状态：1-启用，0-停用',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_vip_level` (`vip_level`),
    KEY `idx_status_type` (`status`, `benefit_type`),
    KEY `idx_template_coupon` (`template_coupon_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='VIP每月周期权益表';








