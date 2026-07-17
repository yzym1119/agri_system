-- ============================================
-- V107: 补齐 order 表缺失的 receiver_name 列
-- Order 实体的 receiverName/receiverPhone/receiverAddress 与实际表结构不匹配
-- 表里已有 delivery_address 和 delivery_phone，但缺少 receiver_name
-- ============================================

ALTER TABLE `order` ADD COLUMN `receiver_name` VARCHAR(100) COMMENT '收货人姓名' AFTER `staff_id`;
