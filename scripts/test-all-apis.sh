#!/bin/bash
# 全面 API 测试 — 覆盖所有后端端点
BASE="http://[::1]:8080"
TOKEN="1960fac7-dd88-4ff4-83ad-1f100ab2c334"
AUTH="Authorization: $TOKEN"
CT="Content-Type: application/json"
PASS=0; FAIL=0; ERRORS=""

call() {
  local method="$1" desc="$2" url="$3" data="$4"
  local args=(-s -w "\n%{http_code}" -X "$method" -H "$AUTH")
  if [ -n "$data" ]; then
    args+=(-H "$CT" -d "$data")
  fi
  local resp
  resp=$(curl "${args[@]}" "$BASE$url")
  local http_code body
  http_code=$(echo "$resp" | tail -1)
  body=$(echo "$resp" | sed '$d')

  # HTTP 200 = pass (some endpoints return raw data without code wrapper)
  if [ "$http_code" = "200" ]; then
    echo "  [PASS] $desc"
    PASS=$((PASS + 1))
  else
    local snippet
    snippet=$(echo "$body" | head -c 200)
    echo "  [FAIL] $desc — HTTP $http_code"
    echo "         $snippet"
    FAIL=$((FAIL + 1))
    ERRORS="${ERRORS}\n  FAIL: $desc (HTTP $http_code)"
  fi
  echo "$body" > /tmp/_last_resp.json
}

G()  { call GET  "$1" "$2"; }
P()  { call POST "$1" "$2" "$3"; }
PU() { call PUT  "$1" "$2" "$3"; }
D()  { call DELETE "$1" "$2"; }

# 提取 JSON 字段
jf() { python3 -c "import sys,json; d=json.load(open('/tmp/_last_resp.json')); v=d${1}; print(v if v is not None else '')" 2>/dev/null; }

# 从分页响应提取第一条记录的 ID (支持两种格式)
first_id() {
  python3 -c "
import json
d=json.load(open('/tmp/_last_resp.json'))
# R<> wrapper: {code:0, data:{records:[...]}}
if 'data' in d and isinstance(d['data'], dict):
  recs = d['data'].get('records', [])
elif 'records' in d:
  recs = d['records']
else:
  recs = d.get('data', []) if isinstance(d.get('data'), list) else []
print(recs[0]['id'] if recs and isinstance(recs[0], dict) and 'id' in recs[0] else '')
" 2>/dev/null
}

echo "=============================================="
echo " 全面 API 测试 — $(date '+%Y-%m-%d %H:%M:%S')"
echo "=============================================="

# ========== 1. 认证 ==========
echo -e "\n--- 1. 认证 ---"
G "获取登录用户信息" "/auth/staff/info"

# ========== 2. 菜单 ==========
echo -e "\n--- 2. 菜单 ---"
G "菜单树(全部)" "/api/admin/menus/tree"
G "当前用户菜单" "/api/admin/menus/current"

# ========== 3. 门店管理 ==========
echo -e "\n--- 3. 门店管理 ---"
G "门店列表" "/api/admin/stores"
G "门店详情(id=1)" "/api/admin/stores/1"

# ========== 4. 员工管理 ==========
echo -e "\n--- 4. 员工管理 ---"
G "员工列表" "/api/admin/staff"
G "员工详情(id=1)" "/api/admin/staff/1/profile"
G "工作台概览" "/api/admin/staff/dashboard"

