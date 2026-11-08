-- ============================================
-- 农资行业进销存系统 - 当前数据库结构快照
-- 生成方式：2026-11-01 由 V138 迁移后的开发库 mysqldump --no-data 导出
-- 数据库: MySQL 8.2+（CHECK 约束与追加事实触发器依赖 MySQL >= 8.0.16）
-- 字符集: utf8mb4 / 排序规则: utf8mb4_unicode_ci（全库统一）
-- 表数: 71（不含 flyway_schema_history，该表由 Flyway 自行管理）
--
-- ⚠ 重要：本文件是结构参考快照，不是建库脚本。
--   新库一律由 Flyway 从空库依次迁移（core-infrastructure/src/main/resources/db/migration/，
--   当前最高 V138），不要导入本快照后假装已完成迁移。表顺序为字母序，业务分组见 ddl/README.md。
-- ============================================

-- MySQL dump 10.13  Distrib 9.6.0, for macos26.3 (arm64)
--
-- Host: 127.0.0.1    Database: agri_system
-- ------------------------------------------------------
-- Server version	9.6.0

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- GTID state at the beginning of the backup
--


--
-- Table structure for table `after_sales`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `after_sales` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `after_sales_no` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '售后单号',
  `order_id` bigint NOT NULL COMMENT '关联订单ID',
  `customer_id` bigint DEFAULT NULL COMMENT '客户ID（可为空，POS散客）',
  `store_id` bigint NOT NULL COMMENT '门店ID',
  `type` tinyint NOT NULL COMMENT '售后类型：1-订单取消/拦截，2-退货退款',
  `status` tinyint NOT NULL COMMENT '状态：10-待审核，40-待退款，50-已完成，90-已驳回',
  `refund_amount` decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '应退金额（元）',
  `reason` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '申请原因',
  `audit_remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '审核备注',
  `audit_by` bigint DEFAULT NULL COMMENT '审核人ID',
  `audit_time` datetime DEFAULT NULL COMMENT '审核时间',
  `refund_by` bigint DEFAULT NULL COMMENT '退款操作人ID',
  `refund_time` datetime DEFAULT NULL COMMENT '退款时间',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `credit_reduction_amount` decimal(10,2) DEFAULT NULL,
  `cash_refund_amount` decimal(10,2) DEFAULT NULL,
  `refund_quote_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `refund_request_hash` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_after_sales_no` (`after_sales_no`),
  UNIQUE KEY `uk_after_sales_refund_quote` (`refund_quote_id`),
  KEY `idx_order_status` (`order_id`, `status`),
  KEY `idx_customer` (`customer_id`),
  CONSTRAINT `fk_after_sales_order` FOREIGN KEY (`order_id`) REFERENCES `order` (`id`),
  CONSTRAINT `chk_after_sales_financial_split` CHECK ((((`credit_reduction_amount` is null) and (`cash_refund_amount` is null)) or ((`credit_reduction_amount` is not null) and (`cash_refund_amount` is not null) and (`credit_reduction_amount` >= 0) and (`cash_refund_amount` >= 0) and ((`credit_reduction_amount` + `cash_refund_amount`) = `refund_amount`)))),
  CONSTRAINT `chk_after_sales_refund_identity` CHECK ((((`refund_quote_id` is null) and (`refund_request_hash` is null)) or ((`refund_quote_id` is not null) and (`refund_request_hash` is not null) and (char_length(`refund_quote_id`) = 64) and (char_length(`refund_request_hash`) = 64))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='售后主表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `after_sales_item`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `after_sales_item` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `after_sales_id` bigint NOT NULL COMMENT '售后单ID',
  `order_item_id` bigint NOT NULL COMMENT '订单明细ID',
  `product_id` bigint NOT NULL COMMENT '商品ID',
  `quantity` decimal(10,4) NOT NULL COMMENT '退货数量',
  `refund_amount` decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '明细退款金额（元）',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_after_sales_id` (`after_sales_id`),
  KEY `idx_order_item_id` (`order_item_id`),
  KEY `fk_after_sales_item_product` (`product_id`),
  CONSTRAINT `fk_after_sales_item_main` FOREIGN KEY (`after_sales_id`) REFERENCES `after_sales` (`id`),
  CONSTRAINT `fk_after_sales_item_order_item` FOREIGN KEY (`order_item_id`) REFERENCES `order_item` (`id`),
  CONSTRAINT `fk_after_sales_item_product` FOREIGN KEY (`product_id`) REFERENCES `product` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='售后明细表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `commission_rule`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `commission_rule` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `product_id` bigint DEFAULT NULL COMMENT '商品ID（为空表示通用规则）',
  `staff_id` bigint DEFAULT NULL COMMENT '员工ID（为空表示通用规则）',
  `rule_category` tinyint NOT NULL DEFAULT '1' COMMENT '1-销售 2-充值 3-拉新 4-品牌推广 5-套餐',
  `target_id` bigint DEFAULT NULL COMMENT '商品/套餐/规则 等ID',
  `calc_mode` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'FIXED' COMMENT 'FIXED/PROFIT_PERCENT/SALES_PERCENT',
  `calc_value` decimal(10,4) NOT NULL DEFAULT '0.0000' COMMENT '固定金额或比例',
  `max_per_order` decimal(10,2) DEFAULT NULL COMMENT '单订单提成封顶',
  `max_per_item` decimal(10,2) DEFAULT NULL COMMENT '单行提成封顶',
  `legacy_rule_type` tinyint DEFAULT NULL COMMENT '历史字段-规则类型',
  `legacy_fixed_amount` decimal(10,2) DEFAULT NULL,
  `legacy_profit_percentage` decimal(5,2) DEFAULT NULL,
  `priority` int DEFAULT '0' COMMENT '优先级（数字越大优先级越高）',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：1-有效，2-无效',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '1-启用 0-禁用',
  `effective_date` date DEFAULT NULL COMMENT '生效日期',
  `expiry_date` date DEFAULT NULL COMMENT '失效日期',
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人ID',
  PRIMARY KEY (`id`),
  KEY `idx_product_id` (`product_id`),
  KEY `idx_staff_id` (`staff_id`),
  KEY `idx_rule_type` (`legacy_rule_type`),
  KEY `idx_status` (`status`),
  KEY `idx_priority` (`priority`),
  KEY `idx_rule_category` (`rule_category`),
  KEY `idx_enabled` (`enabled`),
  CONSTRAINT `fk_commission_product` FOREIGN KEY (`product_id`) REFERENCES `product` (`id`),
  CONSTRAINT `fk_commission_staff` FOREIGN KEY (`staff_id`) REFERENCES `staff` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='提成规则表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `coupon_template`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `coupon_template` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '模板名称',
  `type` tinyint DEFAULT '1' COMMENT '类型：1-满减券，2-折扣券',
  `amount` decimal(10,2) NOT NULL COMMENT '面额/折扣率',
  `min_order_amount` decimal(10,2) DEFAULT '0.00' COMMENT '使用门槛（元）',
  `limit_category_id` bigint DEFAULT NULL COMMENT '限制分类ID（为空则全场通用）',
  `limit_product_id` bigint DEFAULT NULL COMMENT '限制商品ID（为空则不限）',
  `validity_type` tinyint NOT NULL COMMENT '有效期类型：1-固定日期范围，2-领取后有效天数',
  `valid_start_time` datetime DEFAULT NULL COMMENT '固定有效期开始',
  `valid_end_time` datetime DEFAULT NULL COMMENT '固定有效期结束',
  `valid_days` int DEFAULT '30' COMMENT '领取后有效天数',
  `total_count` int DEFAULT '-1' COMMENT '发放总量限制（-1不限）',
  `status` tinyint DEFAULT '1' COMMENT '状态：1-启用，0-停用',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：1-已删 0-未删',
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_status` (`status`),
  KEY `idx_limit_category` (`limit_category_id`),
  KEY `idx_limit_product` (`limit_product_id`),
  CONSTRAINT `fk_template_limit_category` FOREIGN KEY (`limit_category_id`) REFERENCES `product_category` (`id`),
  CONSTRAINT `fk_template_limit_product` FOREIGN KEY (`limit_product_id`) REFERENCES `product` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='优惠券模板表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `credit_account`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `credit_account` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `customer_id` bigint NOT NULL COMMENT '客户ID',
  `order_id` bigint NOT NULL COMMENT '订单ID',
  `credit_amount` decimal(10,2) NOT NULL COMMENT '赊销金额（元）',
  `paid_amount` decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '已还金额（元）',
  `remaining_amount` decimal(10,2) NOT NULL COMMENT '剩余金额（元）',
  `due_date` date DEFAULT NULL COMMENT '到期日期',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：1-未还清，2-已还清，3-已逾期',
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `store_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_credit_account_order` (`order_id`),
  KEY `idx_customer_id` (`customer_id`),
  KEY `idx_order_id` (`order_id`),
  KEY `idx_status` (`status`),
  KEY `idx_due_date` (`due_date`),
  KEY `idx_credit_store_customer` (`store_id`, `customer_id`),
  CONSTRAINT `fk_credit_account_store` FOREIGN KEY (`store_id`) REFERENCES `store` (`id`),
  CONSTRAINT `fk_credit_customer` FOREIGN KEY (`customer_id`) REFERENCES `customer` (`id`),
  CONSTRAINT `fk_credit_order` FOREIGN KEY (`order_id`) REFERENCES `order` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='赊销台账表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `credit_repayment_log`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `credit_repayment_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `repayment_no` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '还款流水号',
  `customer_id` bigint NOT NULL,
  `store_id` bigint NOT NULL COMMENT '收款门店',
  `amount` decimal(10,2) NOT NULL COMMENT '还款金额',
  `payment_method` tinyint NOT NULL COMMENT '还款方式：1-现金 2-微信 3-支付宝 4-银行转账',
  `transaction_id` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '外部单号',
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_by` bigint DEFAULT NULL COMMENT '操作员',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `credit_account_id` bigint DEFAULT NULL,
  `request_key` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `refunded_amount` decimal(10,2) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_credit_store_request` (`store_id`, `request_key`),
  KEY `idx_customer_time` (`customer_id`, `created_at`),
  KEY `idx_credit_repayment_account` (`credit_account_id`, `created_at`),
  KEY `idx_credit_repayment_batch` (`store_id`, `repayment_no`),
  CONSTRAINT `fk_credit_repayment_account` FOREIGN KEY (`credit_account_id`) REFERENCES `credit_account` (`id`),
  CONSTRAINT `chk_credit_repayment_refund` CHECK (((`refunded_amount` is null) or ((`refunded_amount` >= 0) and (`refunded_amount` <= `amount`)))),
  CONSTRAINT `chk_credit_request_source` CHECK (((`request_key` is null) or ((`credit_account_id` is not null) and (`refunded_amount` is not null))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='赊销还款流水表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `credit_statement`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `credit_statement` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `customer_id` bigint NOT NULL COMMENT '客户ID',
  `statement_no` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '对账单号',
  `statement_date` date NOT NULL COMMENT '对账日期',
  `start_date` date NOT NULL COMMENT '对账开始日期',
  `end_date` date NOT NULL COMMENT '对账结束日期',
  `total_credit_amount` decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '赊销总额（元）',
  `total_paid_amount` decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '已还总额（元）',
  `total_remaining_amount` decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '剩余总额（元）',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：1-待确认，2-已确认，3-已争议',
  `confirmed_at` datetime DEFAULT NULL COMMENT '确认时间',
  `confirmed_by` bigint DEFAULT NULL COMMENT '确认人ID（客户或员工）',
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_statement_no` (`statement_no`),
  KEY `idx_customer_id` (`customer_id`),
  KEY `idx_statement_date` (`statement_date`),
  KEY `idx_status` (`status`),
  CONSTRAINT `fk_statement_customer` FOREIGN KEY (`customer_id`) REFERENCES `customer` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='对账历史表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `customer`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `customer` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `code` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '客户编码',
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '客户姓名',
  `company_name` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '公司/农场名称',
  `tax_id` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '纳税人识别号',
  `invoice_title` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '发票抬头',
  `invoice_bank` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '开票银行及账号',
  `invoice_address` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '开票地址',
  `invoice_phone` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '开票电话',
  `phone` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '手机号',
  `type` tinyint NOT NULL DEFAULT '1' COMMENT '客户类型：1-普通客户，2-VIP客户',
  `vip_level` tinyint DEFAULT NULL COMMENT 'VIP等级：1-普通VIP，2-银卡VIP，3-金卡VIP，4-钻石VIP（仅VIP客户有效）',
  `vip_start_date` date DEFAULT NULL COMMENT 'VIP开始日期',
  `vip_end_date` date DEFAULT NULL COMMENT 'VIP结束日期',
  `address` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '地址',
  `land_area` decimal(10,2) DEFAULT NULL COMMENT '种植面积（亩）',
  `crop_type` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '主要种植作物（如：番茄、柑橘、水稻）',
  `id_card` varchar(18) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '身份证号',
  `credit_limit` decimal(10,2) DEFAULT '0.00' COMMENT '赊销额度（元）',
  `total_order_count` int DEFAULT '0' COMMENT '累计成交单数',
  `total_order_amount` decimal(12,2) DEFAULT '0.00' COMMENT '累计成交总额',
  `last_order_time` datetime DEFAULT NULL COMMENT '最近下单时间',
  `bind_staff_id` bigint DEFAULT NULL COMMENT '归属员工ID',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本号',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：1-正常，2-停用',
  `deleted` tinyint DEFAULT '0' COMMENT '逻辑删除：1-已删 0-未删',
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人ID',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_customer_code` (`code`),
  UNIQUE KEY `uk_customer_phone` (`phone`),
  KEY `idx_customer_type` (`type`),
  KEY `idx_customer_status` (`status`),
  KEY `idx_customer_vip_level` (`vip_level`),
  KEY `idx_bind_staff` (`bind_staff_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='客户表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `customer_benefit_log`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `customer_benefit_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `customer_id` bigint NOT NULL COMMENT '客户ID',
  `source_type` tinyint NOT NULL COMMENT '来源类型：1-每月权益(自动) 2-充值赠送 3-手动发放',
  `benefit_content` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '内容快照(如: 现金50元, 化肥券x1)',
  `associated_id` bigint DEFAULT NULL COMMENT '关联ID(如充值规则ID, 模板ID)',
  `created_by` bigint DEFAULT NULL COMMENT '操作人(0为系统)',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_customer_time` (`customer_id`, `created_at`),
  KEY `idx_source_type` (`source_type`),
  CONSTRAINT `fk_benefit_log_customer` FOREIGN KEY (`customer_id`) REFERENCES `customer` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='客户权益获取流水表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `customer_coupon`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `customer_coupon` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `customer_id` bigint NOT NULL COMMENT '关联客户ID',
  `template_id` bigint DEFAULT NULL COMMENT '来源模板ID',
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '券名称',
  `type` tinyint DEFAULT '1' COMMENT '类型：1-满减券，2-折扣券（从模板复制）',
  `amount` decimal(10,2) NOT NULL COMMENT '面额（元）',
  `min_order_amount` decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '使用门槛金额（元）',
  `start_time` datetime DEFAULT NULL COMMENT '生效时间',
  `end_time` datetime DEFAULT NULL COMMENT '过期时间',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：1-未使用, 2-已使用, 3-已过期, 4-已作废',
  `limit_category_id` bigint DEFAULT NULL COMMENT '限制商品分类ID',
  `limit_product_id` bigint DEFAULT NULL COMMENT '限制具体商品ID',
  `used_order_id` bigint DEFAULT NULL COMMENT '使用时关联的订单ID',
  `used_time` datetime DEFAULT NULL COMMENT '使用时间',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_limit_category` (`limit_category_id`),
  KEY `idx_limit_product` (`limit_product_id`),
  KEY `idx_used_order` (`used_order_id`),
  KEY `idx_customer_status` (`customer_id`, `status`),
  KEY `idx_template_id` (`template_id`),
  CONSTRAINT `fk_coupon_customer` FOREIGN KEY (`customer_id`) REFERENCES `customer` (`id`),
  CONSTRAINT `fk_coupon_limit_category` FOREIGN KEY (`limit_category_id`) REFERENCES `product_category` (`id`),
  CONSTRAINT `fk_coupon_limit_product` FOREIGN KEY (`limit_product_id`) REFERENCES `product` (`id`),
  CONSTRAINT `fk_coupon_template` FOREIGN KEY (`template_id`) REFERENCES `coupon_template` (`id`),
  CONSTRAINT `fk_coupon_used_order` FOREIGN KEY (`used_order_id`) REFERENCES `order` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='客户红包/代金券表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `customer_follow_up`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `customer_follow_up` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `customer_id` bigint NOT NULL COMMENT '客户ID',
  `staff_id` bigint NOT NULL COMMENT '跟进员工ID',
  `follow_up_type` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '跟进类型：repurchase-复购提醒, usage-使用回访, complaint-投诉跟进, birthday-生日关怀',
  `content` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '跟进内容/备注',
  `next_follow_date` date DEFAULT NULL COMMENT '下次跟进日期',
  `related_order_id` bigint DEFAULT NULL COMMENT '关联订单ID',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '状态：0-待跟进, 1-已完成, 2-已取消',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_followup_customer` (`customer_id`),
  KEY `idx_followup_staff_date` (`staff_id`, `next_follow_date`),
  KEY `idx_followup_status` (`status`, `next_follow_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='客户跟进记录表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `customer_identity`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `customer_identity` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `customer_id` bigint NOT NULL COMMENT '客户ID',
  `identity_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '身份类型：WECHAT_OPENID-微信OpenID，PHONE-手机号，OTHER-其他',
  `identity_value` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '身份值（OpenID/手机号等）',
  `session_key` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '微信SessionKey（仅微信登录时使用）',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：1-正常，2-停用',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_identity_type_value` (`identity_type`, `identity_value`),
  KEY `idx_customer_id` (`customer_id`),
  CONSTRAINT `fk_identity_customer` FOREIGN KEY (`customer_id`) REFERENCES `customer` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='客户身份表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `customer_wallet`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `customer_wallet` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `customer_id` bigint NOT NULL COMMENT '客户ID',
  `wallet_type` tinyint NOT NULL COMMENT '钱包类型：1-本金，2-专用金，3-积分',
  `balance` decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '余额',
  `frozen_balance` decimal(10,2) DEFAULT '0.00' COMMENT '冻结金额/积分',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本号',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_customer_wallet_type` (`customer_id`, `wallet_type`),
  KEY `idx_customer_id` (`customer_id`),
  CONSTRAINT `fk_wallet_customer` FOREIGN KEY (`customer_id`) REFERENCES `customer` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='客户钱包表';
/*!40101 SET character_set_client = @saved_cs_client */;


--
-- Table structure for table `inventory`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `inventory` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `store_id` bigint NOT NULL COMMENT '门店ID',
  `product_id` bigint NOT NULL COMMENT '商品ID',
  `quantity` decimal(20,8) NOT NULL DEFAULT '0.00000000',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_inventory_store_product` (`store_id`, `product_id`),
  KEY `idx_store_id` (`store_id`),
  KEY `idx_product_id` (`product_id`),
  CONSTRAINT `fk_inventory_product` FOREIGN KEY (`product_id`) REFERENCES `product` (`id`),
  CONSTRAINT `fk_inventory_store` FOREIGN KEY (`store_id`) REFERENCES `store` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='库存快照表（8位小数精度，支持任意换算比例）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `inventory_adjustment`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `inventory_adjustment` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `adjustment_no` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '调整单号',
  `store_id` bigint NOT NULL COMMENT '门店ID',
  `product_id` bigint NOT NULL COMMENT '商品ID',
  `adjustment_type` tinyint NOT NULL COMMENT '调整类型：1-盘盈，2-盘亏，3-报损，4-其他',
  `quantity_before` decimal(20,8) NOT NULL,
  `quantity_after` decimal(20,8) NOT NULL,
  `quantity_diff` decimal(20,8) NOT NULL,
  `unit` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '单位',
  `reason` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '调整原因',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_adjustment_no` (`adjustment_no`),
  KEY `idx_store_id` (`store_id`),
  KEY `idx_product_id` (`product_id`),
  KEY `idx_adjustment_type` (`adjustment_type`),
  CONSTRAINT `fk_adjustment_product` FOREIGN KEY (`product_id`) REFERENCES `product` (`id`),
  CONSTRAINT `fk_adjustment_store` FOREIGN KEY (`store_id`) REFERENCES `store` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='库存调整表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `inventory_balance_feature_status`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `inventory_balance_feature_status` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `store_id` bigint NOT NULL,
  `product_id` bigint NOT NULL,
  `balance_mode` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'LEGACY',
  `cutover_at` datetime DEFAULT NULL,
  `cutover_by` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_inventory_balance_feature_store_product` (`store_id`, `product_id`),
  KEY `fk_inventory_balance_feature_product` (`product_id`),
  KEY `fk_inventory_balance_feature_operator` (`cutover_by`),
  CONSTRAINT `fk_inventory_balance_feature_operator` FOREIGN KEY (`cutover_by`) REFERENCES `staff` (`id`),
  CONSTRAINT `fk_inventory_balance_feature_product` FOREIGN KEY (`product_id`) REFERENCES `product` (`id`),
  CONSTRAINT `fk_inventory_balance_feature_store` FOREIGN KEY (`store_id`) REFERENCES `store` (`id`),
  CONSTRAINT `chk_inventory_balance_feature_mode` CHECK ((`balance_mode` in (_utf8mb4'LEGACY',_utf8mb4'BATCH_LOCATION')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='SKU库存余额权威来源切换状态';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `inventory_batch`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `inventory_batch` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `store_id` bigint NOT NULL COMMENT '门店ID',
  `product_id` bigint NOT NULL COMMENT '商品ID',
  `batch_no` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '批次号（手动输入或系统生成）',
  `production_date` date DEFAULT NULL COMMENT '生产日期',
  `expire_date` date DEFAULT NULL COMMENT '有效期至',
  `quantity` decimal(18,8) NOT NULL DEFAULT '0.00000000' COMMENT '批次数量（主单位，8位小数）',
  `cost_price` decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '该批次的真实成本价（不可变，不允许修改）',
  `is_active` tinyint NOT NULL DEFAULT '1' COMMENT '是否有效：1-有效，0-归档/售罄',
  `source_type` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '来源类型：PURCHASE-采购，TRANSFER_IN-调拨入库，ADJUSTMENT-盘盈等',
  `source_id` bigint DEFAULT NULL COMMENT '来源单据ID',
  `source_line_no` int DEFAULT NULL COMMENT '来源单据行号',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_batch_same_source` (`store_id`, `product_id`,`batch_no`, `source_type`,`source_id`, `source_line_no`) COMMENT '同源批次唯一性约束',
  KEY `idx_expire_date` (`expire_date`),
  KEY `idx_created_at` (`created_at`),
  KEY `fk_batch_product` (`product_id`),
  KEY `idx_fifo` (`store_id`, `product_id`,`is_active`, `expire_date`,`created_at`) COMMENT 'FIFO排序索引（用于悲观锁查询）',
  CONSTRAINT `fk_batch_product` FOREIGN KEY (`product_id`) REFERENCES `product` (`id`),
  CONSTRAINT `fk_batch_store` FOREIGN KEY (`store_id`) REFERENCES `store` (`id`),
  CONSTRAINT `chk_quantity_non_negative` CHECK ((`quantity` >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='库存批次表（8位小数精度，支持任意换算比例）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `inventory_batch_balance`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `inventory_batch_balance` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `batch_id` bigint NOT NULL,
  `product_id` bigint NOT NULL,
  `store_id` bigint NOT NULL,
  `location_id` bigint NOT NULL,
  `base_quantity` decimal(20,8) NOT NULL DEFAULT '0.00000000',
  `reserved_base_quantity` decimal(20,8) NOT NULL DEFAULT '0.00000000',
  `version` int NOT NULL DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_batch_location` (`batch_id`, `location_id`),
  KEY `idx_inventory_batch_balance_location` (`location_id`),
  KEY `fk_inventory_batch_balance_batch` (`batch_id`, `product_id`,`store_id`),
  KEY `fk_inventory_batch_balance_location` (`location_id`, `store_id`),
  KEY `idx_ibb_store_product` (`store_id`, `product_id`),
  CONSTRAINT `fk_inventory_batch_balance_batch` FOREIGN KEY (`batch_id`, `product_id`, `store_id`) REFERENCES `inventory_receipt_batch` (`id`, `product_id`, `store_id`),
  CONSTRAINT `fk_inventory_batch_balance_location` FOREIGN KEY (`location_id`, `store_id`) REFERENCES `inventory_location` (`id`, `store_id`),
  CONSTRAINT `chk_inventory_batch_balance_quantity` CHECK (((`base_quantity` >= 0) and (`reserved_base_quantity` >= 0) and (`reserved_base_quantity` <= `base_quantity`)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='批次位置余额';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `inventory_decision_log`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `inventory_decision_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `risk_kind` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `entity_type` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `entity_id` bigint NOT NULL,
  `suggested_action` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `decision_action` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `decision_reason` varchar(1000) COLLATE utf8mb4_unicode_ci NOT NULL,
  `operator_id` bigint DEFAULT NULL,
  `confirmer_id` bigint DEFAULT NULL,
  `decided_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_inventory_decision_entity` (`entity_type`, `entity_id`),
  KEY `idx_inventory_decision_risk_time` (`risk_kind`, `decided_at`),
  KEY `fk_inventory_decision_operator` (`operator_id`),
  KEY `fk_inventory_decision_confirmer` (`confirmer_id`),
  CONSTRAINT `fk_inventory_decision_confirmer` FOREIGN KEY (`confirmer_id`) REFERENCES `staff` (`id`),
  CONSTRAINT `fk_inventory_decision_operator` FOREIGN KEY (`operator_id`) REFERENCES `staff` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='库存风险决策留痕（追加写）';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = utf8mb4 */ ;
/*!50003 SET character_set_results = utf8mb4 */ ;
/*!50003 SET collation_connection  = utf8mb4_0900_ai_ci */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION' */ ;
DELIMITER ;;
/*!50003 CREATE*/ /*!50003 TRIGGER `trg_inventory_decision_log_no_update` BEFORE UPDATE ON `inventory_decision_log` FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'inventory_decision_log is append-only' */;;
DELIMITER ;
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = utf8mb4 */ ;
/*!50003 SET character_set_results = utf8mb4 */ ;
/*!50003 SET collation_connection  = utf8mb4_0900_ai_ci */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION' */ ;
DELIMITER ;;
/*!50003 CREATE*/ /*!50003 TRIGGER `trg_inventory_decision_log_no_delete` BEFORE DELETE ON `inventory_decision_log` FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'inventory_decision_log is append-only' */;;
DELIMITER ;
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;

--
-- Table structure for table `inventory_location`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `inventory_location` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `store_id` bigint NOT NULL,
  `location_type` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL,
  `code` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` tinyint NOT NULL DEFAULT '1',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `default_location_key` varchar(30) COLLATE utf8mb4_unicode_ci GENERATED ALWAYS AS ((case when (`code` is null) then `location_type` else NULL end)) VIRTUAL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_inventory_location_id_store` (`id`, `store_id`),
  UNIQUE KEY `uk_inventory_location_code` (`store_id`, `code`),
  UNIQUE KEY `uk_inventory_location_default` (`store_id`, `default_location_key`),
  KEY `idx_inventory_location_type` (`store_id`, `location_type`,`status`),
  CONSTRAINT `fk_inventory_location_store` FOREIGN KEY (`store_id`) REFERENCES `store` (`id`),
  CONSTRAINT `chk_inventory_location_type` CHECK ((`location_type` in (_utf8mb4'SALES_FLOOR',_utf8mb4'STORE_BACKROOM',_utf8mb4'CENTRAL_WAREHOUSE',_utf8mb4'OTHER_STORE')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='库存位置';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `inventory_movement`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `inventory_movement` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `movement_no` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `idempotency_key` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `batch_id` bigint NOT NULL,
  `product_id` bigint NOT NULL,
  `store_id` bigint NOT NULL,
  `from_location_id` bigint DEFAULT NULL,
  `to_location_id` bigint DEFAULT NULL,
  `movement_type` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL,
  `transfer_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `original_unit_id` bigint DEFAULT NULL,
  `original_unit_name` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `original_quantity` decimal(20,8) NOT NULL,
  `unit_cost` decimal(20,8) NOT NULL,
  `base_quantity` decimal(20,8) NOT NULL,
  `reference_type` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `reference_id` bigint DEFAULT NULL,
  `operator_id` bigint DEFAULT NULL,
  `occurred_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_inventory_movement_no` (`movement_no`),
  UNIQUE KEY `uk_inventory_movement_batch_product` (`id`, `product_id`),
  UNIQUE KEY `uk_inventory_movement_product_store` (`id`, `product_id`,`store_id`),
  UNIQUE KEY `uk_inventory_movement_idempotency` (`idempotency_key`),
  KEY `idx_inventory_movement_batch_time` (`batch_id`, `occurred_at`),
  KEY `idx_inventory_movement_reference` (`reference_type`, `reference_id`),
  KEY `fk_inventory_movement_batch` (`batch_id`, `product_id`,`store_id`),
  KEY `fk_inventory_movement_from_location` (`from_location_id`, `store_id`),
  KEY `fk_inventory_movement_to_location` (`to_location_id`, `store_id`),
  KEY `fk_inventory_movement_original_unit` (`original_unit_id`, `product_id`),
  KEY `fk_inventory_movement_operator` (`operator_id`),
  KEY `idx_inventory_movement_transfer` (`transfer_id`, `occurred_at`),
  CONSTRAINT `fk_inventory_movement_batch` FOREIGN KEY (`batch_id`, `product_id`, `store_id`) REFERENCES `inventory_receipt_batch` (`id`, `product_id`, `store_id`),
  CONSTRAINT `fk_inventory_movement_from_location` FOREIGN KEY (`from_location_id`, `store_id`) REFERENCES `inventory_location` (`id`, `store_id`),
  CONSTRAINT `fk_inventory_movement_operator` FOREIGN KEY (`operator_id`) REFERENCES `staff` (`id`),
  CONSTRAINT `fk_inventory_movement_original_unit` FOREIGN KEY (`original_unit_id`, `product_id`) REFERENCES `product_unit` (`id`, `product_id`),
  CONSTRAINT `fk_inventory_movement_to_location` FOREIGN KEY (`to_location_id`, `store_id`) REFERENCES `inventory_location` (`id`, `store_id`),
  CONSTRAINT `chk_inventory_movement_quantity` CHECK (((`original_quantity` > 0) and (`base_quantity` > 0) and (`unit_cost` > 0))),
  CONSTRAINT `chk_inventory_movement_type` CHECK ((`movement_type` in (_utf8mb4'RECEIPT',_utf8mb4'SALE',_utf8mb4'REPLENISHMENT',_utf8mb4'PICK',_utf8mb4'TRANSFER',_utf8mb4'COUNT_ADJUSTMENT',_utf8mb4'RETURN')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='库存移动事实';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = utf8mb4 */ ;
/*!50003 SET character_set_results = utf8mb4 */ ;
/*!50003 SET collation_connection  = utf8mb4_0900_ai_ci */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION' */ ;
DELIMITER ;;
/*!50003 CREATE*/ /*!50003 TRIGGER `trg_inventory_movement_no_update` BEFORE UPDATE ON `inventory_movement` FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'inventory_movement is append-only' */;;
DELIMITER ;
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = utf8mb4 */ ;
/*!50003 SET character_set_results = utf8mb4 */ ;
/*!50003 SET collation_connection  = utf8mb4_0900_ai_ci */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION' */ ;
DELIMITER ;;
/*!50003 CREATE*/ /*!50003 TRIGGER `trg_inventory_movement_no_delete` BEFORE DELETE ON `inventory_movement` FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'inventory_movement is append-only' */;;
DELIMITER ;
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;

--
-- Table structure for table `inventory_receipt`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `inventory_receipt` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `receipt_no` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `purchase_order_id` bigint DEFAULT NULL,
  `supplier_id` bigint DEFAULT NULL,
  `store_id` bigint NOT NULL,
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'RECEIVED',
  `received_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `receiver_id` bigint DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `request_key` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_inventory_receipt_id_store` (`id`, `store_id`),
  UNIQUE KEY `uk_inventory_receipt_no` (`receipt_no`),
  UNIQUE KEY `uk_inventory_receipt_request_key` (`request_key`),
  KEY `idx_inventory_receipt_store_time` (`store_id`, `received_at`),
  KEY `idx_inventory_receipt_supplier` (`supplier_id`),
  KEY `fk_inventory_receipt_purchase_order` (`purchase_order_id`),
  KEY `fk_inventory_receipt_receiver` (`receiver_id`),
  CONSTRAINT `fk_inventory_receipt_purchase_order` FOREIGN KEY (`purchase_order_id`) REFERENCES `purchase_order` (`id`),
  CONSTRAINT `fk_inventory_receipt_receiver` FOREIGN KEY (`receiver_id`) REFERENCES `staff` (`id`),
  CONSTRAINT `fk_inventory_receipt_store` FOREIGN KEY (`store_id`) REFERENCES `store` (`id`),
  CONSTRAINT `fk_inventory_receipt_supplier` FOREIGN KEY (`supplier_id`) REFERENCES `supplier` (`id`),
  CONSTRAINT `chk_inventory_receipt_status` CHECK ((`status` in (_utf8mb4'DRAFT',_utf8mb4'RECEIVED',_utf8mb4'CANCELLED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='收货单';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `inventory_receipt_batch`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `inventory_receipt_batch` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `receipt_line_id` bigint NOT NULL,
  `product_id` bigint NOT NULL,
  `store_id` bigint NOT NULL,
  `supplier_batch_no` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `internal_receipt_batch_no` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `original_unit_id` bigint DEFAULT NULL,
  `original_unit_name` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `original_quantity` decimal(20,8) NOT NULL,
  `original_unit_cost` decimal(20,8) NOT NULL,
  `base_unit_id` bigint NOT NULL,
  `base_quantity` decimal(20,8) NOT NULL,
  `base_unit_cost` decimal(20,8) NOT NULL,
  `date_status` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'UNKNOWN',
  `production_date` date DEFAULT NULL,
  `expiry_date` date DEFAULT NULL,
  `inspection_reference` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `receiver_id` bigint DEFAULT NULL,
  `received_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `legacy_batch_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_inventory_receipt_batch_internal_no` (`internal_receipt_batch_no`),
  UNIQUE KEY `uk_inventory_receipt_batch_product` (`id`, `product_id`),
  UNIQUE KEY `uk_inventory_receipt_batch_store` (`id`, `store_id`),
  UNIQUE KEY `uk_inventory_receipt_batch_product_store` (`id`, `product_id`,`store_id`),
  UNIQUE KEY `uk_receipt_legacy_batch` (`legacy_batch_id`),
  KEY `idx_inventory_receipt_batch_supplier_no` (`supplier_batch_no`),
  KEY `idx_inventory_receipt_batch_expiry` (`expiry_date`),
  KEY `idx_inventory_receipt_batch_line` (`receipt_line_id`),
  KEY `fk_inventory_receipt_batch_line_product` (`receipt_line_id`, `product_id`),
  KEY `fk_inventory_receipt_batch_line_store` (`receipt_line_id`, `store_id`),
  KEY `fk_inventory_receipt_batch_original_unit` (`original_unit_id`, `product_id`),
  KEY `fk_inventory_receipt_batch_base_unit` (`base_unit_id`, `product_id`),
  KEY `fk_inventory_receipt_batch_receiver` (`receiver_id`),
  CONSTRAINT `fk_inventory_receipt_batch_base_unit` FOREIGN KEY (`base_unit_id`, `product_id`) REFERENCES `product_unit` (`id`, `product_id`),
  CONSTRAINT `fk_inventory_receipt_batch_line` FOREIGN KEY (`receipt_line_id`) REFERENCES `inventory_receipt_line` (`id`),
  CONSTRAINT `fk_inventory_receipt_batch_line_product` FOREIGN KEY (`receipt_line_id`, `product_id`) REFERENCES `inventory_receipt_line` (`id`, `product_id`),
  CONSTRAINT `fk_inventory_receipt_batch_line_store` FOREIGN KEY (`receipt_line_id`, `store_id`) REFERENCES `inventory_receipt_line` (`id`, `store_id`),
  CONSTRAINT `fk_inventory_receipt_batch_original_unit` FOREIGN KEY (`original_unit_id`, `product_id`) REFERENCES `product_unit` (`id`, `product_id`),
  CONSTRAINT `fk_inventory_receipt_batch_receiver` FOREIGN KEY (`receiver_id`) REFERENCES `staff` (`id`),
  CONSTRAINT `fk_receipt_legacy_batch` FOREIGN KEY (`legacy_batch_id`) REFERENCES `inventory_batch` (`id`),
  CONSTRAINT `chk_inventory_receipt_batch_cost` CHECK (((`original_unit_cost` > 0) and (`base_unit_cost` > 0))),
  CONSTRAINT `chk_inventory_receipt_batch_date_status` CHECK ((((`date_status` = _utf8mb4'UNKNOWN') and (`production_date` is null) and (`expiry_date` is null)) or ((`date_status` = _utf8mb4'PARTIAL') and (((`production_date` is null) and (`expiry_date` is not null)) or ((`production_date` is not null) and (`expiry_date` is null)))) or ((`date_status` = _utf8mb4'KNOWN') and (`production_date` is not null) and (`expiry_date` is not null) and (`production_date` <= `expiry_date`)))),
  CONSTRAINT `chk_inventory_receipt_batch_quantity` CHECK (((`original_quantity` > 0) and (`base_quantity` > 0)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='收货批次';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `inventory_receipt_line`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `inventory_receipt_line` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `receipt_id` bigint NOT NULL,
  `line_no` int NOT NULL,
  `purchase_order_item_id` bigint DEFAULT NULL,
  `product_id` bigint NOT NULL,
  `store_id` bigint NOT NULL,
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_inventory_receipt_line_no` (`receipt_id`, `line_no`),
  UNIQUE KEY `uk_inventory_receipt_line_product` (`id`, `product_id`),
  UNIQUE KEY `uk_inventory_receipt_line_store` (`id`, `store_id`),
  KEY `idx_inventory_receipt_line_product` (`product_id`),
  KEY `fk_inventory_receipt_line_receipt_store` (`receipt_id`, `store_id`),
  KEY `fk_inventory_receipt_line_purchase_item` (`purchase_order_item_id`, `product_id`),
  CONSTRAINT `fk_inventory_receipt_line_product` FOREIGN KEY (`product_id`) REFERENCES `product` (`id`),
  CONSTRAINT `fk_inventory_receipt_line_purchase_item` FOREIGN KEY (`purchase_order_item_id`, `product_id`) REFERENCES `purchase_order_item` (`id`, `product_id`),
  CONSTRAINT `fk_inventory_receipt_line_receipt` FOREIGN KEY (`receipt_id`) REFERENCES `inventory_receipt` (`id`),
  CONSTRAINT `fk_inventory_receipt_line_receipt_store` FOREIGN KEY (`receipt_id`, `store_id`) REFERENCES `inventory_receipt` (`id`, `store_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='收货单行';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `inventory_transaction`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `inventory_transaction` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `store_id` bigint NOT NULL COMMENT '门店ID',
  `product_id` bigint NOT NULL COMMENT '商品ID',
  `before_quantity` decimal(20,8) DEFAULT NULL,
  `batch_id` bigint DEFAULT NULL,
  `batch_before_qty` decimal(20,8) DEFAULT NULL,
  `batch_after_qty` decimal(20,8) DEFAULT NULL,
  `transaction_type` tinyint NOT NULL COMMENT '流水类型：1-入库，2-出库，3-调拨出，4-调拨入，5-调整，6-销售，7-退货',
  `quantity` decimal(20,8) NOT NULL,
  `cost_price` decimal(20,8) DEFAULT NULL,
  `unit` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '单位',
  `original_unit` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '业务发生时的原始单位（如：瓶、件）',
  `original_quantity` decimal(20,8) DEFAULT NULL,
  `after_quantity` decimal(20,8) DEFAULT NULL,
  `reference_type` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '关联类型（如：ORDER-订单，TRANSFER-调拨，ADJUSTMENT-调整）',
  `reference_id` bigint DEFAULT NULL COMMENT '关联ID',
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `created_by` bigint DEFAULT NULL COMMENT '操作人ID',
  `receipt_batch_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_store_product` (`store_id`, `product_id`),
  KEY `idx_transaction_type` (`transaction_type`),
  KEY `idx_reference` (`reference_type`, `reference_id`),
  KEY `idx_created_at` (`created_at`),
  KEY `fk_transaction_product` (`product_id`),
  KEY `idx_batch_id` (`batch_id`),
  KEY `fk_transaction_receipt` (`receipt_batch_id`),
  CONSTRAINT `fk_transaction_batch` FOREIGN KEY (`batch_id`) REFERENCES `inventory_batch` (`id`),
  CONSTRAINT `fk_transaction_product` FOREIGN KEY (`product_id`) REFERENCES `product` (`id`),
  CONSTRAINT `fk_transaction_receipt` FOREIGN KEY (`receipt_batch_id`) REFERENCES `inventory_receipt_batch` (`id`),
  CONSTRAINT `fk_transaction_store` FOREIGN KEY (`store_id`) REFERENCES `store` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='库存流水表（8位小数精度，记录原始单位和数量用于审计）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `inventory_transfer`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `inventory_transfer` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `transfer_no` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '调拨单号',
  `from_store_id` bigint NOT NULL COMMENT '调出门店ID',
  `to_store_id` bigint NOT NULL COMMENT '调入门店ID',
  `product_id` bigint NOT NULL COMMENT '商品ID',
  `quantity` decimal(20,8) NOT NULL,
  `unit` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '单位',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：1-待调出，2-已调出，3-已入库，4-已取消',
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人ID',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人ID',
  `request_key` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_transfer_no` (`transfer_no`),
  UNIQUE KEY `uk_inventory_transfer_request_key` (`request_key`),
  KEY `idx_from_store` (`from_store_id`),
  KEY `idx_to_store` (`to_store_id`),
  KEY `idx_product_id` (`product_id`),
  KEY `idx_status` (`status`),
  CONSTRAINT `fk_transfer_from_store` FOREIGN KEY (`from_store_id`) REFERENCES `store` (`id`),
  CONSTRAINT `fk_transfer_product` FOREIGN KEY (`product_id`) REFERENCES `product` (`id`),
  CONSTRAINT `fk_transfer_to_store` FOREIGN KEY (`to_store_id`) REFERENCES `store` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='库存调拨表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `inventory_transfer_detail`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `inventory_transfer_detail` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `transfer_id` bigint NOT NULL COMMENT '调拨单ID',
  `from_batch_id` bigint DEFAULT NULL,
  `to_batch_id` bigint DEFAULT NULL,
  `quantity` decimal(20,8) NOT NULL,
  `cost_price` decimal(20,8) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `from_receipt_batch_id` bigint DEFAULT NULL,
  `to_receipt_batch_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_transfer_id` (`transfer_id`),
  KEY `idx_from_batch` (`from_batch_id`),
  KEY `idx_to_batch` (`to_batch_id`),
  KEY `fk_transfer_from_receipt` (`from_receipt_batch_id`),
  KEY `fk_transfer_to_receipt` (`to_receipt_batch_id`),
  CONSTRAINT `fk_transfer_detail_from_batch` FOREIGN KEY (`from_batch_id`) REFERENCES `inventory_batch` (`id`),
  CONSTRAINT `fk_transfer_detail_to_batch` FOREIGN KEY (`to_batch_id`) REFERENCES `inventory_batch` (`id`),
  CONSTRAINT `fk_transfer_from_receipt` FOREIGN KEY (`from_receipt_batch_id`) REFERENCES `inventory_receipt_batch` (`id`),
  CONSTRAINT `fk_transfer_to_receipt` FOREIGN KEY (`to_receipt_batch_id`) REFERENCES `inventory_receipt_batch` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='库存调拨明细表（用于审计）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `inventory_warning`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `inventory_warning` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `store_id` bigint NOT NULL COMMENT '门店ID',
  `product_id` bigint NOT NULL COMMENT '商品ID',
  `warning_type` tinyint NOT NULL COMMENT '预警类型：1-库存不足，2-库存积压，3-即将过期，4-已过期',
  `current_quantity` decimal(10,4) NOT NULL COMMENT '当前库存数量',
  `threshold_quantity` decimal(10,4) DEFAULT NULL COMMENT '预警阈值数量',
  `expiry_date` date DEFAULT NULL COMMENT '过期日期（仅过期预警有效）',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：1-待处理，2-已处理，3-已忽略',
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_store_product` (`store_id`, `product_id`),
  KEY `idx_warning_type` (`warning_type`),
  KEY `idx_status` (`status`),
  KEY `fk_warning_product` (`product_id`),
  CONSTRAINT `fk_warning_product` FOREIGN KEY (`product_id`) REFERENCES `product` (`id`),
  CONSTRAINT `fk_warning_store` FOREIGN KEY (`store_id`) REFERENCES `store` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='库存预警表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `marketing_recharge_rule`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `marketing_recharge_rule` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '规则名称',
  `min_recharge_amount` decimal(10,2) NOT NULL COMMENT '门槛金额（元）',
  `target_vip_level` tinyint DEFAULT NULL COMMENT '升级到的VIP等级',
  `gift_duration_days` int DEFAULT '365' COMMENT '赠送VIP时长(天)，默认1年',
  `gift_special_fund` decimal(10,2) DEFAULT '0.00' COMMENT '赠送专用金（元）',
  `gift_template_id` bigint DEFAULT NULL COMMENT '赠送优惠券模板ID',
  `gift_template_count` int DEFAULT '1' COMMENT '赠送数量',
  `status` tinyint DEFAULT '1' COMMENT '状态：1-启用，0-停用',
  `sort_order` int DEFAULT '0' COMMENT '优先级（数字越小优先级越高）',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_status_sort` (`status`, `sort_order`),
  KEY `idx_min_amount` (`min_recharge_amount`),
  KEY `idx_gift_template` (`gift_template_id`),
  CONSTRAINT `fk_recharge_rule_template` FOREIGN KEY (`gift_template_id`) REFERENCES `coupon_template` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='充值一次性奖励规则表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `marketing_vip_rights`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `marketing_vip_rights` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `vip_level` tinyint NOT NULL COMMENT '关联VIP等级',
  `benefit_type` tinyint NOT NULL COMMENT '权益类型：1-每月送券，2-每月送专用金',
  `benefit_value` decimal(10,2) DEFAULT '0.00' COMMENT '数值（如金额，元）',
  `template_id` bigint DEFAULT NULL COMMENT '赠送的优惠券模板ID',
  `template_count` int DEFAULT '1' COMMENT '赠送数量',
  `status` tinyint DEFAULT '1' COMMENT '状态：1-启用，0-停用',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_vip_level` (`vip_level`),
  KEY `idx_status_type` (`status`, `benefit_type`),
  KEY `idx_template_id` (`template_id`),
  CONSTRAINT `fk_vip_rights_template` FOREIGN KEY (`template_id`) REFERENCES `coupon_template` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='VIP每月周期权益表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `one_time_qr`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `one_time_qr` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `qr_token` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `staff_id` bigint NOT NULL,
  `store_id` bigint NOT NULL,
  `order_temp_token` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `scene_data` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` tinyint DEFAULT '0' COMMENT '0=CREATED,1=BOUND,2=USED,3=EXPIRED',
  `customer_id` bigint DEFAULT NULL,
  `openid` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `bind_at` datetime DEFAULT NULL,
  `expire_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_qr_token` (`qr_token`),
  KEY `idx_staff` (`staff_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='一次性二维码记录表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `order`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `order` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `order_no` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '订单号',
  `store_id` bigint NOT NULL COMMENT '门店ID',
  `customer_id` bigint DEFAULT NULL COMMENT '客户ID（可为空，支持散客）',
  `order_type` tinyint NOT NULL COMMENT '订单类型：1-线下POS，2-小程序自提，3-小程序配送',
  `order_status` tinyint NOT NULL DEFAULT '1' COMMENT '订单状态：1-待支付(WAIT_PAY), 2-已支付(PAID), 3-待自提(WAIT_PICKUP), 4-待发货(WAIT_SHIP), 5-已发货(SHIPPED), 6-已完成(FINISHED), 7-已取消(CANCELLED), 8-已退款(REFUNDED), 9-部分退款(PART_REFUNDED)',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：1-已删 0-未删',
  `total_amount` decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '订单总金额（元）',
  `discount_amount` decimal(10,2) DEFAULT '0.00' COMMENT '优惠金额（元）',
  `coupon_amount` decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '优惠券抵扣总额（元）',
  `pay_amount` decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '实付金额（元）',
  `payment_method` tinyint DEFAULT NULL COMMENT '主支付方式：1-现金,2-微信,3-支付宝,4-余额,5-优惠券,6-赊销,10-混合支付',
  `staff_id` bigint DEFAULT NULL COMMENT '销售员工ID（线下POS必填）',
  `receiver_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '收货人姓名',
  `delivery_address` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '配送地址（配送订单必填）',
  `delivery_phone` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '配送联系电话',
  `delivery_status` tinyint DEFAULT NULL COMMENT '配送状态：1-待审核，2-已审核，3-配送中，4-已送达，5-已拒收（仅配送订单有效）',
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `pickup_code` varchar(10) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '自提核销码（仅自提订单生成）',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  UNIQUE KEY `uk_pickup_code` (`pickup_code`),
  KEY `idx_store_id` (`store_id`),
  KEY `idx_customer_id` (`customer_id`),
  KEY `idx_order_type` (`order_type`),
  KEY `idx_order_status` (`order_status`),
  KEY `idx_staff_id` (`staff_id`),
  KEY `idx_created_at` (`created_at`),
  KEY `idx_order_deleted` (`deleted`),
  CONSTRAINT `fk_order_customer` FOREIGN KEY (`customer_id`) REFERENCES `customer` (`id`),
  CONSTRAINT `fk_order_staff` FOREIGN KEY (`staff_id`) REFERENCES `staff` (`id`),
  CONSTRAINT `fk_order_store` FOREIGN KEY (`store_id`) REFERENCES `store` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `order_delivery`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `order_delivery` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `order_id` bigint NOT NULL COMMENT '订单ID',
  `delivery_no` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '配送单号',
  `logistics_company` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '物流公司名称（快递发货时必填）',
  `tracking_no` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '快递单号（快递发货时必填）',
  `delivery_status` tinyint NOT NULL DEFAULT '1' COMMENT '配送状态：1-待审核，2-已审核，3-配送中，4-已送达，5-已拒收',
  `delivery_address` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '配送地址',
  `delivery_phone` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '配送联系电话',
  `delivery_type` tinyint DEFAULT NULL COMMENT '配送方式：2-同城配送，3-快递配送',
  `delivery_staff_id` bigint DEFAULT NULL COMMENT '配送员工ID',
  `audit_status` tinyint DEFAULT '1' COMMENT '审核状态：1-待审核，2-已通过，3-已拒绝',
  `audit_remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '审核备注',
  `audit_at` datetime DEFAULT NULL COMMENT '审核时间',
  `audit_by` bigint DEFAULT NULL COMMENT '审核人ID',
  `delivery_at` datetime DEFAULT NULL COMMENT '配送时间',
  `received_at` datetime DEFAULT NULL COMMENT '签收时间',
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `assignment_version` bigint NOT NULL DEFAULT '0' COMMENT '当前指派版本，明确改派时递增',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_delivery_no` (`delivery_no`),
  UNIQUE KEY `uk_tracking_no` (`tracking_no`),
  KEY `idx_order_id` (`order_id`),
  KEY `idx_delivery_status` (`delivery_status`),
  KEY `idx_audit_status` (`audit_status`),
  KEY `fk_delivery_staff` (`delivery_staff_id`),
  KEY `idx_delivery_type` (`delivery_type`),
  CONSTRAINT `fk_delivery_order` FOREIGN KEY (`order_id`) REFERENCES `order` (`id`),
  CONSTRAINT `fk_delivery_staff` FOREIGN KEY (`delivery_staff_id`) REFERENCES `staff` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='配送表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `order_item`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `order_item` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `order_id` bigint NOT NULL COMMENT '订单ID',
  `product_id` bigint DEFAULT NULL COMMENT '商品ID（单品订单）',
  `package_id` bigint DEFAULT NULL COMMENT '套餐ID（套餐订单）',
  `item_type` tinyint NOT NULL COMMENT '明细类型：1-商品，2-套餐',
  `product_name` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '商品/套餐名称（冗余字段，便于查询）',
  `quantity` decimal(10,4) NOT NULL COMMENT '数量',
  `unit` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '单位',
  `unit_price` decimal(24,12) NOT NULL COMMENT '成交或分摊单价，金额以total_price为准',
  `cost_price_snapshot` decimal(10,2) DEFAULT NULL COMMENT '下单时成本快照',
  `total_price` decimal(10,2) NOT NULL COMMENT '小计金额（元）',
  `commission_amount` decimal(10,2) DEFAULT '0.00' COMMENT '提成金额（元）',
  `allow_special_fund` tinyint NOT NULL DEFAULT '0' COMMENT '是否允许使用专用金：0-不允许，1-允许（来源于商品快照）',
  `sort_order` int DEFAULT '0' COMMENT '排序顺序',
  `base_unit_id` bigint DEFAULT NULL,
  `base_quantity` decimal(20,8) DEFAULT NULL,
  `unit_cost` decimal(20,8) DEFAULT NULL,
  `package_group_no` int DEFAULT NULL COMMENT '同一订单内套餐销售组号',
  `package_name` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '销售时套餐名称快照',
  `package_quantity` decimal(10,4) DEFAULT NULL COMMENT '该销售组实际套数快照',
  `package_unit_price` decimal(10,2) DEFAULT NULL COMMENT '每套成交价快照',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_item_id_product` (`id`, `product_id`),
  KEY `idx_order_id` (`order_id`),
  KEY `idx_product_id` (`product_id`),
  KEY `idx_package_id` (`package_id`),
  KEY `idx_order_item_base_unit` (`base_unit_id`),
  KEY `fk_order_item_base_unit` (`base_unit_id`, `product_id`),
  CONSTRAINT `fk_order_item_base_unit` FOREIGN KEY (`base_unit_id`, `product_id`) REFERENCES `product_unit` (`id`, `product_id`),
  CONSTRAINT `fk_order_item_order` FOREIGN KEY (`order_id`) REFERENCES `order` (`id`),
  CONSTRAINT `fk_order_item_package` FOREIGN KEY (`package_id`) REFERENCES `product_package` (`id`),
  CONSTRAINT `fk_order_item_product` FOREIGN KEY (`product_id`) REFERENCES `product` (`id`),
  CONSTRAINT `chk_order_item_base_quantity` CHECK (((`base_quantity` is null) or (`base_quantity` > 0))),
  CONSTRAINT `chk_order_item_unit_cost` CHECK (((`unit_cost` is null) or (`unit_cost` > 0))),
  CONSTRAINT `chk_package_sale_snapshot` CHECK (((`package_group_no` is null) or ((`package_group_no` > 0) and (`package_id` is not null) and (`package_name` is not null) and (`package_quantity` is not null) and (`package_unit_price` is not null) and (`package_quantity` > 0) and (`package_quantity` = floor(`package_quantity`)) and (`package_unit_price` > 0))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单明细表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `order_item_batch_allocation`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `order_item_batch_allocation` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `order_item_id` bigint NOT NULL,
  `product_id` bigint NOT NULL,
  `store_id` bigint NOT NULL,
  `batch_id` bigint DEFAULT NULL,
  `location_id` bigint DEFAULT NULL,
  `pickup_request_id` bigint DEFAULT NULL,
  `allocated_base_quantity` decimal(20,8) NOT NULL,
  `batch_unit_cost` decimal(20,8) DEFAULT NULL,
  `allocated_cost` decimal(20,8) DEFAULT NULL,
  `evidence_level` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL,
  `allocation_rule` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `allocation_rule_version` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `source_movement_id` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `created_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_order_item_batch_allocation_item` (`order_item_id`),
  KEY `idx_order_item_batch_allocation_batch` (`batch_id`),
  KEY `idx_order_item_batch_allocation_evidence` (`evidence_level`),
  KEY `fk_order_item_batch_allocation_item_product` (`order_item_id`, `product_id`),
  KEY `fk_order_item_batch_allocation_batch` (`batch_id`, `product_id`,`store_id`),
  KEY `fk_order_item_batch_allocation_location` (`location_id`, `store_id`),
  KEY `fk_order_item_batch_allocation_movement` (`source_movement_id`, `product_id`,`store_id`),
  KEY `idx_order_item_batch_allocation_pickup` (`pickup_request_id`),
  CONSTRAINT `fk_order_item_batch_allocation_batch` FOREIGN KEY (`batch_id`, `product_id`, `store_id`) REFERENCES `inventory_receipt_batch` (`id`, `product_id`, `store_id`),
  CONSTRAINT `fk_order_item_batch_allocation_item` FOREIGN KEY (`order_item_id`) REFERENCES `order_item` (`id`),
  CONSTRAINT `fk_order_item_batch_allocation_item_product` FOREIGN KEY (`order_item_id`, `product_id`) REFERENCES `order_item` (`id`, `product_id`),
  CONSTRAINT `fk_order_item_batch_allocation_location` FOREIGN KEY (`location_id`, `store_id`) REFERENCES `inventory_location` (`id`, `store_id`),
  CONSTRAINT `fk_order_item_batch_allocation_movement` FOREIGN KEY (`source_movement_id`, `product_id`, `store_id`) REFERENCES `inventory_movement` (`id`, `product_id`, `store_id`),
  CONSTRAINT `fk_order_item_batch_allocation_pickup` FOREIGN KEY (`pickup_request_id`) REFERENCES `order_pickup_request` (`id`),
  CONSTRAINT `chk_order_item_batch_allocation_cost` CHECK ((((`batch_unit_cost` is null) or (`batch_unit_cost` > 0)) and ((`allocated_cost` is null) or (`allocated_cost` > 0)))),
  CONSTRAINT `chk_order_item_batch_allocation_evidence` CHECK ((`evidence_level` in (_utf8mb4'SCANNED',_utf8mb4'FULFILLMENT_CONFIRMED',_utf8mb4'RULE_ALLOCATED',_utf8mb4'UNKNOWN'))),
  CONSTRAINT `chk_order_item_batch_allocation_quantity` CHECK ((`allocated_base_quantity` > 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单行批次归属';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `order_item_price_audit`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `order_item_price_audit` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `order_item_id` bigint NOT NULL,
  `product_id` bigint NOT NULL,
  `sale_unit_id` bigint DEFAULT NULL,
  `suggested_unit_price` decimal(24,12) NOT NULL,
  `final_unit_price` decimal(24,12) NOT NULL,
  `price_difference` decimal(24,12) NOT NULL,
  `price_rule_version` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `change_reason` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `operator_id` bigint DEFAULT NULL,
  `confirmer_id` bigint DEFAULT NULL,
  `decided_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `quote_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_order_item_price_audit_item` (`order_item_id`),
  KEY `idx_order_item_price_audit_product_time` (`product_id`, `decided_at`),
  KEY `fk_order_item_price_audit_item` (`order_item_id`, `product_id`),
  KEY `fk_order_item_price_audit_unit` (`sale_unit_id`, `product_id`),
  KEY `fk_order_item_price_audit_operator` (`operator_id`),
  KEY `fk_order_item_price_audit_confirmer` (`confirmer_id`),
  CONSTRAINT `fk_order_item_price_audit_confirmer` FOREIGN KEY (`confirmer_id`) REFERENCES `staff` (`id`),
  CONSTRAINT `fk_order_item_price_audit_item` FOREIGN KEY (`order_item_id`, `product_id`) REFERENCES `order_item` (`id`, `product_id`),
  CONSTRAINT `fk_order_item_price_audit_operator` FOREIGN KEY (`operator_id`) REFERENCES `staff` (`id`),
  CONSTRAINT `fk_order_item_price_audit_product` FOREIGN KEY (`product_id`) REFERENCES `product` (`id`),
  CONSTRAINT `fk_order_item_price_audit_unit` FOREIGN KEY (`sale_unit_id`, `product_id`) REFERENCES `product_unit` (`id`, `product_id`),
  CONSTRAINT `chk_order_item_price_audit_prices` CHECK (((`suggested_unit_price` >= 0) and (`final_unit_price` >= 0)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单行价格审计（追加写）';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = utf8mb4 */ ;
/*!50003 SET character_set_results = utf8mb4 */ ;
/*!50003 SET collation_connection  = utf8mb4_0900_ai_ci */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION' */ ;
DELIMITER ;;
/*!50003 CREATE*/ /*!50003 TRIGGER `trg_order_item_price_audit_no_update` BEFORE UPDATE ON `order_item_price_audit` FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'order_item_price_audit is append-only' */;;
DELIMITER ;
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = utf8mb4 */ ;
/*!50003 SET character_set_results = utf8mb4 */ ;
/*!50003 SET collation_connection  = utf8mb4_0900_ai_ci */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION' */ ;
DELIMITER ;;
/*!50003 CREATE*/ /*!50003 TRIGGER `trg_order_item_price_audit_no_delete` BEFORE DELETE ON `order_item_price_audit` FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'order_item_price_audit is append-only' */;;
DELIMITER ;
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;

--
-- Table structure for table `order_payment`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `order_payment` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `order_id` bigint NOT NULL COMMENT '订单ID',
  `payment_type` tinyint NOT NULL COMMENT '支付类型：1-现金,2-微信,3-支付宝,4-本金余额,5-红包/优惠券,6-赊销,8-专用金余额,10-混合支付',
  `amount` decimal(10,2) NOT NULL COMMENT '该方式支付金额（元）',
  `coupon_id` bigint DEFAULT NULL COMMENT '红包/优惠券ID（当payment_type=5时填充）',
  `merchant_no` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '支付平台商户号（接入真实平台时核验填充）',
  `outer_trans_no` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '外部支付流水号（微信/支付宝）',
  `callback_time` datetime DEFAULT NULL COMMENT '外部支付回调时间（微信/支付宝）',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `refunded_amount` decimal(10,2) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_order_id` (`order_id`),
  KEY `idx_coupon_id` (`coupon_id`),
  UNIQUE KEY `uk_order_payment_outer` (`payment_type`,`merchant_no`,`outer_trans_no`),
  CONSTRAINT `fk_payment_coupon` FOREIGN KEY (`coupon_id`) REFERENCES `customer_coupon` (`id`),
  CONSTRAINT `fk_payment_order` FOREIGN KEY (`order_id`) REFERENCES `order` (`id`),
  CONSTRAINT `chk_order_payment_refund` CHECK (((`refunded_amount` is null) or ((`refunded_amount` >= 0) and (`refunded_amount` <= `amount`)))),
  CONSTRAINT `chk_payment_no_points` CHECK ((`payment_type` <> 7))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单支付明细表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `order_payment_intent`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `order_payment_intent` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `intent_no` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '支付意图号',
  `request_key` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '支付发起幂等键：同键重试重放同一意图',
  `order_id` bigint NOT NULL COMMENT '订单ID',
  `customer_id` bigint NOT NULL COMMENT '客户ID',
  `use_balance` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否使用本金余额',
  `use_special_fund` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否使用专用金',
  `coupon_id` bigint DEFAULT NULL COMMENT '优惠券ID',
  `external_payment_type` tinyint NOT NULL COMMENT '外部支付方式：2-微信，3-支付宝',
  `external_amount` decimal(10,2) NOT NULL COMMENT '外部支付金额',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：1-待确认，2-已确认，3-已失效',
  `external_transaction_no` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '外部支付流水号',
  `expire_at` datetime NOT NULL COMMENT '过期时间',
  `confirmed_at` datetime DEFAULT NULL COMMENT '确认时间',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_intent_no` (`intent_no`),
  UNIQUE KEY `uk_intent_request_key` (`request_key`),
  KEY `idx_order_status` (`order_id`, `status`),
  KEY `idx_customer_status` (`customer_id`, `status`),
  KEY `fk_order_payment_intent_coupon` (`coupon_id`),
  CONSTRAINT `fk_order_payment_intent_coupon` FOREIGN KEY (`coupon_id`) REFERENCES `customer_coupon` (`id`),
  CONSTRAINT `fk_order_payment_intent_order` FOREIGN KEY (`order_id`) REFERENCES `order` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单外部支付意图表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `order_pickup_request`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `order_pickup_request` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `order_id` bigint NOT NULL,
  `order_item_id` bigint NOT NULL,
  `store_id` bigint NOT NULL,
  `product_id` bigint NOT NULL,
  `requested_base_quantity` decimal(20,8) NOT NULL,
  `source_location_id` bigint DEFAULT NULL,
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `created_by` bigint DEFAULT NULL,
  `fulfilled_at` datetime DEFAULT NULL,
  `fulfilled_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_order_pickup_request_order` (`order_id`),
  KEY `idx_order_pickup_request_item` (`order_item_id`),
  KEY `idx_order_pickup_request_status` (`store_id`, `status`),
  KEY `fk_order_pickup_request_item_product` (`order_item_id`, `product_id`),
  KEY `fk_order_pickup_request_product` (`product_id`),
  KEY `fk_order_pickup_request_location` (`source_location_id`, `store_id`),
  KEY `fk_order_pickup_request_creator` (`created_by`),
  KEY `fk_order_pickup_request_fulfiller` (`fulfilled_by`),
  CONSTRAINT `fk_order_pickup_request_creator` FOREIGN KEY (`created_by`) REFERENCES `staff` (`id`),
  CONSTRAINT `fk_order_pickup_request_fulfiller` FOREIGN KEY (`fulfilled_by`) REFERENCES `staff` (`id`),
  CONSTRAINT `fk_order_pickup_request_item` FOREIGN KEY (`order_item_id`) REFERENCES `order_item` (`id`),
  CONSTRAINT `fk_order_pickup_request_item_product` FOREIGN KEY (`order_item_id`, `product_id`) REFERENCES `order_item` (`id`, `product_id`),
  CONSTRAINT `fk_order_pickup_request_location` FOREIGN KEY (`source_location_id`, `store_id`) REFERENCES `inventory_location` (`id`, `store_id`),
  CONSTRAINT `fk_order_pickup_request_product` FOREIGN KEY (`product_id`) REFERENCES `product` (`id`),
  CONSTRAINT `chk_order_pickup_request_quantity` CHECK ((`requested_base_quantity` > 0)),
  CONSTRAINT `chk_order_pickup_request_status` CHECK ((`status` in (_utf8mb4'PENDING',_utf8mb4'FULFILLED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='库房取货请求';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `performance_event_record`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `performance_event_record` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `event_key` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  `event_type` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `biz_id` bigint DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_event_key` (`event_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='绩效模块事件幂等记录';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `points_exchange_order`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `points_exchange_order` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `exchange_no` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '兑换单号',
  `customer_id` bigint NOT NULL COMMENT '客户ID',
  `points_goods_id` bigint NOT NULL COMMENT '积分礼品ID',
  `points` int NOT NULL COMMENT '单件所需积分',
  `quantity` int NOT NULL DEFAULT '1' COMMENT '兑换数量',
  `receiver_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '收货人',
  `receiver_phone` varchar(30) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '联系电话',
  `receiver_address` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '收货地址',
  `status` tinyint NOT NULL DEFAULT '10' COMMENT '状态：10-待审核，20-备货中，30-待自提，50-已完成，90-已拒绝',
  `logistics_company` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '物流公司',
  `tracking_no` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '物流单号',
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `pickup_code` varchar(10) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '自提核销码（仅自提订单生成）',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_points_exchange_no` (`exchange_no`),
  UNIQUE KEY `uk_pickup_code` (`pickup_code`),
  KEY `idx_points_order_customer` (`customer_id`),
  KEY `idx_points_order_goods` (`points_goods_id`),
  KEY `idx_points_order_status` (`status`),
  KEY `idx_points_order_created_at` (`created_at`),
  CONSTRAINT `fk_points_order_customer` FOREIGN KEY (`customer_id`) REFERENCES `customer` (`id`),
  CONSTRAINT `fk_points_order_goods` FOREIGN KEY (`points_goods_id`) REFERENCES `points_goods` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='积分兑换订单表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `points_goods`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `points_goods` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `code` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '礼品编码',
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '礼品名称',
  `points` int NOT NULL COMMENT '所需积分',
  `stock` int NOT NULL DEFAULT '0' COMMENT '库存',
  `image_url` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '图片地址',
  `description` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '描述',
  `status` tinyint NOT NULL DEFAULT '2' COMMENT '状态：1-上架，2-下架',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '排序值',
  `created_by` bigint DEFAULT NULL COMMENT '创建人ID',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人ID',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_points_goods_code` (`code`),
  KEY `idx_points_goods_status_sort` (`status`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='积分礼品表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pos_sale_submission`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pos_sale_submission` (
  `request_key` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL,
  `store_id` bigint NOT NULL,
  `staff_id` bigint NOT NULL,
  `request_hash` char(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL,
  `order_id` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`request_key`),
  KEY `idx_pos_sale_submission_actor` (`store_id`, `staff_id`,`created_at`),
  CONSTRAINT `chk_pos_sale_submission_result` CHECK ((((`status` = _utf8mb4'PENDING') and (`order_id` is null)) or ((`status` = _utf8mb4'CANCELLED') and (`order_id` is null)) or ((`status` = _utf8mb4'COMPLETED') and (`order_id` is not null) and (`request_hash` is not null))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `product`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `product` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `code` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '商品编码',
  `barcode` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '条形码（EAN-13/Code128）',
  `name` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '商品名称',
  `category_id` bigint DEFAULT NULL COMMENT '商品分类ID（外键关联product_category）',
  `specification` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '规格（如：500g/袋）',
  `dosage_form` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '剂型（如：颗粒剂、水剂、粉剂）',
  `manufacturer` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '生产厂家',
  `registration_no` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '农药登记证号/肥料登记证号',
  `main_unit` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主单位（如：袋、瓶、箱）',
  `aux_unit` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '辅单位（如：箱、件）',
  `main_to_aux_ratio` decimal(10,4) DEFAULT NULL COMMENT '主辅单位换算比例（1主单位=？辅单位，如：1箱=20袋，则比例为20）',
  `suggested_retail_price` decimal(10,2) DEFAULT NULL COMMENT '建议零售价（元）',
  `vip_price` decimal(10,2) DEFAULT NULL COMMENT 'VIP价格（元）',
  `min_price` decimal(10,2) DEFAULT NULL COMMENT '最低限价（元）',
  `avg_cost_price` decimal(10,2) DEFAULT '0.00' COMMENT '移动加权平均成本（元，由采购入库触发计算）',
  `image_url` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '主图URL（列表/详情共用）',
  `detail_text` text COLLATE utf8mb4_unicode_ci COMMENT '详情文字（纯文字详情）',
  `is_online` tinyint NOT NULL DEFAULT '0' COMMENT '是否上架：1-上架，0-下架',
  `base_shipping_fee` decimal(10,2) DEFAULT '0.00' COMMENT '基础运费（元，默认0）',
  `allow_special_fund` tinyint DEFAULT '0' COMMENT '允许使用专用金：1-是 0-否',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：1-正常，2-停用',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：1-已删 0-未删',
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `storage_conditions` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '存储条件（如：阴凉干燥处、避光保存）',
  `shelf_life_days` int DEFAULT NULL COMMENT '保质期（天）',
  `usage_method` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '使用方法/稀释比例',
  `min_stock` decimal(12,2) DEFAULT NULL COMMENT '最低库存预警值（低于此值触发预警）',
  `max_stock` decimal(12,2) DEFAULT NULL COMMENT '最高库存预警值（高于此值触发积压预警）',
  `reorder_quantity` decimal(12,2) DEFAULT NULL COMMENT '建议补货数量',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人ID',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_product_code` (`code`),
  UNIQUE KEY `uk_product_name_spec_manu` (`name`, `specification`,`manufacturer`),
  KEY `idx_product_name` (`name`),
  KEY `idx_category_id` (`category_id`),
  KEY `idx_product_status` (`status`),
  KEY `idx_is_online` (`is_online`),
  KEY `idx_product_barcode` (`barcode`),
  KEY `idx_product_deleted` (`deleted`),
  CONSTRAINT `fk_product_category` FOREIGN KEY (`category_id`) REFERENCES `product_category` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `product_category`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `product_category` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `code` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '分类编码',
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '分类名称',
  `parent_id` bigint DEFAULT NULL COMMENT '父分类ID（为空表示顶级分类）',
  `level` int NOT NULL DEFAULT '1' COMMENT '分类层级（1-一级，2-二级，以此类推）',
  `sort_order` int DEFAULT '0' COMMENT '排序顺序',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：1-正常，2-停用',
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人ID',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_category_code` (`code`),
  KEY `idx_parent_id` (`parent_id`),
  KEY `idx_level` (`level`),
  KEY `idx_status` (`status`),
  CONSTRAINT `fk_category_parent` FOREIGN KEY (`parent_id`) REFERENCES `product_category` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品分类表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `product_package`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `product_package` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `code` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '套餐编码（系统生成、唯一）',
  `name` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '套餐名称',
  `suggested_retail_price` decimal(10,2) NOT NULL COMMENT '零售价（必填）',
  `vip_price` decimal(10,2) DEFAULT NULL COMMENT 'VIP价（可空，null=不启用VIP价）',
  `min_price` decimal(10,2) DEFAULT NULL COMMENT '最低售价',
  `allow_special_fund` tinyint DEFAULT '0' COMMENT '允许使用专用金：1-是 0-否',
  `status` int NOT NULL DEFAULT '0' COMMENT '状态：1=上架，0=下架，-1=删除',
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人ID',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_package_code` (`code`),
  KEY `idx_package_name` (`name`),
  KEY `idx_package_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='套餐主表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `product_package_item`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `product_package_item` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `package_id` bigint NOT NULL COMMENT '套餐ID',
  `product_id` bigint NOT NULL COMMENT '商品ID',
  `quantity` decimal(10,4) NOT NULL COMMENT '数量（必须 > 0）',
  `unit` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '单位',
  `sort_order` int DEFAULT '0' COMMENT '排序顺序',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_package_id` (`package_id`),
  KEY `idx_product_id` (`product_id`),
  CONSTRAINT `fk_package_item_package` FOREIGN KEY (`package_id`) REFERENCES `product_package` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_package_item_product` FOREIGN KEY (`product_id`) REFERENCES `product` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='套餐明细表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `product_price`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `product_price` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `product_id` bigint NOT NULL COMMENT '商品ID',
  `store_id` bigint DEFAULT NULL COMMENT '门店ID（为空表示全局价格）',
  `customer_type` tinyint DEFAULT NULL COMMENT '客户类型：1-普通客户，2-VIP客户（为空表示通用价格）',
  `vip_level` tinyint DEFAULT NULL COMMENT 'VIP等级：1-普通VIP，2-银卡VIP，3-金卡VIP，4-钻石VIP（仅VIP客户有效）',
  `price_type` tinyint NOT NULL COMMENT '价格类型：1-建议零售价，2-VIP价，3-最低限价，4-进价',
  `price` decimal(10,2) NOT NULL COMMENT '价格（元）',
  `effective_date` date DEFAULT NULL COMMENT '生效日期',
  `expiry_date` date DEFAULT NULL COMMENT '失效日期',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：1-有效，2-无效',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人ID',
  `sale_unit` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `channel` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `rule_version` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_product_id` (`product_id`),
  KEY `idx_store_id` (`store_id`),
  KEY `idx_customer_type` (`customer_type`),
  KEY `idx_price_type` (`price_type`),
  KEY `idx_status` (`status`),
  CONSTRAINT `fk_price_product` FOREIGN KEY (`product_id`) REFERENCES `product` (`id`),
  CONSTRAINT `fk_price_store` FOREIGN KEY (`store_id`) REFERENCES `store` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品价格表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `product_unit`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `product_unit` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `product_id` bigint NOT NULL,
  `unit_name` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `base_quantity_per_unit` decimal(20,8) NOT NULL,
  `is_base_unit` tinyint NOT NULL DEFAULT '0',
  `allow_purchase` tinyint NOT NULL DEFAULT '1',
  `allow_sale` tinyint NOT NULL DEFAULT '1',
  `allow_inventory` tinyint NOT NULL DEFAULT '1',
  `allow_transfer` tinyint NOT NULL DEFAULT '1',
  `barcode` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` tinyint NOT NULL DEFAULT '1',
  `enabled_base_unit_key` tinyint GENERATED ALWAYS AS ((case when ((`status` = 1) and (`is_base_unit` = 1)) then 1 else NULL end)) VIRTUAL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_product_unit_id_product` (`id`, `product_id`),
  UNIQUE KEY `uk_product_unit_name` (`product_id`, `unit_name`),
  UNIQUE KEY `uk_product_enabled_base_unit` (`product_id`, `enabled_base_unit_key`),
  KEY `idx_product_unit_status` (`product_id`, `status`),
  KEY `idx_product_unit_barcode` (`barcode`),
  CONSTRAINT `fk_product_unit_product` FOREIGN KEY (`product_id`) REFERENCES `product` (`id`),
  CONSTRAINT `chk_product_unit_base_flag` CHECK ((`is_base_unit` in (0,1))),
  CONSTRAINT `chk_product_unit_base_quantity` CHECK ((`base_quantity_per_unit` > 0)),
  CONSTRAINT `chk_product_unit_base_ratio` CHECK (((`is_base_unit` = 0) or (`base_quantity_per_unit` = 1))),
  CONSTRAINT `chk_product_unit_inventory_flag` CHECK ((`allow_inventory` in (0,1))),
  CONSTRAINT `chk_product_unit_purchase_flag` CHECK ((`allow_purchase` in (0,1))),
  CONSTRAINT `chk_product_unit_sale_flag` CHECK ((`allow_sale` in (0,1))),
  CONSTRAINT `chk_product_unit_transfer_flag` CHECK ((`allow_transfer` in (0,1)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='SKU 业务单位';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `product_unit_conversion`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `product_unit_conversion` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `product_id` bigint NOT NULL COMMENT '商品ID',
  `from_unit` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '源单位',
  `to_unit` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '目标单位',
  `conversion_ratio` decimal(10,4) NOT NULL COMMENT '换算比例（1源单位=？目标单位）',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_product_unit` (`product_id`, `from_unit`,`to_unit`),
  KEY `idx_product_id` (`product_id`),
  CONSTRAINT `fk_unit_product` FOREIGN KEY (`product_id`) REFERENCES `product` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品单位换算表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `purchase_order`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `purchase_order` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `purchase_no` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '采购单号',
  `supplier_id` bigint NOT NULL COMMENT '供应商ID',
  `store_id` bigint NOT NULL COMMENT '入库门店ID',
  `total_amount` decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '总金额（元）',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：1-待提交，2-待入库，3-已入库，4-已取消',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：1-已删 0-未删',
  `purchase_date` date DEFAULT NULL COMMENT '采购日期',
  `expected_arrival_date` date DEFAULT NULL COMMENT '预计到货日期',
  `arrival_date` date DEFAULT NULL COMMENT '实际到货日期',
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人ID',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_purchase_no` (`purchase_no`),
  KEY `idx_purchase_supplier` (`supplier_id`),
  KEY `idx_purchase_store` (`store_id`),
  KEY `idx_purchase_status` (`status`),
  KEY `idx_purchase_date` (`purchase_date`),
  KEY `idx_purchase_order_deleted` (`deleted`),
  CONSTRAINT `fk_purchase_store` FOREIGN KEY (`store_id`) REFERENCES `store` (`id`),
  CONSTRAINT `fk_purchase_supplier` FOREIGN KEY (`supplier_id`) REFERENCES `supplier` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='采购订单表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `purchase_order_item`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `purchase_order_item` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `purchase_order_id` bigint NOT NULL COMMENT '采购订单ID',
  `product_id` bigint NOT NULL COMMENT '商品ID',
  `quantity` decimal(10,4) NOT NULL COMMENT '数量',
  `unit` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '单位',
  `buy_price` decimal(10,2) NOT NULL COMMENT '本次真实进价（元）',
  `total_price` decimal(10,2) NOT NULL COMMENT '小计金额（元）',
  `sort_order` int DEFAULT '0' COMMENT '排序顺序',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_purchase_order_item_id_product` (`id`, `product_id`),
  KEY `idx_poi_order` (`purchase_order_id`),
  KEY `idx_poi_product` (`product_id`),
  CONSTRAINT `fk_poi_order` FOREIGN KEY (`purchase_order_id`) REFERENCES `purchase_order` (`id`),
  CONSTRAINT `fk_poi_product` FOREIGN KEY (`product_id`) REFERENCES `product` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='采购订单明细表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `staff`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `staff` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `code` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '员工编码',
  `name` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '员工姓名',
  `phone` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '手机号',
  `password` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '密码（BCrypt加密）',
  `id_card` varchar(18) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '身份证号',
  `store_id` bigint NOT NULL COMMENT '所属门店ID',
  `role` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '角色：ADMIN-管理员，MANAGER-店长，STAFF-员工',
  `position` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '岗位（如：店长、收银员、仓管、农技顾问）',
  `shift_type` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '班次类型（如：早班、晚班、全天）',
  `salary_base` decimal(10,2) DEFAULT NULL COMMENT '基本底薪（元）',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：1-在职，2-离职',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：1-已删，0-未删',
  `hire_date` date DEFAULT NULL COMMENT '入职日期',
  `contract_end_date` date DEFAULT NULL COMMENT '合同到期日',
  `emergency_contact` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '紧急联系人',
  `emergency_phone` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '紧急联系人电话',
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人ID',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_staff_code` (`code`),
  UNIQUE KEY `uk_staff_phone` (`phone`),
  KEY `idx_staff_store` (`store_id`),
  KEY `idx_staff_status` (`status`),
  KEY `idx_staff_role` (`role`),
  KEY `idx_staff_deleted` (`deleted`),
  CONSTRAINT `fk_staff_store` FOREIGN KEY (`store_id`) REFERENCES `store` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='员工表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `staff_customer_relation`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `staff_customer_relation` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `staff_id` bigint NOT NULL,
  `customer_id` bigint NOT NULL,
  `bind_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `status` tinyint DEFAULT '1' COMMENT '1=pending,2=approved,3=rejected',
  `approved_at` datetime DEFAULT NULL,
  `first_effective_at` datetime DEFAULT NULL,
  `bind_source` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'QR / MANUAL 等',
  `bind_order_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_staff_customer` (`staff_id`, `customer_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='员工与客户绑定关系（拉新归属）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `staff_monthly_goal`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `staff_monthly_goal` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `staff_id` bigint NOT NULL,
  `month` varchar(7) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'YYYY-MM',
  `target_sales_amount` decimal(14,2) DEFAULT '0.00',
  `target_new_customer` int DEFAULT '0',
  `target_commission` decimal(14,2) DEFAULT '0.00',
  `status` tinyint DEFAULT '1' COMMENT '1-running,2-settled',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_staff_month` (`staff_id`, `month`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='员工月目标表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `staff_performance_log`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `staff_performance_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `staff_id` bigint NOT NULL,
  `store_id` bigint NOT NULL,
  `biz_type` tinyint NOT NULL COMMENT '1-sale,2-recharge,3-new_customer,4-refund,5-adjust,6-maintain,7-package,8-brand',
  `biz_id` bigint DEFAULT NULL COMMENT 'order_id 或 recharge_id 等',
  `order_item_id` bigint DEFAULT NULL,
  `amount` decimal(10,2) NOT NULL,
  `calc_desc` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `need_review` tinyint DEFAULT '0' COMMENT '0-正常 1-待复核',
  `status` tinyint NOT NULL DEFAULT '2' COMMENT '1-冻结 2-已解冻',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `freeze_until` datetime DEFAULT NULL COMMENT '解冻时点:冻结类=created_at+冻结期,非冻结类=created_at',
  PRIMARY KEY (`id`),
  KEY `idx_staff` (`staff_id`),
  KEY `idx_biz` (`biz_type`, `biz_id`),
  KEY `idx_freeze` (`status`, `freeze_until`),
  KEY `idx_staff_status_freeze` (`staff_id`, `status`,`freeze_until`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='绩效流水记录表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `staff_salary_bill`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `staff_salary_bill` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `staff_id` bigint NOT NULL,
  `month` varchar(7) COLLATE utf8mb4_unicode_ci NOT NULL,
  `total_commission` decimal(14,2) DEFAULT '0.00',
  `total_sales` decimal(14,2) DEFAULT '0.00',
  `new_customer_count` int DEFAULT '0',
  `status` tinyint DEFAULT '0' COMMENT '0-pending,1-confirmed',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `confirmed_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_staff_month_bill` (`staff_id`, `month`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='员工工资单（月度汇总）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `store`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `store` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `code` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '门店编码',
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '门店名称',
  `address` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '门店地址',
  `phone` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '联系电话',
  `manager_id` bigint DEFAULT NULL COMMENT '店长ID（关联staff表）',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：1-正常，2-停用',
  `support_pickup` tinyint DEFAULT '1' COMMENT '是否支持自提：1-是 0-否',
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人ID',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人ID',
  `deleted` tinyint DEFAULT '0' COMMENT '逻辑删除：1-已删 0-未删',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_store_code` (`code`),
  KEY `idx_store_status` (`status`),
  KEY `idx_store_manager` (`manager_id`),
  KEY `idx_store_pickup` (`support_pickup`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='门店表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `supplier`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `supplier` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `code` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '供应商编码',
  `name` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '供应商名称',
  `contact_name` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '联系人姓名',
  `contact_phone` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '联系人电话',
  `bank_account` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '银行账户',
  `bank_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '开户银行',
  `address` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '供应商地址',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：1-正常，2-停用',
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人ID',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_supplier_code` (`code`),
  KEY `idx_supplier_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='供应商表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_code_sequence`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_code_sequence` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code_type` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '编码类型：STORE, STAFF, PRODUCT, CUSTOMER, SUPPLIER, ROLE',
  `last_code` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '最后一个编码',
  `sequence` bigint DEFAULT '0' COMMENT '当前序号',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code_type` (`code_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='编码序号表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_dict_data`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_dict_data` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `type_code` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '关联字典类型编码',
  `label` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '展示名',
  `value` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '存储值',
  `sort_order` int DEFAULT '0' COMMENT '排序(越小越前)',
  `status` tinyint DEFAULT '1',
  `is_default` tinyint DEFAULT '0' COMMENT '是否默认选中',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_dict_data_type` (`type_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='字典数据表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_dict_type`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_dict_type` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '字典编码',
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '字典名称',
  `status` tinyint DEFAULT '1' COMMENT '1-启用 0-停用',
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dict_type_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='字典类型表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_menu`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_menu` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `parent_id` bigint DEFAULT '0',
  `name` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `permission` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '权限标识',
  `type` tinyint DEFAULT NULL COMMENT '1-菜单 2-按钮',
  `path` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `icon` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `sort_order` int DEFAULT '0',
  `visible` tinyint DEFAULT '1' COMMENT '1-显示 0-隐藏',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统菜单/权限';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_operation_log`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_operation_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `staff_id` bigint DEFAULT NULL,
  `module` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `action` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `method` varchar(10) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `request_uri` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `request_params` text COLLATE utf8mb4_unicode_ci,
  `ip` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `cost_time` bigint DEFAULT NULL,
  `status` tinyint DEFAULT NULL COMMENT '1-成功 0-失败',
  `error_msg` text COLLATE utf8mb4_unicode_ci,
  `trace_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_staff_time` (`staff_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统操作日志';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_role`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_role` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '角色名称',
  `code` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '角色编码',
  `status` tinyint DEFAULT '1' COMMENT '1-启用 0-停用',
  `remark` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统角色';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_role_menu`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_role_menu` (
  `role_id` bigint NOT NULL,
  `menu_id` bigint NOT NULL,
  PRIMARY KEY (`role_id`, `menu_id`),
  KEY `fk_role_menu_menu` (`menu_id`),
  CONSTRAINT `fk_role_menu_menu` FOREIGN KEY (`menu_id`) REFERENCES `sys_menu` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_role_menu_role` FOREIGN KEY (`role_id`) REFERENCES `sys_role` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色菜单关联';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_staff_role`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_staff_role` (
  `staff_id` bigint NOT NULL,
  `role_id` bigint NOT NULL,
  PRIMARY KEY (`staff_id`, `role_id`),
  KEY `fk_staff_role_role` (`role_id`),
  CONSTRAINT `fk_staff_role_role` FOREIGN KEY (`role_id`) REFERENCES `sys_role` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_staff_role_staff` FOREIGN KEY (`staff_id`) REFERENCES `staff` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='员工角色关联';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `system_notification`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `system_notification` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `title` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '通知标题',
  `content` text COLLATE utf8mb4_unicode_ci COMMENT '通知内容',
  `type` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '类型：stock_warning-库存预警, order_event-订单事件, approval-审批, system-系统通知, commission-提成通知',
  `target_type` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '目标类型：staff-指定员工, role-指定角色, all-全员',
  `target_id` bigint DEFAULT NULL COMMENT '目标ID（staff_id或role_id，all时为空）',
  `is_read` tinyint NOT NULL DEFAULT '0' COMMENT '是否已读：1-已读 0-未读',
  `read_at` datetime DEFAULT NULL COMMENT '阅读时间',
  `extra_data` json DEFAULT NULL COMMENT '附加数据（如关联的订单号、商品ID等）',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_notification_target` (`target_type`, `target_id`,`is_read`),
  KEY `idx_notification_type` (`type`, `created_at`),
  KEY `idx_notification_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统通知表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `vip_monthly_record`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `vip_monthly_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `customer_id` bigint NOT NULL COMMENT '客户ID',
  `year_month` varchar(7) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '发放月份，如 2023-10',
  `vip_level` tinyint NOT NULL COMMENT 'VIP等级',
  `record_type` tinyint NOT NULL DEFAULT '1' COMMENT '记录类型：1-系统自动发放（月度权益），2-充值奖励发放',
  `grant_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发放时间',
  `content` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '发放内容描述',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_customer_month` (`customer_id`, `year_month`),
  KEY `idx_customer_id` (`customer_id`),
  KEY `idx_year_month` (`year_month`),
  CONSTRAINT `fk_vip_monthly_customer` FOREIGN KEY (`customer_id`) REFERENCES `customer` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='VIP每月权益发放记录表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `wallet_auth_code`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `wallet_auth_code` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `customer_id` bigint NOT NULL COMMENT '客户ID',
  `order_id` bigint DEFAULT NULL COMMENT '关联订单ID',
  `auth_code` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '6位授权码',
  `amount` decimal(12,2) NOT NULL COMMENT '授权金额',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '0=待使用,1=已使用,2=已过期',
  `expire_at` datetime NOT NULL COMMENT '过期时间',
  `used_at` datetime DEFAULT NULL COMMENT '使用时间',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_auth_code` (`auth_code`),
  KEY `idx_customer_id` (`customer_id`),
  KEY `idx_status_expire` (`status`, `expire_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='钱包授权码';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `wallet_transaction`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `wallet_transaction` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `customer_id` bigint NOT NULL COMMENT '客户ID',
  `wallet_type` tinyint NOT NULL COMMENT '钱包类型：1-本金，2-专用金，3-积分',
  `transaction_type` tinyint NOT NULL COMMENT '交易类型：1-充值，2-消费，3-退款，4-赠送，5-扣除',
  `amount` decimal(10,2) NOT NULL COMMENT '金额（正数为增加，负数为减少）',
  `balance_before` decimal(10,2) NOT NULL COMMENT '交易前余额',
  `balance_after` decimal(10,2) NOT NULL COMMENT '交易后余额',
  `reference_type` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '关联类型（如：ORDER-订单，RECHARGE-充值）',
  `reference_id` bigint DEFAULT NULL COMMENT '关联ID',
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `payment_method` tinyint DEFAULT NULL COMMENT '支付方式：1-现金，2-微信，3-支付宝（仅充值交易有效）',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `created_by` bigint DEFAULT NULL COMMENT '操作人ID',
  PRIMARY KEY (`id`),
  KEY `idx_customer_id` (`customer_id`),
  KEY `idx_wallet_type` (`wallet_type`),
  KEY `idx_transaction_type` (`transaction_type`),
  KEY `idx_reference` (`reference_type`, `reference_id`),
  KEY `idx_created_at` (`created_at`),
  CONSTRAINT `fk_wallet_transaction_customer` FOREIGN KEY (`customer_id`) REFERENCES `customer` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='钱包交易记录表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-11-02  8:15:00
