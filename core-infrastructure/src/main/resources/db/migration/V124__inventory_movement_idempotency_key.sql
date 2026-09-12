-- V124: 库存移动幂等键,防重试重复追加事实导致批次余额被重复增减(Critical 1 收尾)。
-- 盘点调整/退货等单边事实必须携带幂等键;重复提交时第二条 INSERT 撞唯一约束安全回滚,而非静默重复扣增。
-- idempotency_key 允许为 NULL(补货/拣货等内部双边移动不强制),MySQL/H2 唯一索引均允许多行 NULL 共存。
ALTER TABLE `inventory_movement`
  ADD COLUMN `idempotency_key` VARCHAR(128) NULL AFTER `movement_no`;
ALTER TABLE `inventory_movement`
  ADD UNIQUE KEY `uk_inventory_movement_idempotency` (`idempotency_key`);
