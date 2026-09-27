#!/usr/bin/env bash
# =====================================================================
# 零售事实链三层对账 verify — 只读 (仅 SELECT), 无 DDL / 无任何写操作
#
# 用法:
#   scripts/verify-retail-fact-chain.sh --database <库名> [更多参数]
#
# 参数:
#   --host        MySQL 主机 (默认 127.0.0.1)
#   --port        MySQL 端口 (默认 3306)
#   --user        用户名 (默认 root)
#   --password    密码 (默认空; 非空时经 MYSQL_PWD 传递, 不进入进程参数)
#   --database    数据库名 (必填, 仅字母/数字/下划线)
#   --stores      门店 ID 列表 CSV, 例 1,2 (空 = 全部门店)
#   --products    商品 ID 列表 CSV, 例 101,205 (空 = 全部商品)
#   --tolerance   数量容差, 非负数字 (默认 0; 三层均生效: |diff| > tolerance 即失败)
#   -h, --help    显示本帮助
#
# 退出码:
#   2 = 参数/连接/查询执行错误 (含目标库缺少所需表)
#   1 = 任一层的差异超出容差
#   0 = 三层全部一致
#
# 三层语义 (详见 docs/runbooks/retail-fact-chain-rollout.md):
#   L1 权威源一致（跨账本总量）: 逐 (store, product) 对比
#      Σ inventory_batch_balance.base_quantity
#      vs Σ inventory.quantity 换算到基准单位
#      真实 schema: V6_1 起 inventory 无 unit 列, quantity 按主单位记录(列注释)。
#      factor 解析: 已启用(balance_mode='BATCH_LOCATION')的快照已被投影改写为
#      基准单位 -> 1; 未启用 -> 取 product_unit(unit_name = product.main_unit
#      AND status=1) 的 base_quantity_per_unit; 商品无任何 product_unit 行时与
#      ProductUnitService.projectLegacyUnits 一致按 factor=1(主单位=基准单位);
#      解析不到记 UNRESOLVED 失败 (绝不静默按 0 计)。
#   L2 投影一致（快照回写）: 仅 balance_mode='BATCH_LOCATION' 的 (store, product):
#      Σ inventory.quantity (快照) vs Σ base_quantity。
#      未启用 SKU 以 N/A (LEGACY) 行报告, 不计入失败; 未启用且无余额的 SKU 不出现。
#   L3 事实可重建（movements ↔ balance）: 逐 (batch, location):
#      balance.base_quantity vs 移动事实净额
#      (+base_quantity 当 to_location_id=loc, -base_quantity 当 from_location_id=loc);
#      另报告"无余额行的移动孤儿组"。不枚举 movement_type。
#
# 提示: 日常对账请用 --stores/--products 限定到当前已切换范围。L1 对未完成
# 历史初始化的 SKU 会如实报告差异 (它们尚未进入新链)。
# 刚激活但尚无任何新链写入(未投影)的 SKU: L1/L2 会如实报告主/基准单位差异,
# 先完成一次新链操作触发投影后再验 (见 runbook §4「投影前提与顺序」)。
# =====================================================================

set -euo pipefail

HOST="127.0.0.1"
PORT="3306"
DB_USER="root"
DB_PASSWORD=""
DATABASE=""
STORES=""
PRODUCTS=""
TOLERANCE="0"

usage() {
    echo "用法: scripts/verify-retail-fact-chain.sh --database <库名> [--host H] [--port P] [--user U] [--password PW] [--stores CSV] [--products CSV] [--tolerance N]"
    echo "说明: 只读三层对账 (L1 跨账本总量 / L2 快照投影 / L3 移动事实↔余额); 退出码 2=参数/连接错误, 1=存在超容差差异, 0=全部一致; -h 查看完整帮助。"
}

die() {
    echo "错误: $*" >&2
    exit 2
}

value_of() { # $@ = 完整参数序列; 校验其后至少有一个取值 (拒绝把下一个选项当作取值)
    local name="$1"
    shift
    if [[ $# -lt 1 || "$1" == --* ]]; then
        die "参数 $name 缺少取值"
    fi
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --host)     value_of "$@"; HOST="$2"; shift 2 ;;
        --port)     value_of "$@"; PORT="$2"; shift 2 ;;
        --user)     value_of "$@"; DB_USER="$2"; shift 2 ;;
        --password) value_of "$@"; DB_PASSWORD="$2"; shift 2 ;;
        --database) value_of "$@"; DATABASE="$2"; shift 2 ;;
        --stores)   value_of "$@"; STORES="$2"; shift 2 ;;
        --products) value_of "$@"; PRODUCTS="$2"; shift 2 ;;
        --tolerance) value_of "$@"; TOLERANCE="$2"; shift 2 ;;
        -h|--help)  usage; exit 0 ;;
        *)          die "未知参数: $1 (用 -h 查看帮助)" ;;
    esac
