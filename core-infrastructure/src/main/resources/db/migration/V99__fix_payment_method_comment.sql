-- Fix comment for order.payment_method to reflect actual codes used by application (6 = 赊销)
-- Note: Before applying to production, confirm column type and execute in maintenance window.
ALTER TABLE `order` 
  MODIFY COLUMN `payment_method` TINYINT(3) NULL COMMENT '主支付方式：1-现金,2-微信,3-支付宝,4-本金余额,5-红包/优惠券,6-赊销,10-混合支付';











