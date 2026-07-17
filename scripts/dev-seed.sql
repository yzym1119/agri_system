-- ============================================
-- 农资系统种子数据（开发环境用）
-- 用途：填充商品、客户、库存、订单等业务数据，让系统有内容可看
-- 警告：仅用于开发环境，不要在生产库执行
-- ============================================

SET NAMES utf8mb4;

-- ============================================
-- 1. 商品分类（5个）
-- ============================================
INSERT INTO `product_category` (`code`, `name`, `parent_id`, `sort_order`, `status`) VALUES
('CAT_PESTICIDE', '农药', NULL, 1, 1),
('CAT_FERTILIZER', '化肥', NULL, 2, 1),
('CAT_SEED', '种子', NULL, 3, 1),
('CAT_TOOL', '农具', NULL, 4, 1),
('CAT_OTHER', '其他', NULL, 5, 1)
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`);

-- ============================================
-- 2. 商品（20个）
-- ============================================
INSERT INTO `product` (`code`, `name`, `category_id`, `specification`, `manufacturer`, `main_unit`, `suggested_retail_price`, `vip_price`, `min_price`, `status`, `is_online`) VALUES
-- 农药类
('PROD001', '草甘膦除草剂', 1, '500g/瓶', '先正达', '瓶', 25.00, 22.00, 20.00, 1, 1),
('PROD002', '吡虫啉杀虫剂', 1, '100g/袋', '拜耳', '袋', 15.00, 13.50, 12.00, 1, 1),
('PROD003', '多菌灵杀菌剂', 1, '200g/袋', '安道麦', '袋', 18.00, 16.00, 14.00, 1, 1),
('PROD004', '敌敌畏', 1, '500ml/瓶', '扬农化工', '瓶', 30.00, 27.00, 25.00, 1, 1),
('PROD005', '阿维菌素', 1, '100ml/瓶', '红太阳', '瓶', 22.00, 20.00, 18.00, 1, 1),

-- 化肥类
('PROD006', '尿素', 2, '50kg/袋', '云天化', '袋', 120.00, 110.00, 100.00, 1, 1),
('PROD007', '复合肥', 2, '50kg/袋', '史丹利', '袋', 150.00, 135.00, 125.00, 1, 1),
('PROD008', '磷酸二铵', 2, '50kg/袋', '瓮福', '袋', 180.00, 165.00, 150.00, 1, 1),
('PROD009', '钾肥', 2, '25kg/袋', '盐湖股份', '袋', 90.00, 82.00, 75.00, 1, 1),
('PROD010', '有机肥', 2, '40kg/袋', '根力多', '袋', 60.00, 55.00, 50.00, 1, 1),

-- 种子类
('PROD011', '玉米种子', 3, '5kg/袋', '隆平高科', '袋', 80.00, 72.00, 65.00, 1, 1),
('PROD012', '水稻种子', 3, '10kg/袋', '荃银高科', '袋', 120.00, 108.00, 100.00, 1, 1),
('PROD013', '大豆种子', 3, '5kg/袋', '北大荒', '袋', 60.00, 54.00, 50.00, 1, 1),
('PROD014', '小麦种子', 3, '10kg/袋', '登海种业', '袋', 90.00, 81.00, 75.00, 1, 1),
('PROD015', '蔬菜种子套装', 3, '10种/盒', '寿光蔬菜', '盒', 45.00, 40.00, 35.00, 1, 1),

-- 农具类
('PROD016', '喷雾器', 4, '16L/台', '绿田', '台', 280.00, 252.00, 240.00, 1, 1),
('PROD017', '锄头', 4, '标准型/把', '永康五金', '把', 35.00, 31.50, 28.00, 1, 1),
('PROD018', '铁锹', 4, '标准型/把', '永康五金', '把', 40.00, 36.00, 32.00, 1, 1),

-- 其他
('PROD019', '农用薄膜', 5, '100m²/卷', '山东塑料', '卷', 150.00, 135.00, 125.00, 1, 1),
('PROD020', '遮阳网', 5, '50m²/卷', '安平丝网', '卷', 80.00, 72.00, 65.00, 1, 1)
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`);

