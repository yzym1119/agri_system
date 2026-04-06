-- ============================================
-- V43: System Dict Full
-- ============================================

-- 1. 字典类型表
CREATE TABLE IF NOT EXISTS `sys_dict_type` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `code` VARCHAR(50) NOT NULL COMMENT '字典编码',
    `name` VARCHAR(100) NOT NULL COMMENT '字典名称',
    `status` TINYINT DEFAULT 1 COMMENT '1-启用 0-停用',
    `remark` VARCHAR(500),
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_dict_type_code` (`code`)
) COMMENT='字典类型表';

-- 2. 字典数据表
CREATE TABLE IF NOT EXISTS `sys_dict_data` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `type_code` VARCHAR(50) NOT NULL COMMENT '关联字典类型编码',
    `label` VARCHAR(100) NOT NULL COMMENT '展示名',
    `value` VARCHAR(100) NOT NULL COMMENT '存储值',
    `sort_order` INT DEFAULT 0 COMMENT '排序(越小越前)',
    `status` TINYINT DEFAULT 1,
    `is_default` TINYINT DEFAULT 0 COMMENT '是否默认选中',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_dict_data_type` (`type_code`)
) COMMENT='字典数据表';

-- 3. 初始化字典类型
INSERT INTO `sys_dict_type` (`code`, `name`, `status`, `remark`)
VALUES
    ('product_unit', '商品单位', 1, '商品包装单位'),
    ('dosage_form', '农药剂型', 1, '农药剂型选项'),
    ('product_manufacturer', '生产厂家', 1, '预置常见厂家'),
    ('target_village', '常用村镇', 1, '地址辅助村镇'),
    ('logistics_company', '物流公司', 1, '物流/配送公司'),
    ('refund_reason', '售后原因', 1, '售后原因列表')
ON DUPLICATE KEY UPDATE
    `name` = VALUES(`name`),
    `status` = VALUES(`status`),
    `remark` = VALUES(`remark`);

-- 4. 初始化字典数据
INSERT INTO `sys_dict_data` (`type_code`, `label`, `value`, `sort_order`, `status`, `is_default`)
VALUES
    -- 商品单位
    ('product_unit', '箱', '箱', 10, 1, 0),
    ('product_unit', '袋', '袋', 20, 1, 0),
    ('product_unit', '瓶', '瓶', 30, 1, 0),
    ('product_unit', '桶', '桶', 40, 1, 0),
    ('product_unit', '支', '支', 50, 1, 0),
    ('product_unit', '件', '件', 60, 1, 0),
    ('product_unit', '盒', '盒', 70, 1, 0),
    ('product_unit', '罐', '罐', 80, 1, 0),
    ('product_unit', '克(g)', '克(g)', 90, 1, 0),
    ('product_unit', '千克(kg)', '千克(kg)', 100, 1, 0),
    ('product_unit', '吨', '吨', 110, 1, 0),
    ('product_unit', '毫升(ml)', '毫升(ml)', 120, 1, 0),
    ('product_unit', '升(L)', '升(L)', 130, 1, 0),

    -- 农药剂型
    ('dosage_form', '乳油', '乳油', 10, 1, 0),
    ('dosage_form', '悬浮剂', '悬浮剂', 20, 1, 0),
    ('dosage_form', '水剂', '水剂', 30, 1, 0),
    ('dosage_form', '粉剂', '粉剂', 40, 1, 0),
    ('dosage_form', '颗粒剂', '颗粒剂', 50, 1, 0),
    ('dosage_form', '烟剂', '烟剂', 60, 1, 0),
    ('dosage_form', '种衣剂', '种衣剂', 70, 1, 0),
    ('dosage_form', '分散片', '分散片', 80, 1, 0),

    -- 生产厂家
    ('product_manufacturer', '先正达', '先正达', 10, 1, 0),
    ('product_manufacturer', '拜耳作物科学', '拜耳作物科学', 20, 1, 0),
    ('product_manufacturer', '陶氏杜邦', '陶氏杜邦', 30, 1, 0),
    ('product_manufacturer', '巴斯夫', '巴斯夫', 40, 1, 0),
    ('product_manufacturer', '深圳诺普信', '深圳诺普信', 50, 1, 0),
    ('product_manufacturer', '河南远见', '河南远见', 60, 1, 0),
    ('product_manufacturer', '陕西上格', '陕西上格', 70, 1, 0),

    -- 常用村镇
    ('target_village', '城关镇', '城关镇', 10, 1, 0),
    ('target_village', '赵庄', '赵庄', 20, 1, 0),
    ('target_village', '李庄', '李庄', 30, 1, 0),
    ('target_village', '王村', '王村', 40, 1, 0),
    ('target_village', '管理员维护', '管理员维护', 50, 1, 0),

    -- 物流公司
    ('logistics_company', '顺丰速运', '顺丰速运', 10, 1, 0),
    ('logistics_company', '中通快递', '中通快递', 20, 1, 0),
    ('logistics_company', '圆通速递', '圆通速递', 30, 1, 0),
    ('logistics_company', '申通快递', '申通快递', 40, 1, 0),
    ('logistics_company', '韵达快递', '韵达快递', 50, 1, 0),
    ('logistics_company', '邮政EMS', '邮政EMS', 60, 1, 0),
    ('logistics_company', '极兔速递', '极兔速递', 70, 1, 0),
    ('logistics_company', '门店自送', '门店自送', 80, 1, 0),

    -- 售后原因
    ('refund_reason', '买多了/买错了', '买多了/买错了', 10, 1, 0),
    ('refund_reason', '包装破损/泄露', '包装破损/泄露', 20, 1, 0),
    ('refund_reason', '效果不满意/有药害', '效果不满意/有药害', 30, 1, 0),
    ('refund_reason', '计划变更/退耕', '计划变更/退耕', 40, 1, 0),
    ('refund_reason', '其他原因', '其他原因', 50, 1, 0)
ON DUPLICATE KEY UPDATE
    `label` = VALUES(`label`),
    `value` = VALUES(`value`),
    `sort_order` = VALUES(`sort_order`),
    `status` = VALUES(`status`);






