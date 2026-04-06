-- ============================================
-- V67: VIP月度权益发放记录表添加类型字段
-- ============================================

-- 添加 record_type 字段和 content 字段
ALTER TABLE `vip_monthly_record`
    ADD COLUMN `record_type` TINYINT NOT NULL DEFAULT 1 COMMENT '记录类型：1-系统自动发放（月度权益），2-充值奖励发放' AFTER `vip_level`,
    ADD COLUMN `content` VARCHAR(500) COMMENT '发放内容描述' AFTER `grant_time`;