done

[[ -n "$DATABASE" ]] || die "--database 为必填"
[[ "$DATABASE" =~ ^[A-Za-z0-9_]+$ ]] || die "--database 仅允许字母/数字/下划线"
[[ "$PORT" =~ ^[0-9]+$ ]] || die "--port 必须为数字"
[[ "$TOLERANCE" =~ ^[0-9]+([.][0-9]+)?$ ]] || die "--tolerance 必须为非负数字"
if [[ -n "$STORES" && ! "$STORES" =~ ^[0-9]+(,[0-9]+)*$ ]]; then
    die "--stores 必须为纯数字 CSV 列表, 例 1,2"
fi
if [[ -n "$PRODUCTS" && ! "$PRODUCTS" =~ ^[0-9]+(,[0-9]+)*$ ]]; then
    die "--products 必须为纯数字 CSV 列表, 例 101,205"
fi

MYSQL_BIN="${MYSQL_BIN:-mysql}"
command -v "$MYSQL_BIN" >/dev/null 2>&1 || die "找不到 mysql 客户端 (可用 MYSQL_BIN 指定路径)"

MYSQL_ARGS=("--host=$HOST" "--port=$PORT" "--user=$DB_USER" "--batch" "--raw" "-N")
if [[ -n "$DB_PASSWORD" ]]; then
    export MYSQL_PWD="$DB_PASSWORD"
else
    unset MYSQL_PWD
fi

run_sql() {
    "$MYSQL_BIN" "${MYSQL_ARGS[@]}" "--database=$DATABASE" -e "$1"
}

# 连接 + 库结构预检 (缺表/连不上都归入退出码 2)
probe_out=""
if ! probe_out=$(run_sql "SELECT COUNT(*) FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND table_name IN ('product_unit','inventory','inventory_batch_balance','inventory_movement','inventory_balance_feature_status');" 2>&1); then
    echo "$probe_out" >&2
    die "无法连接数据库 $DATABASE ($HOST:$PORT, user=$DB_USER) 或库不存在"
fi
if [[ "$probe_out" != "5" ]]; then
    die "数据库 $DATABASE 缺少零售事实链相关表 (命中 $probe_out/5; 需先执行 Flyway 迁移 V119+/V121+)"
fi

PRELUDE="SET @store_ids := '$STORES'; SET @product_ids := '$PRODUCTS'; SET @tolerance := $TOLERANCE;"

