-- 唯一索引：名称 + 规格 + 厂家
ALTER TABLE `product`
  ADD UNIQUE INDEX `uk_product_name_spec_manu` (`name`, `specification`, `manufacturer`);









