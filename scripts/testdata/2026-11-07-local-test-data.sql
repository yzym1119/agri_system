-- 本机开发库联调测试数据（仅 INSERT，不修改/删除任何既有行）
-- 日期：2026-11-07  用户已明确授权准备测试数据
-- 使用：MYSQL_PWD=agri1234 mysql -h 127.0.0.1 -P 3306 -u agri -D agri_system < scripts/testdata/2026-11-07-local-test-data.sql
-- 约定：所有测试数据 ID 使用 9101~9199 段，便于识别与按 cleanup 脚本清理。
-- 门店 id=1、员工 id=1（admin/admin123）、货位 id=1 均为既有种子数据，不改动。
-- 库存严格按「收货批次 + 批次货位余额 + 追加移动流水」三处事实一致写入；
-- 成本是明确的测试值（非掩盖真实成本），date_status=UNKNOWN 不猜生产/有效期。

START TRANSACTION;

-- 分类
INSERT INTO product_category (id,code,name,parent_id,level,sort_order,status,remark,created_by)
VALUES (9101,'TEST-CAT','联调测试分类',NULL,1,1,1,'2026-11-07 联调测试数据',1);

-- 商品（3 个：整件袋装 x2、瓶装 x1；价格均按持久化基准单位）
INSERT INTO product
 (id,code,barcode,name,category_id,specification,manufacturer,main_unit,
  suggested_retail_price,vip_price,min_price,avg_cost_price,
  is_online,base_shipping_fee,allow_special_fund,status,deleted,remark,min_stock,created_by)
VALUES
 (9101,'TEST-P001','6900000000001','联调复合肥 40kg',9101,'40kg/袋','联调测试厂家','袋',
  12.34,11.00,9.00,8.00,1,0.00,1,1,0,'2026-11-07 联调测试数据；基准单位=袋',10,1),
 (9102,'TEST-P002','6900000000002','联调尿素 50kg',9101,'50kg/袋','联调测试厂家','袋',
  25.00,23.00,20.00,18.00,1,0.00,0,1,0,'2026-11-07 联调测试数据；基准单位=袋',10,1),
 (9103,'TEST-P003','6900000000003','联调杀虫剂 500ml',9101,'500ml/瓶','联调测试厂家','瓶',
  38.50,NULL,30.00,28.00,1,0.00,0,1,0,'2026-11-07 联调测试数据；基准单位=瓶',5,1);

-- 基准单位（is_base_unit=1 时系数必须为 1；整件单位不允许小数基准数量）
INSERT INTO product_unit
 (id,product_id,unit_name,base_quantity_per_unit,is_base_unit,
  allow_purchase,allow_sale,allow_inventory,allow_transfer,barcode,status,created_by)
VALUES
 (9101,9101,'袋',1.00000000,1,1,1,1,1,'6900000000001',1,1),
 (9102,9102,'袋',1.00000000,1,1,1,1,1,'6900000000002',1,1),
 (9103,9103,'瓶',1.00000000,1,1,1,1,1,'6900000000003',1,1);

-- 客户（现金 / 赊销各一，赊销客户给额度）
INSERT INTO customer
 (id,code,name,phone,type,address,credit_limit,version,status,deleted,remark,created_by)
VALUES
 (9101,'TEST-C001','联调客户-现金','13900000001',1,'联调测试地址1号',0.00,0,1,0,'2026-11-07 联调测试数据',1),
 (9102,'TEST-C002','联调客户-赊销','13900000002',1,'联调测试地址2号',5000.00,0,1,0,'2026-11-07 联调测试数据',1);

-- 三钱包初始化（本金/专用金/积分，余额均为 0；不预造资金）
INSERT INTO customer_wallet (id,customer_id,wallet_type,balance,frozen_balance,version)
VALUES
 (9101,9101,1,0.00,0.00,0),(9102,9101,2,0.00,0.00,0),(9103,9101,3,0.00,0.00,0),
 (9104,9102,1,0.00,0.00,0),(9105,9102,2,0.00,0.00,0),(9106,9102,3,0.00,0.00,0);

