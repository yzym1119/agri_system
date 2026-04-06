-- ============================================
-- V1: 门店表
-- ============================================

CREATE TABLE IF NOT EXISTS `store` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `code` VARCHAR(50) NOT NULL COMMENT '门店编码',
    `name` VARCHAR(100) NOT NULL COMMENT '门店名称',
    `address` VARCHAR(500) COMMENT '门店地址',
    `phone` VARCHAR(20) COMMENT '联系电话',
    `manager_id` BIGINT COMMENT '店长ID（关联staff表）',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1-正常，2-停用',
    `remark` VARCHAR(500) COMMENT '备注',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `created_by` BIGINT COMMENT '创建人ID',
    `updated_by` BIGINT COMMENT '更新人ID',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_store_code` (`code`),
    KEY `idx_store_status` (`status`),
    KEY `idx_store_manager` (`manager_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='门店表';




