-- ============================================
-- 3. 供应商（2个）
-- ============================================
INSERT INTO `supplier` (`code`, `name`, `contact_name`, `contact_phone`, `address`, `status`) VALUES
('SUPP001', '先正达中国', '李经理', '13800138001', '上海市浦东新区', 1),
('SUPP002', '史丹利化肥', '王经理', '13800138002', '山东省临沂市', 1)
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`);

-- ============================================
-- 4. 客户（5个）
-- ============================================
INSERT INTO `customer` (`code`, `name`, `phone`, `type`, `vip_level`, `address`, `credit_limit`, `status`) VALUES
('CUST001', '张三', '13900001111', 1, NULL, '测试村1组', 0.00, 1),
('CUST002', '李四', '13900002222', 1, NULL, '测试村2组', 0.00, 1),
('CUST003', '王五', '13900003333', 2, 1, '示范农场', 5000.00, 1),
('CUST004', '赵六', '13900004444', 2, 2, '种植合作社', 10000.00, 1),
('CUST005', '孙七', '13900005555', 1, NULL, '邻村', 0.00, 1)
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`);

-- ============================================
-- 5. 库存（门店1，每个商品10-100件）
-- ============================================
INSERT INTO `inventory` (`store_id`, `product_id`, `quantity`) VALUES
-- 农药
(1, 1, 50.00000000),
(1, 2, 80.00000000),
(1, 3, 60.00000000),
(1, 4, 30.00000000),
(1, 5, 45.00000000),

-- 化肥
(1, 6, 100.00000000),
(1, 7, 80.00000000),
(1, 8, 60.00000000),
(1, 9, 70.00000000),
(1, 10, 90.00000000),

-- 种子
(1, 11, 40.00000000),
(1, 12, 35.00000000),
(1, 13, 50.00000000),
(1, 14, 45.00000000),
(1, 15, 60.00000000),

-- 农具
(1, 16, 20.00000000),
(1, 17, 100.00000000),
(1, 18, 80.00000000),

-- 其他
(1, 19, 25.00000000),
(1, 20, 30.00000000)
ON DUPLICATE KEY UPDATE `quantity` = VALUES(`quantity`);

-- ============================================
-- 5b. 库存批次（业务核心：出库/扣减走 inventory_batch）
-- ============================================
INSERT INTO `inventory_batch` (`store_id`, `product_id`, `batch_no`, `production_date`, `expire_date`, `quantity`, `cost_price`, `is_active`, `source_type`) VALUES
(1, 1, 'BATCH20260501', '2026-01-01', '2028-01-01', 50.00000000, 15.00, 1, 'SEED'),
(1, 2, 'BATCH20260502', '2026-01-01', '2028-01-01', 80.00000000, 9.00, 1, 'SEED'),
(1, 3, 'BATCH20260503', '2026-01-01', '2028-01-01', 60.00000000, 11.00, 1, 'SEED'),
(1, 4, 'BATCH20260504', '2026-01-01', '2028-01-01', 30.00000000, 18.00, 1, 'SEED'),
(1, 5, 'BATCH20260505', '2026-01-01', '2028-01-01', 45.00000000, 13.00, 1, 'SEED'),
(1, 6, 'BATCH20260506', '2026-01-01', '2029-01-01', 100.00000000, 80.00, 1, 'SEED'),
(1, 7, 'BATCH20260507', '2026-01-01', '2029-01-01', 80.00000000, 100.00, 1, 'SEED'),
(1, 8, 'BATCH20260508', '2026-01-01', '2029-01-01', 60.00000000, 120.00, 1, 'SEED'),
(1, 9, 'BATCH20260509', '2026-01-01', '2029-01-01', 70.00000000, 55.00, 1, 'SEED'),
(1, 10, 'BATCH20260510', '2026-01-01', '2029-01-01', 90.00000000, 35.00, 1, 'SEED'),
(1, 11, 'BATCH20260511', '2026-01-01', '2027-06-01', 40.00000000, 50.00, 1, 'SEED'),
(1, 12, 'BATCH20260512', '2026-01-01', '2027-06-01', 35.00000000, 75.00, 1, 'SEED'),
(1, 13, 'BATCH20260513', '2026-01-01', '2027-06-01', 50.00000000, 38.00, 1, 'SEED'),
(1, 14, 'BATCH20260514', '2026-01-01', '2027-06-01', 45.00000000, 55.00, 1, 'SEED'),
(1, 15, 'BATCH20260515', '2026-01-01', '2027-06-01', 60.00000000, 25.00, 1, 'SEED'),
(1, 16, 'BATCH20260516', '2026-01-01', '2030-01-01', 20.00000000, 180.00, 1, 'SEED'),
(1, 17, 'BATCH20260517', '2026-01-01', '2030-01-01', 100.00000000, 20.00, 1, 'SEED'),
(1, 18, 'BATCH20260518', '2026-01-01', '2030-01-01', 80.00000000, 24.00, 1, 'SEED'),
(1, 19, 'BATCH20260519', '2026-01-01', '2028-01-01', 25.00000000, 90.00, 1, 'SEED'),
(1, 20, 'BATCH20260520', '2026-01-01', '2028-01-01', 30.00000000, 48.00, 1, 'SEED')
ON DUPLICATE KEY UPDATE `quantity` = VALUES(`quantity`);