-- 供应商
INSERT INTO supplier (id,code,name,contact_name,contact_phone,status,remark,created_by)
VALUES (9101,'TEST-S001','联调测试供应商','测试联系人','13800000009',1,'2026-11-07 联调测试数据',1);

-- 收货单（无采购单，purchase_order_id 允许为空）
INSERT INTO inventory_receipt
 (id,receipt_no,supplier_id,store_id,status,receiver_id,remark,request_key)
VALUES (9101,'TEST-RCV-20261107-01',9101,1,'RECEIVED',1,'2026-11-07 联调测试收货','TEST-RCV-KEY-9101');

-- 收货行
INSERT INTO inventory_receipt_line (id,receipt_id,line_no,product_id,store_id,remark)
VALUES
 (9101,9101,1,9101,1,'联调测试收货行1'),
 (9102,9101,2,9102,1,'联调测试收货行2'),
 (9103,9101,3,9103,1,'联调测试收货行3');

-- 收货批次（date_status=UNKNOWN，生产/有效期保持 NULL）
INSERT INTO inventory_receipt_batch
 (id,receipt_line_id,product_id,store_id,supplier_batch_no,internal_receipt_batch_no,
  original_unit_id,original_unit_name,original_quantity,original_unit_cost,
  base_unit_id,base_quantity,base_unit_cost,date_status,production_date,expiry_date,
  receiver_id,received_at)
VALUES
 (9101,9101,9101,1,'TEST-BATCH-P1','TEST-INTERNAL-9101',9101,'袋',100.00000000,8.00000000,
  9101,100.00000000,8.00000000,'UNKNOWN',NULL,NULL,1,NOW()),
 (9102,9102,9102,1,'TEST-BATCH-P2','TEST-INTERNAL-9102',9102,'袋',60.00000000,18.00000000,
  9102,60.00000000,18.00000000,'UNKNOWN',NULL,NULL,1,NOW()),
 (9103,9103,9103,1,'TEST-BATCH-P3','TEST-INTERNAL-9103',9103,'瓶',40.00000000,28.00000000,
  9103,40.00000000,28.00000000,'UNKNOWN',NULL,NULL,1,NOW());

-- 批次货位余额（货位 id=1 为既有默认后仓）
INSERT INTO inventory_batch_balance
 (id,batch_id,product_id,store_id,location_id,base_quantity,reserved_base_quantity,version)
VALUES
 (9101,9101,9101,1,1,100.00000000,0.00000000,0),
 (9102,9102,9102,1,1,60.00000000,0.00000000,0),
 (9103,9103,9103,1,1,40.00000000,0.00000000,0);

-- 追加收货流水（只 INSERT，追加事实表禁止 UPDATE/DELETE）
INSERT INTO inventory_movement
 (id,movement_no,idempotency_key,batch_id,product_id,store_id,from_location_id,to_location_id,
  movement_type,original_unit_id,original_unit_name,original_quantity,unit_cost,base_quantity,
  reference_type,reference_id,operator_id,occurred_at,remark)
VALUES
 (9101,'TEST-MOV-9101','TEST-MOV-KEY-9101',9101,9101,1,NULL,1,'RECEIPT',9101,'袋',
  100.00000000,8.00000000,100.00000000,'RECEIPT',9101,1,NOW(),'2026-11-07 联调测试收货'),
 (9102,'TEST-MOV-9102','TEST-MOV-KEY-9102',9102,9102,1,NULL,1,'RECEIPT',9102,'袋',
  60.00000000,18.00000000,60.00000000,'RECEIPT',9101,1,NOW(),'2026-11-07 联调测试收货'),
 (9103,'TEST-MOV-9103','TEST-MOV-KEY-9103',9103,9103,1,NULL,1,'RECEIPT',9103,'瓶',
  40.00000000,28.00000000,40.00000000,'RECEIPT',9101,1,NOW(),'2026-11-07 联调测试收货');

-- 旧汇总表仅作统一库存行锁与盘点账面对照，数量与批次余额保持一致
INSERT INTO inventory (id,store_id,product_id,quantity)
VALUES
 (9101,1,9101,100.00000000),
 (9102,1,9102,60.00000000),
 (9103,1,9103,40.00000000);

COMMIT;