SQL_L1="WITH legacy_rows AS (
    SELECT i.store_id, i.product_id, i.quantity,
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
    SELECT store_id, product_id,
           SUM(CASE WHEN factor IS NULL THEN 0 ELSE quantity * factor END) AS legacy_base_total,
           SUM(CASE WHEN factor IS NULL THEN 1 ELSE 0 END) AS unresolved_rows
    FROM legacy_rows
    GROUP BY store_id, product_id
),
new_chain AS (
    SELECT b.store_id, b.product_id, SUM(b.base_quantity) AS new_base_total
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
SELECT pr.store_id, pr.product_id,
       CASE WHEN l.unresolved_rows > 0 THEN 'UNRESOLVED'
            WHEN l.legacy_base_total IS NULL THEN 'MISSING_LEGACY'
            ELSE 'MISMATCH' END AS status,
       COALESCE(l.legacy_base_total, 0) AS legacy_base_total,
       COALESCE(n.new_base_total, 0) AS new_base_total,
       (COALESCE(l.legacy_base_total, 0) - COALESCE(n.new_base_total, 0)) AS diff,
       COALESCE(l.unresolved_rows, 0) AS unresolved_rows
FROM pairs pr
LEFT JOIN legacy l ON l.store_id = pr.store_id AND l.product_id = pr.product_id
LEFT JOIN new_chain n ON n.store_id = pr.store_id AND n.product_id = pr.product_id
WHERE l.unresolved_rows > 0
   OR (l.legacy_base_total IS NULL AND COALESCE(n.new_base_total, 0) <> 0)
   OR (l.legacy_base_total IS NOT NULL
       AND ABS(l.legacy_base_total - COALESCE(n.new_base_total, 0)) > @tolerance)
ORDER BY pr.store_id, pr.product_id;"

SQL_L2_FAIL="SELECT f.store_id, f.product_id, f.balance_mode,
       COALESCE(snap.snapshot_total, 0) AS snapshot_total,
       COALESCE(nbal.new_base_total, 0) AS new_base_total,
       (COALESCE(snap.snapshot_total, 0) - COALESCE(nbal.new_base_total, 0)) AS diff,
       CASE WHEN snap.snapshot_total IS NULL THEN 'MISSING_SNAPSHOT'
            ELSE 'MISMATCH' END AS status
FROM inventory_balance_feature_status f
LEFT JOIN (
    SELECT i.store_id, i.product_id, SUM(i.quantity) AS snapshot_total
    FROM inventory i
    WHERE (@store_ids = '' OR FIND_IN_SET(i.store_id, @store_ids) > 0)
      AND (@product_ids = '' OR FIND_IN_SET(i.product_id, @product_ids) > 0)
    GROUP BY i.store_id, i.product_id
) snap ON snap.store_id = f.store_id AND snap.product_id = f.product_id
LEFT JOIN (
    SELECT b.store_id, b.product_id, SUM(b.base_quantity) AS new_base_total
    FROM inventory_batch_balance b
    WHERE (@store_ids = '' OR FIND_IN_SET(b.store_id, @store_ids) > 0)
      AND (@product_ids = '' OR FIND_IN_SET(b.product_id, @product_ids) > 0)
    GROUP BY b.store_id, b.product_id
) nbal ON nbal.store_id = f.store_id AND nbal.product_id = f.product_id
WHERE f.balance_mode = 'BATCH_LOCATION'
  AND (@store_ids = '' OR FIND_IN_SET(f.store_id, @store_ids) > 0)
  AND (@product_ids = '' OR FIND_IN_SET(f.product_id, @product_ids) > 0)
  AND ABS(COALESCE(snap.snapshot_total, 0) - COALESCE(nbal.new_base_total, 0)) > @tolerance
ORDER BY f.store_id, f.product_id;"

SQL_L2_NA="SELECT x.store_id, x.product_id,
       COALESCE(f.balance_mode, 'LEGACY') AS balance_mode,
       'N/A (LEGACY)' AS note
FROM (
    SELECT f0.store_id, f0.product_id
    FROM inventory_balance_feature_status f0
    WHERE (@store_ids = '' OR FIND_IN_SET(f0.store_id, @store_ids) > 0)
      AND (@product_ids = '' OR FIND_IN_SET(f0.product_id, @product_ids) > 0)
    UNION
    SELECT b.store_id, b.product_id
    FROM inventory_batch_balance b
    WHERE (@store_ids = '' OR FIND_IN_SET(b.store_id, @store_ids) > 0)
      AND (@product_ids = '' OR FIND_IN_SET(b.product_id, @product_ids) > 0)
) x
LEFT JOIN inventory_balance_feature_status f
       ON f.store_id = x.store_id AND f.product_id = x.product_id
WHERE COALESCE(f.balance_mode, 'LEGACY') <> 'BATCH_LOCATION'
ORDER BY x.store_id, x.product_id;"

SQL_L3="WITH mv_parts AS (
    SELECT m.batch_id, m.product_id, m.store_id, m.to_location_id AS location_id,
           m.base_quantity AS delta
    FROM inventory_movement m
    WHERE m.to_location_id IS NOT NULL
      AND (@store_ids = '' OR FIND_IN_SET(m.store_id, @store_ids) > 0)
      AND (@product_ids = '' OR FIND_IN_SET(m.product_id, @product_ids) > 0)
    UNION ALL
    SELECT m.batch_id, m.product_id, m.store_id, m.from_location_id AS location_id,
           -m.base_quantity AS delta
    FROM inventory_movement m
    WHERE m.from_location_id IS NOT NULL
      AND (@store_ids = '' OR FIND_IN_SET(m.store_id, @store_ids) > 0)
      AND (@product_ids = '' OR FIND_IN_SET(m.product_id, @product_ids) > 0)
),
mv AS (
    SELECT batch_id, product_id, store_id, location_id, SUM(delta) AS net_base_quantity
    FROM mv_parts
    GROUP BY batch_id, product_id, store_id, location_id
),
bal AS (
    SELECT b.batch_id, b.product_id, b.store_id, b.location_id, b.base_quantity
    FROM inventory_batch_balance b
    WHERE (@store_ids = '' OR FIND_IN_SET(b.store_id, @store_ids) > 0)
      AND (@product_ids = '' OR FIND_IN_SET(b.product_id, @product_ids) > 0)
)
SELECT 'BALANCE_DIFF' AS kind, bal.store_id, bal.product_id, bal.batch_id, bal.location_id,
       bal.base_quantity, COALESCE(mv.net_base_quantity, 0) AS net_base_quantity,
       (bal.base_quantity - COALESCE(mv.net_base_quantity, 0)) AS diff
