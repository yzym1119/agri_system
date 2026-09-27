-- =====================================================================
-- 零售事实链切换前检查 (preflight) — 只列不改, 无 DDL / 无任何写操作
--
-- 用法:
--   mysql -h127.0.0.1 -uroot -t <数据库名> < scripts/retail-fact-chain-preflight.sql
--
-- 参数 (session 变量, 按拟切换范围修改下方 SET; 空字符串 = 覆盖所有行):
--   @store_ids   门店 ID 列表 (CSV), 例 '1,2'
--   @product_ids 商品 ID 列表 (CSV), 例 '101,205'
--   @tolerance   数量容差 (默认 0.00000000); B5 中 |差异| > @tolerance 才报告
--
-- 输出: 7 个检查块, 每块一次 SELECT; 列 check_id / severity(BLOCKER|WARN|LIST):
--   B1 缺确认基准单位          BLOCKER
--   B2 legacy 主辅单位无效     WARN
--   B3 无销售单位的价格行      BLOCKER(单位不存在) / WARN(sale_unit 为空)
--   B4 日期未知批次            LIST (设计允许, 仅知情)
--   B5 聚合/批次不匹配         BLOCKER(差异/UNRESOLVED) / WARN(缺 legacy 快照行)
--   B6 重复默认货位            BLOCKER
--   B7 缺 legacy 库存快照行    WARN (既有 legacy 遗留缺口)
--
-- 语义: BLOCKER = 切换前必须解决; WARN = 必须人工确认; LIST = 仅列出。
-- 本文件不含 INSERT/UPDATE/DELETE/ALTER, 可在只读副本上安全执行。
-- =====================================================================

SET @store_ids = '';          -- 门店范围, 空 = 全部门店
SET @product_ids = '';        -- 商品范围, 空 = 全部商品
SET @tolerance = 0.00000000;  -- B5 数量容差

-- ---------------------------------------------------------------------
-- B1 缺确认基准单位 (BLOCKER)
--     启用商品 (product.status = 1) 不存在启用中的基准单位
--     (product_unit.status = 1 AND is_base_unit = 1)。
--     单位是商品级配置, 与门店无关。
-- ---------------------------------------------------------------------
SELECT 'B1' AS check_id,
       'BLOCKER' AS severity,
       p.id AS product_id,
       p.code AS product_code,
       p.name AS product_name,
       p.main_unit,
       '启用商品缺少已确认基准单位(product_unit.status=1 AND is_base_unit=1)' AS issue,
       '先在单位设置页配置基准单位, 否则新链无法建立可换算的余额' AS remedy
FROM product p
WHERE p.status = 1
  AND (@product_ids = '' OR FIND_IN_SET(p.id, @product_ids) > 0)
  AND NOT EXISTS (
      SELECT 1 FROM product_unit pu
      WHERE pu.product_id = p.id AND pu.status = 1 AND pu.is_base_unit = 1
  )
ORDER BY p.id;

-- ---------------------------------------------------------------------
-- B2 legacy 主辅单位无效 (WARN)
--     aux_unit 非空但换算比例缺失/非正, 或辅单位与主单位同名。
--     legacy 语义不可靠时必须人工确认, 不得由脚本推断箱/瓶。
-- ---------------------------------------------------------------------
SELECT 'B2' AS check_id,
       'WARN' AS severity,
       p.id AS product_id,
       p.code AS product_code,
       p.name AS product_name,
       p.main_unit,
       p.aux_unit,
       p.main_to_aux_ratio,
       CASE WHEN p.aux_unit IS NOT NULL AND p.aux_unit = p.main_unit THEN '辅单位与主单位同名'
            WHEN p.aux_unit IS NOT NULL AND p.main_to_aux_ratio IS NULL THEN '有辅单位但换算比例缺失'
            ELSE '换算比例非正数' END AS issue,
       '人工确认 legacy 主辅换算语义, 决定是否/如何录入 product_unit' AS remedy
FROM product p
WHERE p.status = 1
  AND (@product_ids = '' OR FIND_IN_SET(p.id, @product_ids) > 0)
  AND (
        (p.aux_unit IS NOT NULL AND (p.main_to_aux_ratio IS NULL OR p.main_to_aux_ratio <= 0))
     OR (p.aux_unit IS NOT NULL AND p.aux_unit = p.main_unit)
  )
ORDER BY p.id;

