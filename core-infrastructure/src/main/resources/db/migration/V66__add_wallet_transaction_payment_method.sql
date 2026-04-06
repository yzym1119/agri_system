-- ============================================
-- V66: 钱包交易记录表添加支付方式字段
-- ============================================

-- 添加 payment_method 字段
ALTER TABLE `wallet_transaction`
    ADD COLUMN `payment_method` TINYINT COMMENT '支付方式：1-现金，2-微信，3-支付宝（仅充值交易有效）' AFTER `remark`;









