-- 统一订单主支付方式与支付流水编码
-- 说明：
-- 1. order.payment_method 使用主支付方式编码：1-现金,2-微信,3-支付宝,4-余额,5-优惠券,6-赊销,10-混合支付
-- 2. order_payment.payment_type 使用支付流水编码：1-现金,2-微信,3-支付宝,4-本金余额,5-红包/优惠券,6-赊销,8-专用金余额,10-混合支付
-- 3. 若数据库中仍有旧测试数据，则将 payment_type=4 统一迁移为专用金余额(8)，
--    将未携带外部流水号的 payment_type=3 统一视为本金余额(4)

UPDATE `order_payment`
SET `payment_type` = 8
WHERE `payment_type` = 4;

UPDATE `order_payment`
SET `payment_type` = 4
WHERE `payment_type` = 3
  AND (`outer_trans_no` IS NULL OR TRIM(`outer_trans_no`) = '');

ALTER TABLE `order`
    MODIFY COLUMN `payment_method` TINYINT(3) NULL COMMENT '主支付方式：1-现金,2-微信,3-支付宝,4-余额,5-优惠券,6-赊销,10-混合支付';

ALTER TABLE `order_payment`
    MODIFY COLUMN `payment_type` TINYINT NOT NULL COMMENT '支付类型：1-现金,2-微信,3-支付宝,4-本金余额,5-红包/优惠券,6-赊销,8-专用金余额,10-混合支付';