-- ---------------------------------------------------------------------
-- B3 无销售单位的价格行
--     BLOCKER: 启用价格行 sale_unit 非空, 但该商品不存在启用中的可销售单位
--              (product_unit.unit_name = sale_unit AND status = 1 AND allow_sale = 1)
--     WARN   : 启用价格行 sale_unit 为空 —— V126 语义允许 (沿用 legacy 主单位换算),
--              切换时须人工确认, 不得静默视作无问题。
--     store_id 为 NULL 的价格行为全局价, 不受 @store_ids 过滤。
-- ---------------------------------------------------------------------
SELECT 'B3' AS check_id,
       'BLOCKER' AS severity,
       pp.product_id,
       p.code AS product_code,
       p.name AS product_name,
       pp.id AS price_id,
       pp.store_id,
       pp.price_type,
       pp.sale_unit,
       'sale_unit 在 product_unit 中不存在启用中的可销售单位' AS issue,
       '先在单位设置页补录该销售单位(或修正价格行的 sale_unit)' AS remedy
FROM product_price pp
JOIN product p ON p.id = pp.product_id
WHERE pp.status = 1
  AND pp.sale_unit IS NOT NULL
  AND (@product_ids = '' OR FIND_IN_SET(pp.product_id, @product_ids) > 0)
  AND (@store_ids = '' OR pp.store_id IS NULL OR FIND_IN_SET(pp.store_id, @store_ids) > 0)
  AND NOT EXISTS (
      SELECT 1 FROM product_unit pu
      WHERE pu.product_id = pp.product_id
        AND pu.unit_name = pp.sale_unit
        AND pu.status = 1
        AND pu.allow_sale = 1
  )
UNION ALL
SELECT 'B3' AS check_id,
       'WARN' AS severity,
       pp.product_id,
       p.code AS product_code,
       p.name AS product_name,
       pp.id AS price_id,
       pp.store_id,
       pp.price_type,
       pp.sale_unit,
       '启用价格行 sale_unit 为空, 沿用 legacy 主单位换算语义' AS issue,
       '切换前人工确认该价格行的单位语义(V126 允许留空, 但必须知情)' AS remedy
FROM product_price pp
JOIN product p ON p.id = pp.product_id
WHERE pp.status = 1
  AND pp.sale_unit IS NULL
  AND (@product_ids = '' OR FIND_IN_SET(pp.product_id, @product_ids) > 0)
  AND (@store_ids = '' OR pp.store_id IS NULL OR FIND_IN_SET(pp.store_id, @store_ids) > 0)
ORDER BY 2, 3, 6;

-- ---------------------------------------------------------------------
-- B4 日期未知批次 (LIST)
--     date_status = 'UNKNOWN' 为设计允许 (V119 CHECK)。仅列出, 让运营知道
--     哪些批次无生产/有效期进入新链; 不自动阻断。
-- ---------------------------------------------------------------------
SELECT 'B4' AS check_id,
       'LIST' AS severity,
       rb.store_id,
       rb.product_id,
       p.code AS product_code,
       rb.id AS receipt_batch_id,
       rb.internal_receipt_batch_no,
       rb.original_unit_name,
       rb.original_quantity,
       rb.base_quantity,
       rb.date_status,
       rb.received_at,
       '进入新链的批次无生产/有效期(UNKNOWN, 设计允许)' AS issue,
       '运营知情并向收货人复核; 不自动阻断' AS remedy
FROM inventory_receipt_batch rb
JOIN product p ON p.id = rb.product_id
WHERE rb.date_status = 'UNKNOWN'
  AND (@store_ids = '' OR FIND_IN_SET(rb.store_id, @store_ids) > 0)
  AND (@product_ids = '' OR FIND_IN_SET(rb.product_id, @product_ids) > 0)
ORDER BY rb.store_id, rb.product_id, rb.id;

