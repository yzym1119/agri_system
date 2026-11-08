-- 支付上线前资金一致性加固：
-- 1) 支付意图发起幂等：request_key 唯一（MySQL 唯一索引允许多个 NULL，历史行不受影响）。
--    同一 requestKey 的重复/重试请求在数据库层保证只落一个意图，防止网络重试作废已创建意图。
-- 2) 外部收款流水全局唯一：同一支付平台(payment_type)+商户号+外部流水号只能归属一笔有效业务收款。
--    merchant_no 为平台商户号占位列，真实微信/支付宝接入时由回调核验填充（历史行默认空串）。
ALTER TABLE order_payment_intent
    ADD COLUMN request_key VARCHAR(64) NULL COMMENT '支付发起幂等键：同键重试重放同一意图' AFTER intent_no,
    ADD UNIQUE KEY uk_intent_request_key (request_key);

ALTER TABLE order_payment
    ADD COLUMN merchant_no VARCHAR(64) NOT NULL DEFAULT '' COMMENT '支付平台商户号（接入真实平台时核验填充）' AFTER coupon_id,
    ADD UNIQUE KEY uk_order_payment_outer (payment_type, merchant_no, outer_trans_no);
