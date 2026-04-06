-- ============================================
-- V62: 为客户表添加 version 字段（乐观锁）
-- ============================================

-- 添加 version 字段
ALTER TABLE `customer`
    ADD COLUMN `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号' AFTER `bind_staff_id`;









