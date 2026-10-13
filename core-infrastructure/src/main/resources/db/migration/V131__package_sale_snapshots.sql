-- 套餐独立定价：仍使用商品订单行出库、售后和提成，不新增销售表。
-- 历史行保持空快照，不能从当前套餐配置猜测其原组成或套数。
ALTER TABLE order_item ADD COLUMN package_group_no INT NULL COMMENT '同一订单内套餐销售组号';
ALTER TABLE order_item ADD COLUMN package_name VARCHAR(200) NULL COMMENT '销售时套餐名称快照';
ALTER TABLE order_item ADD COLUMN package_quantity DECIMAL(10,4) NULL COMMENT '该销售组实际套数快照';
ALTER TABLE order_item ADD COLUMN package_unit_price DECIMAL(10,2) NULL COMMENT '每套成交价快照';
-- 分摊金额total_price始终按分保存。提高参考单价精度，避免7个物料分10元时损失尾分。
ALTER TABLE order_item MODIFY COLUMN unit_price DECIMAL(24,12) NOT NULL COMMENT '成交或分摊单价，金额以total_price为准';
ALTER TABLE order_item_price_audit MODIFY COLUMN suggested_unit_price DECIMAL(24,12) NOT NULL;
ALTER TABLE order_item_price_audit MODIFY COLUMN final_unit_price DECIMAL(24,12) NOT NULL;
ALTER TABLE order_item_price_audit MODIFY COLUMN price_difference DECIMAL(24,12) NOT NULL;
ALTER TABLE order_item ADD CONSTRAINT chk_package_sale_snapshot CHECK (
    package_group_no IS NULL OR
    (package_group_no > 0 AND package_id IS NOT NULL AND package_name IS NOT NULL
     AND package_quantity IS NOT NULL AND package_unit_price IS NOT NULL
     AND package_quantity > 0 AND package_quantity = FLOOR(package_quantity) AND package_unit_price > 0)
);
