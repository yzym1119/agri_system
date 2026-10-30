SELECT
 (SELECT COUNT(*) FROM `order`)=9 AS only_9_orders,
 (SELECT COUNT(*) FROM order_payment)=9 AS only_9_payments,
 (SELECT COUNT(*) FROM inventory_movement)=24 AS only_24_movements,
 (SELECT COUNT(*) FROM pos_sale_submission)=5 AS only_5_requests;

SELECT COUNT(*)=1 AND MIN(order_id)=9 AND MIN(status)='COMPLETED'
 AND MIN(staff_id)=1 AND MIN(store_id)=1 AS original_request_completed
FROM pos_sale_submission
WHERE request_key='8177e336-b15f-4368-99d4-e4243028d2c0';

SELECT COUNT(*)=1 AND SUM(amount)=50 AND SUM(refunded_amount)=0
 AS original_cash_payment_once
FROM order_payment WHERE order_id=9 AND payment_type=1;

SELECT COUNT(*)=1 AND SUM(base_quantity)=1 AND SUM(base_quantity*unit_cost)=30
 AS original_sale_once
FROM inventory_movement
WHERE reference_type='ORDER' AND reference_id=9 AND movement_type='SALE';

SELECT product_id,SUM(base_quantity) AS base_quantity
FROM inventory_batch_balance GROUP BY product_id ORDER BY product_id;

SELECT SUM(b.base_quantity*r.base_unit_cost)=622 AS remaining_stock_cost_622
FROM inventory_batch_balance b JOIN inventory_receipt_batch r ON r.id=b.batch_id;

SELECT COUNT(*)=89 AND SUM(success=0)=0
 AND MAX(CASE WHEN version REGEXP '^[0-9]+$' THEN CAST(version AS UNSIGNED) END)=133
 AS migrations_89_success_v133
FROM flyway_schema_history;
