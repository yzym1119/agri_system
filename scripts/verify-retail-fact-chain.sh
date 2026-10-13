#!/usr/bin/env bash
# 统一库存账只读对账：不读取任何SKU模式开关。
# 用法: scripts/verify-retail-fact-chain.sh --database 库名 [--stores CSV] [--products CSV]
# 数量容差默认0；退出码0全部一致、1有差异、2参数/连接/查询失败。
# 收货也是RECEIPT流水，余额必须等于所有移动的净额，不能再额外加一次收货数量。
set -euo pipefail

HOST="127.0.0.1"
PORT="3306"
DB_USER="root"
DB_PASSWORD="${MYSQL_PWD:-}"
DATABASE=""
STORES=""
PRODUCTS=""
TOLERANCE="0"

usage() {
    echo "用法: scripts/verify-retail-fact-chain.sh --database <库名> [--host H] [--port P] [--user U] [--password PW] [--stores CSV] [--products CSV] [--tolerance N]"
    echo "说明: 只读统一库存对账 (L1 主单位快照 / L2 余额与流水 / L3 销售分配); 退出码 2=参数/连接错误, 1=存在超容差差异, 0=全部一致; -h 查看完整帮助。"
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

probe_out=""
if ! probe_out=$(run_sql "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN ('product_unit','inventory','inventory_batch_balance','inventory_movement','order_item_batch_allocation');" 2>&1); then
    echo "$probe_out" >&2
    die "无法连接所选数据库"
fi
[[ "$probe_out" == "5" ]] || die "缺少统一库存相关表，请先完成迁移"
PRELUDE="SET @store_ids := '$STORES'; SET @product_ids := '$PRODUCTS'; SET @tolerance := $TOLERANCE;"
FILTER="(@store_ids='' OR FIND_IN_SET(x.store_id,@store_ids)>0) AND (@product_ids='' OR FIND_IN_SET(x.product_id,@product_ids)>0)"
SQL_L1="WITH stock AS (
    SELECT store_id,product_id,SUM(base_quantity) qty FROM inventory_batch_balance GROUP BY store_id,product_id
), pairs AS (
    SELECT store_id,product_id FROM inventory UNION SELECT store_id,product_id FROM stock
)
SELECT x.store_id,x.product_id,i.quantity main_quantity,u.base_quantity_per_unit factor,COALESCE(b.qty,0) base_quantity
FROM pairs x JOIN product p ON p.id=x.product_id
LEFT JOIN product_unit u ON u.product_id=p.id AND u.unit_name=p.main_unit AND u.status=1
LEFT JOIN inventory i ON i.store_id=x.store_id AND i.product_id=x.product_id
LEFT JOIN stock b ON b.store_id=x.store_id AND b.product_id=x.product_id
WHERE $FILTER AND (u.id IS NULL OR i.id IS NULL OR ABS(i.quantity-ROUND(COALESCE(b.qty,0)/u.base_quantity_per_unit,8))>@tolerance);"
SQL_L2="WITH net AS (
    SELECT batch_id,location_id,store_id,product_id,SUM(qty) qty FROM (
        SELECT batch_id,to_location_id location_id,store_id,product_id,base_quantity qty FROM inventory_movement WHERE to_location_id IS NOT NULL
        UNION ALL
        SELECT batch_id,from_location_id,store_id,product_id,-base_quantity FROM inventory_movement WHERE from_location_id IS NOT NULL
    ) m GROUP BY batch_id,location_id,store_id,product_id
), pairs AS (
    SELECT batch_id,location_id,store_id,product_id FROM inventory_batch_balance
    UNION SELECT batch_id,location_id,store_id,product_id FROM net
)
SELECT x.store_id,x.product_id,x.batch_id,x.location_id,b.base_quantity,n.qty movement_net
FROM pairs x LEFT JOIN inventory_batch_balance b ON b.batch_id=x.batch_id AND b.location_id=x.location_id
LEFT JOIN net n ON n.batch_id=x.batch_id AND n.location_id=x.location_id
WHERE $FILTER AND (b.id IS NULL OR ABS(b.base_quantity-COALESCE(n.qty,0))>@tolerance
    OR b.base_quantity<b.reserved_base_quantity OR b.reserved_base_quantity<0);"
SQL_L3="SELECT x.store_id,x.product_id,x.id allocation_id,x.order_item_id,x.source_movement_id
FROM order_item_batch_allocation x LEFT JOIN inventory_movement m ON m.id=x.source_movement_id
WHERE $FILTER AND (m.id IS NULL OR m.movement_type<>'SALE' OR m.to_location_id IS NOT NULL
    OR m.batch_id<>x.batch_id OR m.from_location_id<>x.location_id
    OR ABS(m.base_quantity-x.allocated_base_quantity)>@tolerance
    OR ABS(m.unit_cost-x.batch_unit_cost)>@tolerance
    OR ABS(x.allocated_cost-ROUND(x.allocated_base_quantity*x.batch_unit_cost,8))>@tolerance);"
failed=0
check_layer() {
    local label="$1" sql="$2" output
    if ! output=$(run_sql "$PRELUDE $sql" 2>&1); then
        echo "$output" >&2
        die "$label 查询失败"
    fi
    if [[ -n "$output" ]]; then
        echo "[$label] 发现差异："
        echo "$output"
        failed=1
    else
        echo "[$label] 一致"
    fi
}
check_layer "L1 主单位快照" "$SQL_L1"
check_layer "L2 余额与移动净额" "$SQL_L2"
check_layer "L3 销售批次与实际出库" "$SQL_L3"
exit "$failed"
