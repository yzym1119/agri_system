-- ============================================
-- 为积分兑换订单表添加自提核销码字段
-- ============================================
-- 如果 V60 迁移没有自动执行，可以手动执行此脚本

USE agri_system;

-- 检查字段是否已存在
SELECT COLUMN_NAME 
FROM INFORMATION_SCHEMA.COLUMNS 
WHERE TABLE_SCHEMA = 'agri_system' 
  AND TABLE_NAME = 'points_exchange_order' 
  AND COLUMN_NAME = 'pickup_code';

-- 如果上面的查询返回空，则执行以下语句添加字段
ALTER TABLE `points_exchange_order`
    ADD COLUMN `pickup_code` VARCHAR(10) COMMENT '自提核销码（仅自提订单生成）' AFTER `remark`;

-- 添加唯一索引（如果不存在）
ALTER TABLE `points_exchange_order`
    ADD UNIQUE KEY `uk_pickup_code` (`pickup_code`);

-- 验证结果
DESCRIBE `points_exchange_order`;