-- ---------------------------------------------------------------------
-- B5 聚合/批次不匹配 (BLOCKER / WARN)
--     逐 (store, product) 对比:
--       legacy 侧 = SUM(inventory.quantity × factor(→基准))       (无行 = NULL, 不按 0)
--       新链侧   = SUM(inventory_batch_balance.base_quantity)     (无行 = 0)
--     真实 schema 注 (以全量迁移后的库为准): V6_1 已删除 inventory.unit 列,
--     inventory.quantity 按主单位记录 (见列注释「库存数量（主单位，8位小数）」)。
--     因此 factor 解析为:
--       已启用 (balance_mode = 'BATCH_LOCATION') 的行: 投影已把快照改写为
--         基准单位量 -> factor = 1;
--       未启用且商品存在持久化 product_unit 行: 取 unit_name = product.main_unit
--         且 status = 1 的行的 base_quantity_per_unit (人工确认的换算);
--         解析不到 (主单位行缺失/已停用/主单位空白) -> UNRESOLVED;
--       未启用且商品无任何 product_unit 行: 与
--         ProductUnitService.projectLegacyUnits 的 legacy 投影一致 —
--         主单位即基准单位 -> factor = 1 (主单位空白 -> UNRESOLVED)。
--     命中:
--       UNRESOLVED     无法解析主单位→基准的换算 -> BLOCKER (绝不静默按 0 计)
--       MISSING_LEGACY 新链有余额但 legacy 无任何快照行 -> WARN (补救见 B7)
--       MISMATCH       |legacy_base_total - new_base_total| > @tolerance -> BLOCKER
--     提示: 用 @store_ids / @product_ids 限定到拟切换范围, 否则历史存量商品会
--     大量呈现 MISMATCH (初始化见 runbook §历史初始化)。
-- ---------------------------------------------------------------------
WITH legacy_rows AS (
    SELECT i.store_id,
           i.product_id,
           i.quantity,
           CASE
               WHEN fs.balance_mode = 'BATCH_LOCATION' THEN 1
               WHEN EXISTS (SELECT 1 FROM product_unit x WHERE x.product_id = i.product_id) THEN
                   (SELECT pu.base_quantity_per_unit
                    FROM product_unit pu
                    WHERE pu.product_id = i.product_id
                      AND pu.unit_name = TRIM(COALESCE(p.main_unit, ''))
                      AND pu.status = 1
                    ORDER BY pu.id
                    LIMIT 1)
               WHEN TRIM(COALESCE(p.main_unit, '')) = '' THEN NULL
               ELSE 1
           END AS factor
    FROM inventory i
    JOIN product p ON p.id = i.product_id
    LEFT JOIN inventory_balance_feature_status fs
           ON fs.store_id = i.store_id AND fs.product_id = i.product_id
    WHERE (@store_ids = '' OR FIND_IN_SET(i.store_id, @store_ids) > 0)
      AND (@product_ids = '' OR FIND_IN_SET(i.product_id, @product_ids) > 0)
),
legacy AS (
    SELECT store_id,
           product_id,
           SUM(CASE WHEN factor IS NULL THEN 0 ELSE quantity * factor END) AS legacy_base_total,
           SUM(CASE WHEN factor IS NULL THEN 1 ELSE 0 END) AS unresolved_rows
    FROM legacy_rows
    GROUP BY store_id, product_id
),
new_chain AS (
    SELECT b.store_id,
           b.product_id,
           SUM(b.base_quantity) AS new_base_total
    FROM inventory_batch_balance b
    WHERE (@store_ids = '' OR FIND_IN_SET(b.store_id, @store_ids) > 0)
      AND (@product_ids = '' OR FIND_IN_SET(b.product_id, @product_ids) > 0)
    GROUP BY b.store_id, b.product_id
),
pairs AS (
    SELECT store_id, product_id FROM legacy
    UNION
    SELECT store_id, product_id FROM new_chain
)
SELECT 'B5' AS check_id,
       CASE WHEN l.unresolved_rows > 0 OR l.legacy_base_total IS NOT NULL THEN 'BLOCKER' ELSE 'WARN' END AS severity,
       CASE WHEN l.unresolved_rows > 0 THEN 'UNRESOLVED'
            WHEN l.legacy_base_total IS NULL THEN 'MISSING_LEGACY'
            ELSE 'MISMATCH' END AS status,
       pr.store_id,
       pr.product_id,
       p.code AS product_code,
       p.name AS product_name,
       l.legacy_base_total,
       COALESCE(n.new_base_total, 0) AS new_base_total,
       (COALESCE(l.legacy_base_total, 0) - COALESCE(n.new_base_total, 0)) AS diff,
       COALESCE(l.unresolved_rows, 0) AS unresolved_rows,
       CASE WHEN l.unresolved_rows > 0 THEN '无法解析主单位→基准单位的换算(未按 0 计)'
            WHEN l.legacy_base_total IS NULL THEN '新链有余额但 legacy 无任何快照行'
            ELSE '跨账本总量差异超出 @tolerance' END AS issue,
       CASE WHEN l.unresolved_rows > 0 THEN '先确认/补录主单位→基准单位换算(见 B1 与单位设置页, 或 §历史初始化)后重算'
            WHEN l.legacy_base_total IS NULL THEN '先补建 legacy 快照行再激活, 见 B7'
            ELSE '完成/修正历史初始化(批次+货位余额+移动事实)后重跑' END AS remedy
