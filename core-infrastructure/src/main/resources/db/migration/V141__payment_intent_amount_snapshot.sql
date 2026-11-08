-- 支付意图资金拆分快照：发起时把券/专用金/余额/外部四段金额一起落库。
--
-- 为什么必须新增字段，而不是用现有列推算：
-- 现有表只保存 use_balance / use_special_fund / coupon_id 三个开关与 external_amount。
-- 开关为真但实际抵扣为 0（余额不足、券不适用）时，布尔值无法还原当时的拆分；
-- 重放若重新读钱包与券，客户充值/消费后外部金额就会变，同一个 requestKey 无法稳定重放，
-- 与「同键重试必须拿到第一次结果」的幂等约定冲突。
--
-- 历史数据：存量待确认意图（部署窗口内最多 15 分钟）快照为 0，
-- 重放时金额合计与订单应付不一致会被拒绝，客户端换新 requestKey 重新发起即可，不做历史回填猜测。
ALTER TABLE order_payment_intent
    ADD COLUMN coupon_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '发起时优惠券抵扣金额快照' AFTER coupon_id,
    ADD COLUMN special_fund_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '发起时专用金抵扣金额快照' AFTER use_special_fund,
    ADD COLUMN balance_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '发起时本金余额抵扣金额快照' AFTER use_balance;
