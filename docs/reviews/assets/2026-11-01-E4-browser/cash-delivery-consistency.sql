-- 只读：本轮独立虚构浏览器验收库专用，不能用于生产基线。
SELECT '唯一销售订单' AS check_name, ((SELECT COUNT(*) FROM `order`)=1) AS passed;
SELECT '订单金额250' AS check_name, ((SELECT COUNT(*) FROM `order` WHERE id=1 AND total_amount=250 AND pay_amount=250 AND payment_method=1)=1) AS passed;
SELECT '唯一原请求已完成' AS check_name, ((SELECT COUNT(*) FROM pos_sale_submission)=1 AND (SELECT COUNT(*) FROM pos_sale_submission WHERE order_id=1 AND status='COMPLETED')=1) AS passed;
SELECT '唯一现金收款250未退款' AS check_name, ((SELECT COUNT(*) FROM order_payment)=1 AND (SELECT COUNT(*) FROM order_payment WHERE order_id=1 AND payment_type=1 AND amount=250 AND refunded_amount=0)=1) AS passed;
SELECT '原收货20与销售5各一次' AS check_name, ((SELECT COUNT(*) FROM inventory_movement)=2 AND (SELECT COUNT(*) FROM inventory_movement WHERE movement_type='RECEIPT' AND base_quantity=20)=1 AND (SELECT COUNT(*) FROM inventory_movement WHERE movement_type='SALE' AND base_quantity=5 AND reference_id=1)=1) AS passed;
SELECT '余额15预留0' AS check_name, ((SELECT COUNT(*) FROM inventory_batch_balance)=1 AND (SELECT COUNT(*) FROM inventory_batch_balance WHERE base_quantity=15 AND reserved_base_quantity=0)=1) AS passed;
SELECT '流水重算与余额一致' AS check_name, ((SELECT SUM(CASE WHEN movement_type='RECEIPT' THEN base_quantity WHEN movement_type='SALE' THEN -base_quantity ELSE 0 END) FROM inventory_movement)=(SELECT SUM(base_quantity) FROM inventory_batch_balance)) AS passed;
SELECT '原销售分配成本150' AS check_name, ((SELECT COUNT(*) FROM order_item_batch_allocation)=1 AND (SELECT COUNT(*) FROM order_item_batch_allocation WHERE allocated_base_quantity=5 AND batch_unit_cost=30 AND allocated_cost=150 AND source_movement_id=2)=1) AS passed;
SELECT '唯一配送已改派乙版本1' AS check_name, ((SELECT COUNT(*) FROM order_delivery)=1 AND (SELECT COUNT(*) FROM order_delivery WHERE order_id=1 AND delivery_type=2 AND delivery_status=3 AND delivery_staff_id=3 AND assignment_version=1)=1) AS passed;
SELECT '原发货时间保留且未签收' AS check_name, ((SELECT COUNT(*) FROM order_delivery WHERE delivery_at='2026-11-01 19:14:09' AND received_at IS NULL)=1) AS passed;
SELECT '未生成自提任务' AS check_name, ((SELECT COUNT(*) FROM order_pickup_request)=0) AS passed;
SELECT '唯一业务改派日志' AS check_name, ((SELECT COUNT(*) FROM sys_operation_log WHERE action='配送改派' AND status=1)=1) AS passed;