# ========== 5. 角色管理 ==========
echo -e "\n--- 5. 角色管理 ---"
G "角色列表" "/api/admin/roles"
ROLE_ID=$(G "角色列表(提取ID)" "/api/admin/roles" | python3 -c "
import sys,json
d=json.load(open('/tmp/_last_resp.json'))
recs = d.get('data', []) if isinstance(d.get('data'), list) else d.get('data',{}).get('records', d.get('records',[]))
print(recs[0]['id'] if recs and isinstance(recs[0], dict) and 'id' in recs[0] else '')
" 2>/dev/null)
[ -n "$ROLE_ID" ] && G "角色菜单(id=$ROLE_ID)" "/api/admin/roles/$ROLE_ID/menus"

# ========== 6. 字典管理 ==========
echo -e "\n--- 6. 字典管理 ---"
G "字典数据列表" "/api/admin/dict/data"
G "公共字典(支付方式)" "/api/public/dict/payment_method"

# ========== 7. 商品分类 ==========
echo -e "\n--- 7. 商品分类 ---"
G "分类树" "/api/admin/products/categories/tree"
G "单位换算列表(产品1)" "/api/admin/products/1/unit-conversions"

# ========== 8. 商品管理 ==========
echo -e "\n--- 8. 商品管理 ---"
G "商品列表" "/api/admin/products"
PROD_ID=$(G "商品列表(提取ID)" "/api/admin/products?pageNum=1&pageSize=5" | first_id)
[ -z "$PROD_ID" ] && PROD_ID=1
echo "    (使用商品ID=$PROD_ID)"
G "商品详情" "/api/admin/products/$PROD_ID"
G "价格规则列表" "/api/admin/products/$PROD_ID/price-rules"

# ========== 9. 套餐管理 ==========
echo -e "\n--- 9. 套餐管理 ---"
G "套餐列表" "/api/admin/product-packages"
PKG_ID=$(first_id)
[ -n "$PKG_ID" ] && G "套餐详情" "/api/admin/product-packages/$PKG_ID"

# ========== 10. 供应商管理 ==========
echo -e "\n--- 10. 供应商管理 ---"
G "供应商列表" "/api/admin/suppliers"
SUP_ID=$(first_id)
[ -z "$SUP_ID" ] && SUP_ID=1
[ -n "$SUP_ID" ] && G "供应商详情" "/api/admin/suppliers/$SUP_ID"

# ========== 11. 采购管理 ==========
echo -e "\n--- 11. 采购管理 ---"
G "采购单列表" "/api/admin/purchase/list"
PO_ID=$(first_id)
[ -n "$PO_ID" ] && G "采购单详情" "/api/admin/purchase/$PO_ID"

# ========== 12. 库存管理 ==========
echo -e "\n--- 12. 库存管理 ---"
G "库存列表" "/api/admin/inventory/list"
G "库存详情(store=1,product=$PROD_ID)" "/api/admin/inventory/detail?storeId=1&productId=$PROD_ID"
G "库存流水" "/api/admin/inventory/transactions"
G "调拨记录" "/api/admin/inventory/transfers"

# ========== 13. 库存预警 ==========
echo -e "\n--- 13. 库存预警 ---"
G "低库存预警" "/api/admin/inventory/warning/low-stock"
G "高库存预警" "/api/admin/inventory/warning/overstock"

# ========== 14. 客户管理 ==========
echo -e "\n--- 14. 客户管理 ---"
G "客户列表" "/api/admin/customer/list"
CUST_ID=$(first_id)
[ -z "$CUST_ID" ] && CUST_ID=1
echo "    (使用客户ID=$CUST_ID)"
G "客户详情" "/api/admin/customer/$CUST_ID"
G "钱包交易" "/api/admin/customer/wallet-transactions?customerId=$CUST_ID"
G "权益流水" "/api/admin/customer/$CUST_ID/benefit-logs"
G "客户优惠券" "/api/admin/customer/$CUST_ID/coupons"
G "VIP月度记录" "/api/admin/customer/vip-monthly-records?customerId=$CUST_ID"

# ========== 15. 客户跟进 ==========
echo -e "\n--- 15. 客户跟进 ---"
G "复购提醒" "/api/admin/customer/follow-up/repurchase-alerts"

# ========== 16. 订单管理 ==========
echo -e "\n--- 16. 订单管理 ---"
G "订单列表" "/api/admin/orders"
ORDER_ID=$(first_id)
[ -z "$ORDER_ID" ] && ORDER_ID=1
echo "    (使用订单ID=$ORDER_ID)"
G "订单详情" "/api/admin/orders/$ORDER_ID"

# ========== 17. 售后管理 ==========
echo -e "\n--- 17. 售后管理 ---"
G "售后列表" "/api/admin/after-sales"
AS_ID=$(first_id)
[ -n "$AS_ID" ] && G "售后详情" "/api/admin/after-sales/$AS_ID"
G "按订单查售后" "/api/admin/after-sales/order/$ORDER_ID"

# ========== 18. 赊销管理 ==========
echo -e "\n--- 18. 赊销管理 ---"
G "赊销账户列表" "/api/admin/credit/accounts"
G "赊销还款记录" "/api/admin/credit/repayments"
G "赊销统计" "/api/admin/credit/statistics"

# ========== 19. 优惠券模板 ==========
echo -e "\n--- 19. 优惠券模板 ---"
G "优惠券模板列表" "/api/admin/marketing/coupon-template"
CPN_ID=$(first_id)
[ -z "$CPN_ID" ] && CPN_ID=1
G "优惠券模板详情" "/api/admin/marketing/coupon-template/$CPN_ID"

# ========== 20. 充值规则 ==========
echo -e "\n--- 20. 充值规则 ---"
G "充值规则列表" "/api/admin/marketing/recharge-rule"

# ========== 21. VIP权益 ==========
echo -e "\n--- 21. VIP权益 ---"
G "VIP权益列表" "/api/admin/marketing/vip-rights"
G "VIP权益(level=1)" "/api/admin/marketing/vip-rights/level/1"

# ========== 22. 积分商品 ==========
echo -e "\n--- 22. 积分商品 ---"
G "积分商品列表" "/api/admin/points/goods"
PT_ID=$(first_id)
[ -n "$PT_ID" ] && echo "    (积分商品ID=$PT_ID)"

# ========== 23. 积分兑换单 ==========
echo -e "\n--- 23. 积分兑换单 ---"
G "积分兑换单列表" "/api/admin/points/orders"

# ========== 24. 报表 - 工作台 ==========
echo -e "\n--- 24. 报表 - 工作台 ---"
G "工作台今日数据" "/api/admin/report/dashboard/today"

# ========== 25. 报表 - 销售 ==========
echo -e "\n--- 25. 报表 - 销售 ---"
G "销售趋势" "/api/admin/report/sales/trend?period=day&startDate=2025-01-01&endDate=2026-12-31"

# ========== 26. 报表 - 客户 ==========
echo -e "\n--- 26. 报表 - 客户 ---"
G "客户排行" "/api/admin/report/customer/top?limit=10"
G "客户流失" "/api/admin/report/customer/churn?inactiveDays=90"

# ========== 27. 报表 - 库存 ==========
echo -e "\n--- 27. 报表 - 库存 ---"
G "库存价值" "/api/admin/report/inventory/value"
G "滞销分析" "/api/admin/report/inventory/slow-moving?days=90"

# ========== 28. 报表 - 财务 ==========
echo -e "\n--- 28. 报表 - 财务 ---"
G "赊销财务报表" "/api/admin/report/finance/credit"

# ========== 29. 绩效管理 ==========
echo -e "\n--- 29. 绩效管理 ---"
G "绩效规则" "/api/admin/performance/rule"
G "绩效目标查询" "/api/admin/performance/goal?month=2026-05"
G "工资单查询" "/api/admin/performance/bill?month=2026-05"

# ========== 30. 站内信 ==========
echo -e "\n--- 30. 站内信 ---"
G "通知列表" "/api/admin/notifications"
G "未读数" "/api/admin/notifications/unread-count"

# ========== 31. 操作日志 ==========
echo -e "\n--- 31. 操作日志 ---"
G "日志列表" "/api/admin/logs"

# ========== 32. 二维码 ==========
echo -e "\n--- 32. 二维码 ---"
G "二维码状态" "/api/admin/qr/status?storeId=1"

# ========== 33. 健康检查 ==========
echo -e "\n--- 33. 健康检查 ---"
G "Health" "/health"

# ========== 34. 写操作测试（创建→更新→删除） ==========
echo -e "\n--- 34. 写操作流程测试 ---"

# 创建字典
P "创建字典项" "/api/admin/dict/data" '{"typeCode":"test_type","label":"测试项","value":"test_val","sortOrder":99}'
DICT_ID=$(jf "['data']['id']" 2>/dev/null || jf "['data']" 2>/dev/null)
[ -z "$DICT_ID" ] && DICT_ID=$(first_id)
echo "    (字典ID=$DICT_ID)"

# 更新字典
[ -n "$DICT_ID" ] && PU "更新字典项" "/api/admin/dict/data" "{\"id\":$DICT_ID,\"typeCode\":\"test_type\",\"label\":\"测试项修改\",\"value\":\"test_val_mod\",\"sortOrder\":99}"

# 删除字典
[ -n "$DICT_ID" ] && D "删除字典项" "/api/admin/dict/data?id=$DICT_ID"

# 标记通知全部已读
PU "标记全部已读" "/api/admin/notifications/read-all" ""

# ========== 汇总 ==========
echo ""
echo "=============================================="
TOTAL=$((PASS + FAIL))
echo " 测试结果: $PASS / $TOTAL 通过"
if [ "$FAIL" -gt 0 ]; then
  echo ""
  echo " 失败详情:"
  echo -e "$ERRORS"
fi
echo "=============================================="
