-- ============================================
-- V68: 创建客户权益流水表
-- ============================================

CREATE TABLE `customer_benefit_log` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `customer_id` BIGINT NOT NULL COMMENT '客户ID',
    `source_type` TINYINT NOT NULL COMMENT '来源类型：1-每月权益(自动) 2-充值赠送 3-手动发放',
    `benefit_content` VARCHAR(500) NOT NULL COMMENT '内容快照(如: 现金50元, 化肥券x1)',
    `associated_id` BIGINT COMMENT '关联ID(如充值规则ID, 模板ID)',
    `created_by` BIGINT COMMENT '操作人(0为系统)',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    INDEX `idx_customer_time` (`customer_id`, `created_at`),
    INDEX `idx_source_type` (`source_type`),
    CONSTRAINT `fk_benefit_log_customer` FOREIGN KEY (`customer_id`) REFERENCES `customer` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='客户权益获取流水表';









