-- 1) 库存账本热路径索引：盘点 FOR UPDATE、FEFO 挑批、批次汇总均按 (store_id, product_id)
--    过滤 inventory_batch_balance（InventoryLedgerService 多处 WHERE store_id=? AND product_id=?）；
--    现有索引前导列为 batch_id/location_id，门店+商品维度查询退化为全表扫描。
--    仅对本表补索引；inventory_movement / order_item_batch_allocation 未找到同维度直接查询证据，不猜测加索引。
CREATE INDEX idx_ibb_store_product ON inventory_batch_balance (store_id, product_id);

-- 2) 字符集统一：wallet_auth_code（V113 建表）为 utf8mb4_0900_ai_ci，与其余表不一致，统一到库默认。
ALTER TABLE wallet_auth_code CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
