-- V108: 回填商品平均成本价格 + 修复赊销金额为0的历史记录
-- Bug: createPOSOrder 赊销订单 payAmount 被清零后传入 addDebt，导致 credit_account.credit_amount = 0

-- 1. 从 inventory_batch 回填 avg_cost_price（加权平均）
UPDATE `product` p
SET p.avg_cost_price = (
    SELECT ROUND(SUM(ib.cost_price * ib.quantity) / NULLIF(SUM(ib.quantity), 0), 2)
    FROM `inventory_batch` ib
    WHERE ib.product_id = p.id AND ib.is_active = 1 AND ib.cost_price IS NOT NULL AND ib.cost_price > 0
)
WHERE p.avg_cost_price IS NULL OR p.avg_cost_price = 0;

-- 2. 修复赊销账户金额为0的记录：从订单总额反算
UPDATE `credit_account` ca
INNER JOIN `order` o ON ca.order_id = o.id
SET ca.credit_amount = COALESCE(o.total_amount, 0) - COALESCE(o.discount_amount, 0) - COALESCE(o.coupon_amount, 0),
    ca.remaining_amount = COALESCE(o.total_amount, 0) - COALESCE(o.discount_amount, 0) - COALESCE(o.coupon_amount, 0) - COALESCE(ca.paid_amount, 0),
    ca.updated_at = NOW()
WHERE ca.credit_amount = 0
  AND ca.status = 1
  AND o.total_amount IS NOT NULL
  AND o.total_amount > 0;

-- 3. 同步修复赊销统计相关字段（确保 customer 表 total_credit 等字段一致）
-- 如果 customer 表有 credit_amount / total_debt 等聚合字段，在此一并更新
