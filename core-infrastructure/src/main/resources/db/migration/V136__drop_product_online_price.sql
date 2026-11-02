-- 线上与门店统一使用商品零售价（V130 收口），online_price 列已无任何消费方：
-- 应用层此前已强制该列只能写入等于零售价的值、出参回显零售价，属纯噪声字段。
-- 本迁移删除该列；商品线上展示价格一律取 suggested_retail_price。
ALTER TABLE `product` DROP COLUMN `online_price`;
