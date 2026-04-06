-- 修复POS订单状态：将线下POS订单的"已支付"状态(2)更新为"已完成"状态(6)
-- 原因：POS订单是线下即时交易，支付成功后应该直接是"已完成"状态，而不是"已支付"状态

UPDATE `order`
SET `order_status` = 6
WHERE `order_type` = 1  -- POS订单
  AND `order_status` = 2;  -- 已支付状态









