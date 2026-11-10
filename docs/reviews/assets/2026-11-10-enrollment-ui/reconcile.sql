-- 仅本轮独立虚构验收库，只读复核；不是原业务库操作脚本。
USE agri_enrollment_ui_c49fa72ccfc44e78a9ca575959180ca6;
SELECT version, success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1;
SELECT id,status,customer_id,identity_id,reviewed_by,decision_request_key,active_subject IS NULL AS anchor_released FROM customer_identity_enrollment ORDER BY id;
SELECT customer_id,app_id,identity_value,auth_version FROM customer_identity ORDER BY customer_id;
SELECT id,phone,status,deleted FROM customer ORDER BY id;
SELECT COUNT(*) AS wallet_count,SUM(balance) AS balance_total,SUM(frozen_balance) AS frozen_total FROM customer_wallet;
SELECT 'wallet_transaction' AS fact,COUNT(*) AS rows_count FROM wallet_transaction UNION ALL SELECT 'order',COUNT(*) FROM `order` UNION ALL SELECT 'inventory_movement',COUNT(*) FROM inventory_movement;
