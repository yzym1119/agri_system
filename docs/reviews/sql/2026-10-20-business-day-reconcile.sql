-- 仅用于本轮3092隔离库 agri_day_20261020_24e59d07aa6c；全部为只读查询。
-- 商品、订单和员工ID来自本轮实际页面操作。业务交易为10月20日，盘点为10月21日。
SELECT check_name, actual, expected, CASE WHEN actual=expected THEN 'PASS' ELSE 'FAIL' END AS result
FROM (
SELECT '成功迁移数' AS check_name, CAST((SELECT COUNT(*) FROM flyway_schema_history WHERE success=1) AS DECIMAL(28,8)) AS actual, CAST(88 AS DECIMAL(28,8)) AS expected
UNION ALL
SELECT '失败迁移数' AS check_name, CAST((SELECT COUNT(*) FROM flyway_schema_history WHERE success=0) AS DECIMAL(28,8)) AS actual, CAST(0 AS DECIMAL(28,8)) AS expected
UNION ALL
SELECT '实际销售单数' AS check_name, CAST((SELECT COUNT(*) FROM `order` WHERE deleted=0) AS DECIMAL(28,8)) AS actual, CAST(4 AS DECIMAL(28,8)) AS expected
UNION ALL
SELECT '原净应结算N' AS check_name, CAST((SELECT SUM(pay_amount) FROM `order`) AS DECIMAL(28,8)) AS actual, CAST(542.5 AS DECIMAL(28,8)) AS expected
UNION ALL
SELECT '原销售行金额' AS check_name, CAST((SELECT SUM(total_price) FROM order_item) AS DECIMAL(28,8)) AS actual, CAST(542.5 AS DECIMAL(28,8)) AS expected
UNION ALL
SELECT '已完成退货R' AS check_name, CAST((SELECT SUM(refund_amount) FROM after_sales WHERE status=50) AS DECIMAL(28,8)) AS actual, CAST(67.5 AS DECIMAL(28,8)) AS expected
UNION ALL
SELECT '净销售额' AS check_name, CAST((SELECT SUM(pay_amount) - (SELECT SUM(refund_amount) FROM after_sales WHERE status=50) FROM `order`) AS DECIMAL(28,8)) AS actual, CAST(475 AS DECIMAL(28,8)) AS expected
UNION ALL
SELECT '实退资金F' AS check_name, CAST((SELECT SUM(cash_refund_amount) FROM after_sales WHERE status=50) AS DECIMAL(28,8)) AS actual, CAST(17.5 AS DECIMAL(28,8)) AS expected
UNION ALL
SELECT '退货冲欠W' AS check_name, CAST((SELECT SUM(credit_reduction_amount) FROM after_sales WHERE status=50) AS DECIMAL(28,8)) AS actual, CAST(50 AS DECIMAL(28,8)) AS expected
UNION ALL
SELECT '原挂账B' AS check_name, CAST((SELECT SUM(credit_amount) FROM credit_account) AS DECIMAL(28,8)) AS actual, CAST(290 AS DECIMAL(28,8)) AS expected
UNION ALL
SELECT '累计实还P' AS check_name, CAST((SELECT SUM(paid_amount) FROM credit_account) AS DECIMAL(28,8)) AS actual, CAST(240 AS DECIMAL(28,8)) AS expected
UNION ALL
SELECT '剩余欠款D' AS check_name, CAST((SELECT SUM(remaining_amount) FROM credit_account) AS DECIMAL(28,8)) AS actual, CAST(0 AS DECIMAL(28,8)) AS expected
UNION ALL
SELECT '还款日志与账户实还一致' AS check_name, CAST((SELECT SUM(amount) - (SELECT SUM(paid_amount) FROM credit_account) FROM credit_repayment_log) AS DECIMAL(28,8)) AS actual, CAST(0 AS DECIMAL(28,8)) AS expected
UNION ALL
SELECT '开单现金原收' AS check_name, CAST((SELECT SUM(amount) FROM order_payment WHERE payment_type=1) AS DECIMAL(28,8)) AS actual, CAST(252.5 AS DECIMAL(28,8)) AS expected
UNION ALL
SELECT '现金净留' AS check_name, CAST((SELECT SUM(amount-refunded_amount) + (SELECT SUM(amount-refunded_amount) FROM credit_repayment_log WHERE payment_method=1) FROM order_payment WHERE payment_type=1) AS DECIMAL(28,8)) AS actual, CAST(375 AS DECIMAL(28,8)) AS expected
UNION ALL
SELECT '银行实还' AS check_name, CAST((SELECT SUM(amount-refunded_amount) FROM credit_repayment_log WHERE payment_method=4) AS DECIMAL(28,8)) AS actual, CAST(100 AS DECIMAL(28,8)) AS expected
UNION ALL
SELECT '还款来源记录数' AS check_name, CAST((SELECT COUNT(*) FROM credit_repayment_log) AS DECIMAL(28,8)) AS actual, CAST(2 AS DECIMAL(28,8)) AS expected
UNION ALL
SELECT '未知退款来源数' AS check_name, CAST((SELECT COUNT(*) FROM credit_repayment_log WHERE refunded_amount IS NULL) AS DECIMAL(28,8)) AS actual, CAST(0 AS DECIMAL(28,8)) AS expected
UNION ALL
SELECT '赊销恒等式B=P+W+D' AS check_name, CAST((SELECT SUM(credit_amount-paid_amount-remaining_amount) - (SELECT SUM(credit_reduction_amount) FROM after_sales WHERE status=50) FROM credit_account) AS DECIMAL(28,8)) AS actual, CAST(0 AS DECIMAL(28,8)) AS expected
UNION ALL
SELECT '本月赊销净回款率' AS check_name, CAST((SELECT 100*SUM(paid_amount)/(SUM(paid_amount)+SUM(remaining_amount)) FROM credit_account) AS DECIMAL(28,8)) AS actual, CAST(100 AS DECIMAL(28,8)) AS expected
UNION ALL
SELECT '收货总成本' AS check_name, CAST((SELECT SUM(base_quantity*base_unit_cost) FROM inventory_receipt_batch) AS DECIMAL(28,8)) AS actual, CAST(992 AS DECIMAL(28,8)) AS expected
UNION ALL
SELECT '收货批次数' AS check_name, CAST((SELECT COUNT(*) FROM inventory_receipt_batch) AS DECIMAL(28,8)) AS actual, CAST(3 AS DECIMAL(28,8)) AS expected
UNION ALL
SELECT '两箱直接换算瓶数' AS check_name, CAST((SELECT base_quantity FROM inventory_receipt_batch WHERE product_id=2) AS DECIMAL(28,8)) AS actual, CAST(24 AS DECIMAL(28,8)) AS expected
UNION ALL
SELECT '药剂每瓶实际成本' AS check_name, CAST((SELECT base_unit_cost FROM inventory_receipt_batch WHERE product_id=2) AS DECIMAL(28,8)) AS actual, CAST(8 AS DECIMAL(28,8)) AS expected
UNION ALL
SELECT '库管收货事实' AS check_name, CAST((SELECT COUNT(*) FROM inventory_receipt_batch WHERE receiver_id=3 AND date_status='UNKNOWN' AND production_date IS NULL AND expiry_date IS NULL) AS DECIMAL(28,8)) AS actual, CAST(3 AS DECIMAL(28,8)) AS expected
UNION ALL
SELECT '商品1最终库存' AS check_name, CAST((SELECT SUM(base_quantity) FROM inventory_batch_balance WHERE product_id=1) AS DECIMAL(28,8)) AS actual, CAST(13 AS DECIMAL(28,8)) AS expected
UNION ALL
SELECT '商品2最终库存' AS check_name, CAST((SELECT SUM(base_quantity) FROM inventory_batch_balance WHERE product_id=2) AS DECIMAL(28,8)) AS actual, CAST(14 AS DECIMAL(28,8)) AS expected
UNION ALL
SELECT '商品3最终库存' AS check_name, CAST((SELECT SUM(base_quantity) FROM inventory_batch_balance WHERE product_id=3) AS DECIMAL(28,8)) AS actual, CAST(9 AS DECIMAL(28,8)) AS expected
UNION ALL
SELECT '最终库存成本' AS check_name, CAST((SELECT SUM(b.base_quantity*r.base_unit_cost) FROM inventory_batch_balance b JOIN inventory_receipt_batch r ON r.id=b.batch_id) AS DECIMAL(28,8)) AS actual, CAST(682 AS DECIMAL(28,8)) AS expected
UNION ALL
SELECT '销售净成本' AS check_name, CAST((SELECT SUM(CASE WHEN movement_type='SALE' THEN base_quantity*unit_cost WHEN movement_type='RETURN' THEN -base_quantity*unit_cost ELSE 0 END) FROM inventory_movement) AS DECIMAL(28,8)) AS actual, CAST(280 AS DECIMAL(28,8)) AS expected
UNION ALL
SELECT '盘亏成本' AS check_name, CAST((SELECT SUM(base_quantity*unit_cost) FROM inventory_movement WHERE movement_type='COUNT_ADJUSTMENT' AND from_location_id IS NOT NULL AND to_location_id IS NULL) AS DECIMAL(28,8)) AS actual, CAST(30 AS DECIMAL(28,8)) AS expected
UNION ALL
SELECT '移动流水总数' AS check_name, CAST((SELECT COUNT(*) FROM inventory_movement) AS DECIMAL(28,8)) AS actual, CAST(13 AS DECIMAL(28,8)) AS expected
UNION ALL
SELECT '余额与逐批逐货位流水一致' AS check_name, CAST((SELECT COUNT(*) FROM inventory_batch_balance b WHERE b.base_quantity <> (SELECT COALESCE(SUM(CASE WHEN m.to_location_id=b.location_id THEN m.base_quantity ELSE 0 END - CASE WHEN m.from_location_id=b.location_id THEN m.base_quantity ELSE 0 END),0) FROM inventory_movement m WHERE m.batch_id=b.batch_id AND m.store_id=b.store_id AND m.product_id=b.product_id)) AS DECIMAL(28,8)) AS actual, CAST(0 AS DECIMAL(28,8)) AS expected
UNION ALL
SELECT '原出库分配数' AS check_name, CAST((SELECT COUNT(*) FROM order_item_batch_allocation) AS DECIMAL(28,8)) AS actual, CAST(7 AS DECIMAL(28,8)) AS expected
UNION ALL
SELECT '退款原分配合法回库数' AS check_name, CAST((SELECT COUNT(*) FROM inventory_movement m JOIN order_item_batch_allocation a ON a.id=m.reference_id WHERE m.movement_type='RETURN' AND m.reference_type='SALE_ALLOCATION' AND m.batch_id=a.batch_id AND m.product_id=a.product_id AND m.to_location_id=a.location_id) AS DECIMAL(28,8)) AS actual, CAST(2 AS DECIMAL(28,8)) AS expected
UNION ALL
SELECT '套餐成交总额' AS check_name, CAST((SELECT SUM(total_price) FROM order_item WHERE order_id=3 AND package_name='营业日组合AB') AS DECIMAL(28,8)) AS actual, CAST(70 AS DECIMAL(28,8)) AS expected
UNION ALL
SELECT '套餐成交快照行数' AS check_name, CAST((SELECT COUNT(*) FROM order_item WHERE order_id=3 AND package_quantity=1 AND package_unit_price=70) AS DECIMAL(28,8)) AS actual, CAST(2 AS DECIMAL(28,8)) AS expected
UNION ALL
SELECT '库管已交货数' AS check_name, CAST((SELECT COUNT(*) FROM order_pickup_request WHERE order_id=3 AND status='FULFILLED' AND fulfilled_by=3) AS DECIMAL(28,8)) AS actual, CAST(2 AS DECIMAL(28,8)) AS expected
UNION ALL
SELECT '剩余取货待办数' AS check_name, CAST((SELECT COUNT(*) FROM order_pickup_request WHERE status='PENDING') AS DECIMAL(28,8)) AS actual, CAST(0 AS DECIMAL(28,8)) AS expected
UNION ALL
SELECT '人工配送已签收' AS check_name, CAST((SELECT COUNT(*) FROM order_delivery WHERE order_id=2 AND delivery_status=4 AND delivery_staff_id=3 AND received_at IS NOT NULL) AS DECIMAL(28,8)) AS actual, CAST(1 AS DECIMAL(28,8)) AS expected
UNION ALL
SELECT '部分退货订单状态保留' AS check_name, CAST((SELECT COUNT(*) FROM `order` WHERE id IN (2,4) AND order_status=9) AS DECIMAL(28,8)) AS actual, CAST(2 AS DECIMAL(28,8)) AS expected
UNION ALL
SELECT '收银实际操作员工' AS check_name, CAST((SELECT COUNT(*) FROM `order` WHERE staff_id=2) AS DECIMAL(28,8)) AS actual, CAST(4 AS DECIMAL(28,8)) AS expected
UNION ALL
SELECT '收银唯一角色' AS check_name, CAST((SELECT COUNT(*) FROM sys_staff_role WHERE staff_id=2 AND role_id=2) AS DECIMAL(28,8)) AS actual, CAST(1 AS DECIMAL(28,8)) AS expected
UNION ALL
SELECT '库管唯一角色' AS check_name, CAST((SELECT COUNT(*) FROM sys_staff_role WHERE staff_id=3 AND role_id=3) AS DECIMAL(28,8)) AS actual, CAST(1 AS DECIMAL(28,8)) AS expected
UNION ALL
SELECT '虚构员工无管理员关联' AS check_name, CAST((SELECT COUNT(*) FROM sys_staff_role WHERE staff_id IN (2,3) AND role_id=1) AS DECIMAL(28,8)) AS actual, CAST(0 AS DECIMAL(28,8)) AS expected
) checks;