FROM bal
LEFT JOIN mv ON mv.batch_id = bal.batch_id AND mv.location_id = bal.location_id
WHERE ABS(bal.base_quantity - COALESCE(mv.net_base_quantity, 0)) > @tolerance
UNION ALL
SELECT 'ORPHAN_MOVEMENT' AS kind, mv.store_id, mv.product_id, mv.batch_id, mv.location_id,
       NULL AS base_quantity, mv.net_base_quantity, NULL AS diff
FROM mv
LEFT JOIN bal ON bal.batch_id = mv.batch_id AND bal.location_id = mv.location_id
WHERE bal.batch_id IS NULL
ORDER BY kind, store_id, product_id, batch_id, location_id;"

ROW_COUNT=0
TOTAL_DIFFS=0

show_rows() { # stdin = 行; $1 = 缩进前缀; 置 ROW_COUNT
    local prefix="$1" line
    ROW_COUNT=0
    while IFS= read -r line; do
        if [[ -n "$line" ]]; then
            printf '%s%s\n' "$prefix" "$line"
            ROW_COUNT=$((ROW_COUNT + 1))
        fi
    done
}

layer_fail_rows() { # $1 = 层名; $2 = SQL; 打印 [OK]/[FAIL] 段; 累加 TOTAL_DIFFS
    local name="$1" sql="$2" out
    echo "== $name =="
    if ! out=$(run_sql "$PRELUDE $sql"); then
        echo "错误: $name 查询执行失败 (参数/库结构/连接问题)" >&2
        exit 2
    fi
    if [[ -z "$out" ]]; then
        echo "  [OK] 无差异"
        return 0
    fi
    echo "  [FAIL] 发现超出容差的差异, 每行一条:"
    show_rows "    " <<<"$out"
    echo "  (共 $ROW_COUNT 条)"
    TOTAL_DIFFS=$((TOTAL_DIFFS + ROW_COUNT))
    return 0
}

echo "零售事实链三层对账 (只读): database=$DATABASE host=$HOST port=$PORT stores='${STORES:-全部}' products='${PRODUCTS:-全部}' tolerance=$TOLERANCE"
echo

layer_fail_rows "L1 权威源一致（跨账本总量） (列: store_id product_id status legacy_base_total new_base_total diff unresolved_rows)" "$SQL_L1"
echo
echo "== L2 投影一致（快照回写） (列: store_id product_id balance_mode snapshot_total new_base_total diff status) =="
if ! L2_OUT=$(run_sql "$PRELUDE $SQL_L2_FAIL"); then
    echo "错误: L2 查询执行失败 (参数/库结构/连接问题)" >&2
    exit 2
fi
if [[ -z "$L2_OUT" ]]; then
    echo "  [OK] 已启用(BATCH_LOCATION) SKU 的快照与批次余额一致"
else
    echo "  [FAIL] 已启用 SKU 存在超出容差的投影差异, 每行一条:"
    show_rows "    " <<<"$L2_OUT"
    echo "  (共 $ROW_COUNT 条)"
    TOTAL_DIFFS=$((TOTAL_DIFFS + ROW_COUNT))
fi
if ! L2_NA_OUT=$(run_sql "$PRELUDE $SQL_L2_NA"); then
    echo "错误: L2 N/A 查询执行失败 (参数/库结构/连接问题)" >&2
    exit 2
fi
if [[ -z "$L2_NA_OUT" ]]; then
    echo "  [N/A] 无未启用新链的 SKU"
else
    echo "  [N/A] 未启用新链的 SKU (LEGACY, 不做 L2 比较, 不计失败):"
    show_rows "    " <<<"$L2_NA_OUT"
    echo "  (共 $ROW_COUNT 条)"
fi
echo
layer_fail_rows "L3 事实可重建（movements ↔ balance） (列: kind store_id product_id batch_id location_id base_quantity net_base_quantity diff)" "$SQL_L3"
echo

if [[ "$TOTAL_DIFFS" -eq 0 ]]; then
    echo "VERIFY OK: L1/L2/L3 全部在容差内 (tolerance=$TOLERANCE, stores='${STORES:-全部}', products='${PRODUCTS:-全部}')"
    exit 0
fi
echo "VERIFY FAILED: 共 $TOTAL_DIFFS 条超出容差的差异 (tolerance=$TOLERANCE); 按 runbook §对账节奏/§回滚 处置并留证" >&2
exit 1