FROM pairs pr
LEFT JOIN legacy l ON l.store_id = pr.store_id AND l.product_id = pr.product_id
LEFT JOIN new_chain n ON n.store_id = pr.store_id AND n.product_id = pr.product_id
JOIN product p ON p.id = pr.product_id
WHERE l.unresolved_rows > 0
   OR (l.legacy_base_total IS NULL AND COALESCE(n.new_base_total, 0) <> 0)
   OR (l.legacy_base_total IS NOT NULL
       AND ABS(l.legacy_base_total - COALESCE(n.new_base_total, 0)) > @tolerance)
ORDER BY CASE severity WHEN 'BLOCKER' THEN 0 ELSE 1 END, pr.store_id, pr.product_id;

-- ---------------------------------------------------------------------
-- B6 重复默认货位 (BLOCKER)
--     同门店同 location_type 下 code IS NULL 的货位多于 1 条: V123 的生成列
--     唯一键 uk_inventory_location_default 会失败 (也说明该库已存在损坏态)。
--     补救 (须人工): 先把 inventory_batch_balance.location_id 人工重指向保留货位,
--     再删/改多余行。
-- ---------------------------------------------------------------------
SELECT 'B6' AS check_id,
       'BLOCKER' AS severity,
       il.store_id,
       il.location_type,
       COUNT(*) AS duplicate_count,
       GROUP_CONCAT(il.id ORDER BY il.id) AS location_ids,
       '同门店同类型的默认货位(code IS NULL)多于 1 条, V123 唯一键会失败' AS issue,
       '先把 inventory_batch_balance.location_id 人工重指向保留货位，再删/改多余行' AS remedy
FROM inventory_location il
WHERE il.code IS NULL
  AND (@store_ids = '' OR FIND_IN_SET(il.store_id, @store_ids) > 0)
GROUP BY il.store_id, il.location_type
HAVING COUNT(*) > 1
ORDER BY il.store_id, il.location_type;

-- ---------------------------------------------------------------------
-- B7 缺 legacy 库存快照行 (WARN, advisory)
--     范围内门店/SKU 中不存在任何 inventory 行 (legacy 口径将其库存视为 0)。
--     真实 schema 注: V6_1 起 inventory 仅剩 (store_id, product_id, quantity[主单位],
--     时间戳), 无 unit 列; 激活后首次投影 (BatchBalanceService.projectInventory ->
--     InventoryRepositoryImpl.createIfNotExists) 会以数量 0 自动补建快照行再回写
--     基准聚合, 不会报错。因此本块的风险不是「插不进去」, 而是:
--       ① 该门店/SKU 在 legacy 账本中没有任何存量基线 —— 若历史初始化按真实库存
--          建了批次余额, B5/L1 会呈 MISSING_LEGACY 差异;
--       ② 回滚/审计时缺少 legacy 侧的对比基线。
--     补救: 激活前人工确认存量, 补建 legacy 快照行 (数量 = 主单位存量, 可为 0)
--     并留证, 再执行切换。
--     注: 该缺口为既有 legacy 遗留 (非 Task5–10 引入); 建议用 @store_ids
--     限定到拟切换门店, 否则会覆盖大量无该门店库存的 SKU。
-- ---------------------------------------------------------------------
SELECT 'B7' AS check_id,
       'WARN' AS severity,
       s.id AS store_id,
       s.name AS store_name,
       p.id AS product_id,
       p.code AS product_code,
       p.name AS product_name,
       p.main_unit,
       '该门店/SKU 无 legacy inventory 快照行; legacy 口径视其库存为 0, 历史初始化后 B5/L1 将报 MISSING_LEGACY' AS issue,
       '激活前人工确认存量并补建 legacy 快照行(数量=主单位存量, 可为 0)并留证, 再执行切换' AS remedy
FROM product p
JOIN store s ON (s.status = 1 OR FIND_IN_SET(s.id, @store_ids) > 0)
WHERE p.status = 1
  AND (@product_ids = '' OR FIND_IN_SET(p.id, @product_ids) > 0)
  AND (@store_ids = '' OR FIND_IN_SET(s.id, @store_ids) > 0)
  AND NOT EXISTS (
      SELECT 1 FROM inventory i
      WHERE i.store_id = s.id AND i.product_id = p.id
  )
ORDER BY s.id, p.id;
