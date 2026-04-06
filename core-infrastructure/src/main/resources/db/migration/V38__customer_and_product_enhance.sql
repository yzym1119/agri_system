-- ============================================
-- V38: 客户成长体系与商品专用金开关补全
-- ============================================

-- 1. 客户表增强：成长值与业绩归属
ALTER TABLE `customer`
    ADD COLUMN `total_order_count` INT DEFAULT 0 COMMENT '累计成交单数' AFTER `credit_limit`,
    ADD COLUMN `total_order_amount` DECIMAL(12,2) DEFAULT 0.00 COMMENT '累计成交总额' AFTER `total_order_count`,
    ADD COLUMN `last_order_time` DATETIME COMMENT '最近下单时间' AFTER `total_order_amount`,
    ADD COLUMN `bind_staff_id` BIGINT COMMENT '归属员工ID' AFTER `last_order_time`;

-- 业绩归属索引
CREATE INDEX `idx_bind_staff` ON `customer` (`bind_staff_id`);


-- 2. 商品主表：专用金开关
ALTER TABLE `product`
    ADD COLUMN `allow_special_fund` TINYINT DEFAULT 0 COMMENT '允许使用专用金：1-是 0-否' AFTER `base_shipping_fee`;


-- 3. 套餐表：专用金开关
ALTER TABLE `product_package`
    ADD COLUMN `allow_special_fund` TINYINT DEFAULT 0 COMMENT '允许使用专用金：1-是 0-否' AFTER `min_price`;


-- 4. 订单表状态注释补全：新增 9-部分退款(PART_REFUNDED)
ALTER TABLE `order`
    MODIFY COLUMN `order_status` TINYINT NOT NULL DEFAULT 1 COMMENT '订单状态：1-待支付(WAIT_PAY), 2-已支付(PAID), 3-待自提(WAIT_PICKUP), 4-待发货(WAIT_SHIP), 5-已发货(SHIPPED), 6-已完成(FINISHED), 7-已取消(CANCELLED), 8-已退款(REFUNDED), 9-部分退款(PART_REFUNDED)';









