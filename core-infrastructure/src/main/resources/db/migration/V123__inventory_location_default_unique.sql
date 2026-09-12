-- V123: 默认货位唯一约束,防并发首收货静默建重复默认后仓(Important 4)。
-- code 为空的默认位置按 (store_id, location_type) 唯一;显式 code 的货位不受此约束。
-- 并发首收货同时创建默认后仓时,第二个事务撞唯一约束安全回滚,而非静默产生重复后仓损坏数据。
ALTER TABLE `inventory_location`
  ADD COLUMN `default_location_key` VARCHAR(30) GENERATED ALWAYS AS
    (CASE WHEN `code` IS NULL THEN `location_type` ELSE NULL END);
ALTER TABLE `inventory_location`
  ADD UNIQUE KEY `uk_inventory_location_default` (`store_id`, `default_location_key`);
