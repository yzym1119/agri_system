-- V126: 按销售单位/渠道报价与人工改价留痕（零售事实链 Task7）
-- product_price 增加 sale_unit/channel/rule_version：显式按销售单位（箱/瓶）与渠道（POS/MINI 等）
-- 定义报价规则；sale_unit 为空表示沿用旧链路按主单位并换算的语义，rule_version 记录规则版本便于审计追溯。
-- order_item_price_audit 增加 quote_id：关联本次服务端报价（PriceQuote），人工改价时留痕“报价-成交”差异来源。
-- 不新增索引：现有 idx_product_id / idx_order_item_price_audit_item 足以支撑当前查询规模。
-- 项目部署基线为 MySQL 8.2（基线注释风格与 V120/V125 保持一致）。

ALTER TABLE `product_price` ADD COLUMN `sale_unit` VARCHAR(32) NULL;
ALTER TABLE `product_price` ADD COLUMN `channel` VARCHAR(32) NULL;
ALTER TABLE `product_price` ADD COLUMN `rule_version` VARCHAR(50) NULL;
ALTER TABLE `order_item_price_audit` ADD COLUMN `quote_id` VARCHAR(64) NULL;
