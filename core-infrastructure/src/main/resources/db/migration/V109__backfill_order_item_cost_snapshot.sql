-- V109: 回填历史订单明细的成本快照
-- Bug: 历史 order_item.cost_price_snapshot 为0（product.avg_cost_price 当时未回填），导致销售趋势毛利虚高

UPDATE `order_item` oi
INNER JOIN `product` p ON oi.product_id = p.id
SET oi.cost_price_snapshot = p.avg_cost_price
WHERE (oi.cost_price_snapshot IS NULL OR oi.cost_price_snapshot = 0)
  AND p.avg_cost_price IS NOT NULL
  AND p.avg_cost_price > 0;