-- ============================================
-- 6. 采购订单（3个，状态不同）
-- ============================================
-- 采购单1：已入库
INSERT INTO `purchase_order` (`purchase_no`, `supplier_id`, `store_id`, `total_amount`, `status`, `purchase_date`, `arrival_date`, `remark`) VALUES
('PO20260501001', 1, 1, 2500.00, 3, '2026-05-01', '2026-05-05', '首批农药采购')
ON DUPLICATE KEY UPDATE `status` = VALUES(`status`);

SET @po1_id = LAST_INSERT_ID();
INSERT INTO `purchase_order_item` (`purchase_order_id`, `product_id`, `quantity`, `unit`, `buy_price`, `total_price`) VALUES
(@po1_id, 1, 100, '瓶', 15.00, 1500.00),
(@po1_id, 2, 50, '袋', 10.00, 500.00),
(@po1_id, 3, 50, '袋', 10.00, 500.00)
ON DUPLICATE KEY UPDATE `quantity` = VALUES(`quantity`);

-- 采购单2：待入库
INSERT INTO `purchase_order` (`purchase_no`, `supplier_id`, `store_id`, `total_amount`, `status`, `purchase_date`, `expected_arrival_date`, `remark`) VALUES
('PO20260520001', 2, 1, 7500.00, 2, '2026-05-20', '2026-05-30', '化肥补货')
ON DUPLICATE KEY UPDATE `status` = VALUES(`status`);

SET @po2_id = LAST_INSERT_ID();
INSERT INTO `purchase_order_item` (`purchase_order_id`, `product_id`, `quantity`, `unit`, `buy_price`, `total_price`) VALUES
(@po2_id, 6, 50, '袋', 80.00, 4000.00),
(@po2_id, 7, 30, '袋', 100.00, 3000.00),
(@po2_id, 8, 20, '袋', 120.00, 2400.00)
ON DUPLICATE KEY UPDATE `quantity` = VALUES(`quantity`);

-- 采购单3：待提交
INSERT INTO `purchase_order` (`purchase_no`, `supplier_id`, `store_id`, `total_amount`, `status`, `purchase_date`, `remark`) VALUES
('PO20260530001', 1, 1, 1200.00, 1, '2026-05-30', '计划采购杀虫剂')
ON DUPLICATE KEY UPDATE `status` = VALUES(`status`);

SET @po3_id = LAST_INSERT_ID();
INSERT INTO `purchase_order_item` (`purchase_order_id`, `product_id`, `quantity`, `unit`, `buy_price`, `total_price`) VALUES
(@po3_id, 2, 100, '袋', 8.00, 800.00),
(@po3_id, 5, 50, '瓶', 8.00, 400.00)
ON DUPLICATE KEY UPDATE `quantity` = VALUES(`quantity`);

