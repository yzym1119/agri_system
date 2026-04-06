-- ============================================
-- V12: 门店表增强（添加自提支持和逻辑删除）
-- ============================================

ALTER TABLE `store`
ADD COLUMN `support_pickup` TINYINT DEFAULT 1 COMMENT '是否支持自提：1-是 0-否' AFTER `status`,
ADD COLUMN `deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除：1-已删 0-未删' AFTER `updated_by`;

-- 为自提状态添加索引，方便小程序筛选
CREATE INDEX `idx_store_pickup` ON `store` (`support_pickup`);










