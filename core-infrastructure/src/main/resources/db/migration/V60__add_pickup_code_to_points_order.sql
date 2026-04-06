-- ============================================
-- V60: 为积分兑换订单表添加自提核销码字段
-- ============================================

-- 为积分兑换订单表添加 pickup_code 字段
ALTER TABLE `points_exchange_order`
    ADD COLUMN `pickup_code` VARCHAR(10) COMMENT '自提核销码（仅自提订单生成）' AFTER `remark`;

-- 添加唯一索引
ALTER TABLE `points_exchange_order`
    ADD UNIQUE KEY `uk_pickup_code` (`pickup_code`);