-- ============================================
-- 7. 订单（3个，状态不同）
-- ============================================
-- 订单1：已完成（POS销售）
INSERT INTO `order` (`order_no`, `store_id`, `customer_id`, `order_type`, `order_status`, `total_amount`, `discount_amount`, `pay_amount`, `payment_method`, `staff_id`, `remark`) VALUES
('ORD20260510001', 1, 1, 1, 5, 75.00, 0.00, 75.00, 1, 1, '张三购买农药')
ON DUPLICATE KEY UPDATE `order_status` = VALUES(`order_status`);

SET @ord1_id = LAST_INSERT_ID();
INSERT INTO `order_item` (`order_id`, `product_id`, `item_type`, `product_name`, `quantity`, `unit`, `unit_price`, `total_price`) VALUES
(@ord1_id, 1, 1, '草甘膦除草剂', 2, '瓶', 25.00, 50.00),
(@ord1_id, 2, 1, '吡虫啉杀虫剂', 1, '袋', 15.00, 15.00),
(@ord1_id, 17, 1, '锄头', 1, '把', 10.00, 10.00)
ON DUPLICATE KEY UPDATE `quantity` = VALUES(`quantity`);

-- 订单2：待支付（小程序）
INSERT INTO `order` (`order_no`, `store_id`, `customer_id`, `order_type`, `order_status`, `total_amount`, `discount_amount`, `pay_amount`, `payment_method`, `delivery_address`, `delivery_phone`, `remark`) VALUES
('ORD20260528001', 1, 3, 2, 1, 240.00, 12.00, 228.00, NULL, '示范农场大门', '13900003333', 'VIP客户网购')
ON DUPLICATE KEY UPDATE `order_status` = VALUES(`order_status`);

SET @ord2_id = LAST_INSERT_ID();
INSERT INTO `order_item` (`order_id`, `product_id`, `item_type`, `product_name`, `quantity`, `unit`, `unit_price`, `total_price`) VALUES
(@ord2_id, 6, 1, '尿素', 2, '袋', 120.00, 240.00)
ON DUPLICATE KEY UPDATE `quantity` = VALUES(`quantity`);

-- 订单3：待发货（小程序配送）
INSERT INTO `order` (`order_no`, `store_id`, `customer_id`, `order_type`, `order_status`, `total_amount`, `discount_amount`, `pay_amount`, `payment_method`, `delivery_address`, `delivery_phone`, `delivery_status`, `remark`) VALUES
('ORD20260529001', 1, 4, 3, 3, 300.00, 30.00, 270.00, 4, '种植合作社办公室', '13900004444', 1, '金卡VIP配送')
ON DUPLICATE KEY UPDATE `order_status` = VALUES(`order_status`);

SET @ord3_id = LAST_INSERT_ID();
INSERT INTO `order_item` (`order_id`, `product_id`, `item_type`, `product_name`, `quantity`, `unit`, `unit_price`, `total_price`) VALUES
(@ord3_id, 7, 1, '复合肥', 2, '袋', 150.00, 300.00)
ON DUPLICATE KEY UPDATE `quantity` = VALUES(`quantity`);

INSERT INTO `order_delivery` (`order_id`, `delivery_no`, `delivery_type`, `delivery_status`, `delivery_address`, `delivery_phone`) VALUES
(@ord3_id, 'DEL20260529001', 3, 1, '种植合作社办公室', '13900004444')
ON DUPLICATE KEY UPDATE `delivery_status` = VALUES(`delivery_status`);

-- ============================================
-- 完成提示
-- ============================================
SELECT '种子数据填充完成！' AS message;
SELECT COUNT(*) AS product_count FROM product;
SELECT COUNT(*) AS customer_count FROM customer;
SELECT COUNT(*) AS inventory_count FROM inventory;
SELECT COUNT(*) AS purchase_order_count FROM purchase_order;
SELECT COUNT(*) AS order_count FROM `order`;
