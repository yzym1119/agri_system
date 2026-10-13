-- V129 统一库存迁移前只读检查。正式迁移自身也做预检；没有按SKU切换的操作。
-- 本文件不修复或清空数据。发现以下记录时，需要核对真实库存来源。
SELECT 'B1 单位信息不完整' check_id,id,main_unit,aux_unit,main_to_aux_ratio
FROM product WHERE TRIM(COALESCE(main_unit,''))='' OR
 (TRIM(COALESCE(aux_unit,''))<>'' AND (main_to_aux_ratio IS NULL OR main_to_aux_ratio<=0));
SELECT 'B2 旧批次日期或成本不合法' check_id,id,store_id,product_id
FROM inventory_batch WHERE quantity>0 AND
 (is_active<>1 OR cost_price IS NULL OR cost_price<=0 OR
  production_date>expire_date);
SELECT 'B3 同商品两套来源重叠' check_id,b.id,b.store_id,b.product_id
FROM inventory_batch b WHERE b.quantity>0 AND b.is_active=1 AND EXISTS
 (SELECT 1 FROM inventory_receipt_batch r WHERE r.store_id=b.store_id AND r.product_id=b.product_id);
SELECT 'B4 收货缺少明确余额' check_id,r.id,r.store_id,r.product_id
FROM inventory_receipt_batch r WHERE NOT EXISTS
 (SELECT 1 FROM inventory_batch_balance b WHERE b.batch_id=r.id);
SELECT 'B5 销售分配缺少实际销售出库' check_id,a.id,a.order_item_id
FROM order_item_batch_allocation a LEFT JOIN inventory_movement m ON m.id=a.source_movement_id
WHERE m.id IS NULL OR m.movement_type<>'SALE';
SELECT 'B6 快照正数但缺少来源' check_id,i.id,i.store_id,i.product_id
FROM inventory i WHERE i.quantity<>0 AND NOT EXISTS
 (SELECT 1 FROM inventory_batch b WHERE b.store_id=i.store_id AND b.product_id=i.product_id AND b.quantity>0 AND b.is_active=1)
 AND NOT EXISTS (SELECT 1 FROM inventory_batch_balance b WHERE b.store_id=i.store_id AND b.product_id=i.product_id);
