-- =====================================================================
-- 零售事实链历史初始化 — 基准单位回填提案 (backfill draft)
-- 只读报告: 仅 SELECT, 无 DDL / 无 INSERT / 无 UPDATE / 无 DELETE。
--
-- 用法:
--   mysql -h127.0.0.1 -uroot -t <数据库名> < scripts/retail-fact-chain-backfill-draft.sql
--
-- 参数 (session 变量, 按拟初始化范围修改下方 SET; 空字符串 = 覆盖所有行):
--   @product_ids 商品 ID 列表 (CSV), 例 '101,205' —— 建议限定为拟切换 SKU 集合
--   @store_ids   门店 ID 列表 (CSV), 例 '1,2'; 非空时只报告在这些门店存在
--                legacy inventory 行的商品; 空 = 不做门店限定
--
-- 输出: 单结果集, 每个商品一行 (按提案优先级排序), 列为:
--   product_id, product_code, product_name, main_unit, aux_unit, main_to_aux_ratio,
--   proposal_status, proposed_unit_name, proposed_base_quantity_per_unit, reason, apply_sql
--
-- proposal_status 语义 (人工确认 + 无歧义半自动, 不做任何推断):
--   AUTO_READY               单一单位商品 (aux_unit 与 main_to_aux_ratio 均为 NULL)、
--                            尚无任何 product_unit 配置且 main_unit 非空白:
--                            apply_sql 为可直接执行的 INSERT 语句 (运营复核后执行)。
--   SKIP_ALREADY_CONFIGURED  该商品已存在 product_unit 行 (可能来自单位设置页),
--                            仅报告, 不提案。
--   PENDING_CONFIRMATION     存在辅单位/换算比例证据、比例非正或主单位空白:
--                            需人工确认并在单位设置页录入; proposed_* 与 apply_sql 为 NULL。
--
-- 重要: 本脚本绝不推断「箱/瓶」或任何换算方向。仅当商品只有一个 legacy 单位
-- (main_unit, 无辅单位、无比例) 时才提案 base_quantity_per_unit = 1 的基准单位,
-- 所提案的 unit_name 也只使用 legacy main_unit 字符串本身 (仅去除首尾空白)。
-- =====================================================================

SET @product_ids = '';  -- 商品范围, 空 = 全部商品
SET @store_ids = '';    -- 门店范围 (按 legacy inventory 行存在性), 空 = 不限门店

WITH scoped AS (
    SELECT p.id,
           p.code,
           p.name,
           p.main_unit,
           p.aux_unit,
           p.main_to_aux_ratio,
           CASE
               WHEN EXISTS (SELECT 1 FROM product_unit pu WHERE pu.product_id = p.id)
                   THEN 'SKIP_ALREADY_CONFIGURED'
               WHEN p.aux_unit IS NULL
                AND p.main_to_aux_ratio IS NULL
                AND TRIM(p.main_unit) <> ''
                   THEN 'AUTO_READY'
               ELSE 'PENDING_CONFIRMATION'
           END AS proposal_status
    FROM product p
    WHERE (@product_ids = '' OR FIND_IN_SET(p.id, @product_ids) > 0)
      AND (@store_ids = '' OR EXISTS (
              SELECT 1 FROM inventory i
              WHERE i.product_id = p.id
                AND FIND_IN_SET(i.store_id, @store_ids) > 0))
)
SELECT s.id AS product_id,
       s.code AS product_code,
       s.name AS product_name,
       s.main_unit,
       s.aux_unit,
       s.main_to_aux_ratio,
       s.proposal_status,
       CASE WHEN s.proposal_status = 'AUTO_READY' THEN TRIM(s.main_unit) ELSE NULL END AS proposed_unit_name,
       CASE WHEN s.proposal_status = 'AUTO_READY' THEN 1 ELSE NULL END AS proposed_base_quantity_per_unit,
       CASE
           WHEN s.proposal_status = 'AUTO_READY'
               THEN '单单位商品(无辅单位/无换算比例)且尚无 product_unit 配置, 可半自动录入基准单位'
           WHEN s.proposal_status = 'SKIP_ALREADY_CONFIGURED'
               THEN '已存在 product_unit 配置(可能来自单位设置页), 仅报告不提案'
           WHEN TRIM(COALESCE(s.main_unit, '')) = ''
               THEN 'legacy 主单位为空, 需人工确认并在单位设置页录入'
           ELSE 'legacy 存在辅单位/换算比例证据(或比例非正), 脚本不推断箱/瓶与换算方向; 需人工确认并在单位设置页录入'
       END AS reason,
       CASE WHEN s.proposal_status = 'AUTO_READY'
            THEN CONCAT('INSERT INTO product_unit (product_id, unit_name, base_quantity_per_unit, is_base_unit, allow_purchase, allow_sale, allow_inventory, allow_transfer, status) VALUES (',
                        s.id, ', ', QUOTE(TRIM(s.main_unit)), ', 1, 1, 1, 1, 1, 1, 1);')
            ELSE NULL
       END AS apply_sql
FROM scoped s
ORDER BY CASE s.proposal_status
             WHEN 'AUTO_READY' THEN 1
             WHEN 'PENDING_CONFIRMATION' THEN 2
             ELSE 3
         END,
         s.id;
