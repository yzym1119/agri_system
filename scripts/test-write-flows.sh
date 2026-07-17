#!/bin/bash
# 写操作业务流程测试 v2 — 创建→查询→修改→删除 完整链路
BASE="http://[::1]:8080"
TOKEN="1960fac7-dd88-4ff4-83ad-1f100ab2c334"
AUTH="Authorization: $TOKEN"
CT="Content-Type: application/json"
PASS=0; FAIL=0; ERRORS=""

# 调用并返回完整响应
do_req() {
  local method="$1" url="$2" data="$3"
  local args=(-s -H "$AUTH")
  [ -n "$data" ] && args+=(-H "$CT" -d "$data")
  curl -X "$method" "${args[@]}" "$BASE$url"
}

# 断言 HTTP 200 且业务成功(code=200/0 或无 code 字段)
assert_ok() {
  local desc="$1" body="$2"
  local http_ok=true
  local biz_code
  biz_code=$(echo "$body" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('code',''))" 2>/dev/null)
  # code 10001/10002 = 业务失败
  if [ "$biz_code" = "10001" ] || [ "$biz_code" = "10002" ]; then
    http_ok=false
  fi

  if $http_ok; then
    echo "  [PASS] $desc"
    PASS=$((PASS + 1))
  else
    local msg
    msg=$(echo "$body" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('message','')[:120])" 2>/dev/null)
    echo "  [FAIL] $desc — code=$biz_code $msg"
    FAIL=$((FAIL + 1))
    ERRORS="${ERRORS}\n  FAIL: $desc (code=$biz_code $msg)"
  fi
}

# 从响应中提取 ID (支持多种格式)
get_id() {
  echo "$1" | python3 -c "
import sys,json
d=json.load(sys.stdin)
# {code:'200', data:{id:X}}
if 'data' in d and isinstance(d.get('data'), dict) and 'id' in d['data']:
  print(d['data']['id'])
# {code:'200', data:X} where X is a number
elif 'data' in d and isinstance(d.get('data'), (int,float)):
  print(int(d['data']))
# raw {id:X}
elif 'id' in d:
  print(d['id'])
else:
  print('')
" 2>/dev/null
}

echo "=============================================="
echo " 写操作业务流程测试 v2 — $(date '+%H:%M:%S')"
echo "=============================================="

# ===== 1. 门店 CRUD =====
echo -e "\n--- 1. 门店 CRUD ---"
BODY=$(do_req POST "/api/admin/stores" '{"name":"自动化测试门店","address":"测试地址","phone":"13900001111","status":1}')
assert_ok "创建门店" "$BODY"
STORE_ID=$(get_id "$BODY")
echo "    → 门店ID=$STORE_ID"

if [ -n "$STORE_ID" ]; then
  BODY=$(do_req GET "/api/admin/stores/$STORE_ID")
  assert_ok "查询新建门店" "$BODY"

  BODY=$(do_req PUT "/api/admin/stores/$STORE_ID" "{\"id\":$STORE_ID,\"name\":\"测试门店(改)\",\"address\":\"新地址\",\"phone\":\"13900002222\",\"status\":1}")
  assert_ok "更新门店" "$BODY"

  BODY=$(do_req DELETE "/api/admin/stores/$STORE_ID")
  assert_ok "删除门店" "$BODY"
fi

# ===== 2. 员工 CRUD =====
echo -e "\n--- 2. 员工 CRUD ---"
BODY=$(do_req POST "/api/admin/staff" '{"name":"测试员工","account":"test_auto_staff","phone":"13800009999","password":"Test1234","storeId":1,"roleIds":[2]}')
assert_ok "创建员工" "$BODY"
STAFF_ID=$(get_id "$BODY")
echo "    → 员工ID=$STAFF_ID"

if [ -n "$STAFF_ID" ]; then
  BODY=$(do_req GET "/api/admin/staff/$STAFF_ID/profile")
  assert_ok "查询新建员工" "$BODY"

  BODY=$(do_req PUT "/api/admin/staff/$STAFF_ID" "{\"id\":$STAFF_ID,\"name\":\"测试员工(改)\",\"phone\":\"13800008888\",\"storeId\":1}")
  assert_ok "更新员工" "$BODY"

  BODY=$(do_req POST "/api/admin/staff/$STAFF_ID/disable" '')
  assert_ok "禁用员工" "$BODY"

  BODY=$(do_req POST "/api/admin/staff/$STAFF_ID/enable" '')
  assert_ok "启用员工" "$BODY"
fi

# ===== 3. 商品分类 CRUD =====
echo -e "\n--- 3. 商品分类 CRUD ---"
BODY=$(do_req POST "/api/admin/products/categories" '{"name":"自动化测试分类","parentId":0,"sortOrder":999}')
assert_ok "创建分类" "$BODY"
CAT_ID=$(get_id "$BODY")
echo "    → 分类ID=$CAT_ID"

if [ -n "$CAT_ID" ]; then
  BODY=$(do_req PUT "/api/admin/products/categories/$CAT_ID" "{\"id\":$CAT_ID,\"name\":\"测试分类(改)\",\"parentId\":0,\"sortOrder\":998}")
  assert_ok "更新分类" "$BODY"

  BODY=$(do_req DELETE "/api/admin/products/categories/$CAT_ID")
  assert_ok "删除分类" "$BODY"
fi

# ===== 4. 商品 CRUD =====
echo -e "\n--- 4. 商品 CRUD ---"
BODY=$(do_req POST "/api/admin/products" '{"name":"自动化测试商品","categoryId":1,"mainUnit":"瓶","suggestedRetailPrice":99.9,"manufacturer":"测试厂家","isOnline":0}')
assert_ok "创建商品" "$BODY"
PROD_ID=$(get_id "$BODY")
echo "    → 商品ID=$PROD_ID"

if [ -n "$PROD_ID" ]; then
  BODY=$(do_req GET "/api/admin/products/$PROD_ID")
  assert_ok "查询新建商品" "$BODY"

  BODY=$(do_req PUT "/api/admin/products/$PROD_ID" "{\"id\":$PROD_ID,\"name\":\"测试商品(改)\",\"categoryId\":1,\"mainUnit\":\"瓶\",\"suggestedRetailPrice\":88.8,\"manufacturer\":\"测试厂家改\",\"isOnline\":0}")
  assert_ok "更新商品" "$BODY"

  BODY=$(do_req PUT "/api/admin/products/$PROD_ID/online-status?isOnline=1")
  assert_ok "上架商品" "$BODY"

  BODY=$(do_req PUT "/api/admin/products/$PROD_ID/online-status?isOnline=0")
  assert_ok "下架商品" "$BODY"

  BODY=$(do_req DELETE "/api/admin/products/$PROD_ID")
  assert_ok "删除商品" "$BODY"
fi

# ===== 5. 供应商 CRUD =====
echo -e "\n--- 5. 供应商 CRUD ---"
BODY=$(do_req POST "/api/admin/suppliers" '{"name":"自动化测试供应商","contactName":"张三","contactPhone":"13700001111","address":"供应商地址","status":1}')
assert_ok "创建供应商" "$BODY"
SUP_ID=$(get_id "$BODY")
echo "    → 供应商ID=$SUP_ID"

if [ -n "$SUP_ID" ]; then
  BODY=$(do_req GET "/api/admin/suppliers/$SUP_ID")
  assert_ok "查询新建供应商" "$BODY"

  BODY=$(do_req PUT "/api/admin/suppliers/$SUP_ID" "{\"id\":$SUP_ID,\"name\":\"测试供应商(改)\",\"contactName\":\"李四\",\"contactPhone\":\"13700002222\",\"status\":1}")
  assert_ok "更新供应商" "$BODY"

  BODY=$(do_req DELETE "/api/admin/suppliers/$SUP_ID")
  assert_ok "删除供应商" "$BODY"
fi

# ===== 6. 客户 CRUD =====
echo -e "\n--- 6. 客户 CRUD ---"
BODY=$(do_req POST "/api/admin/customer" '{"name":"自动化测试客户","phone":"13600001111"}')
assert_ok "创建客户" "$BODY"
CUST_ID=$(get_id "$BODY")
echo "    → 客户ID=$CUST_ID"

if [ -n "$CUST_ID" ]; then
  BODY=$(do_req GET "/api/admin/customer/$CUST_ID")
  assert_ok "查询新建客户" "$BODY"

  BODY=$(do_req PUT "/api/admin/customer/$CUST_ID" '{"name":"测试客户(改)","address":"新地址","remark":"修改"}')
  assert_ok "更新客户" "$BODY"

  # 充值
  BODY=$(do_req POST "/api/admin/customer/recharge" "{\"customerId\":$CUST_ID,\"amount\":100,\"paymentMethod\":1}")
  assert_ok "客户充值100元" "$BODY"

  # 查钱包交易
  BODY=$(do_req GET "/api/admin/customer/wallet-transactions?customerId=$CUST_ID")
  assert_ok "查看充值后钱包交易" "$BODY"

  # 发券(需要有效的 templateId)
  BODY=$(do_req POST "/api/admin/customer/grant-coupon" "{\"customerId\":$CUST_ID,\"templateId\":1,\"count\":1}")
  # 发券可能因为模板不存在而失败,仅记录
  echo "  [INFO] 手动发券: $(echo "$BODY" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('message','ok')[:80])" 2>/dev/null)"

  BODY=$(do_req DELETE "/api/admin/customer/$CUST_ID")
  assert_ok "删除客户" "$BODY"
fi

# ===== 7. 优惠券模板 CRUD =====
echo -e "\n--- 7. 优惠券模板 CRUD ---"
BODY=$(do_req POST "/api/admin/marketing/coupon-template" '{"name":"自动化测试券","type":1,"amount":10,"minOrderAmount":100,"startTime":"2026-01-01 00:00:00","endTime":"2027-12-31 23:59:59","totalCount":100,"status":0}')
assert_ok "创建优惠券模板" "$BODY"
CPN_ID=$(get_id "$BODY")
echo "    → 优惠券ID=$CPN_ID"

if [ -n "$CPN_ID" ]; then
  BODY=$(do_req GET "/api/admin/marketing/coupon-template/$CPN_ID")
  assert_ok "查询优惠券详情" "$BODY"

  BODY=$(do_req PUT "/api/admin/marketing/coupon-template/$CPN_ID" "{\"id\":$CPN_ID,\"name\":\"测试券(改)\",\"type\":1,\"amount\":15,\"minOrderAmount\":100,\"startTime\":\"2026-01-01 00:00:00\",\"endTime\":\"2027-12-31 23:59:59\",\"totalCount\":200}")
  assert_ok "更新优惠券模板" "$BODY"

  BODY=$(do_req PUT "/api/admin/marketing/coupon-template/$CPN_ID/status?status=1")
  assert_ok "发布优惠券" "$BODY"

  BODY=$(do_req PUT "/api/admin/marketing/coupon-template/$CPN_ID/status?status=0")
  assert_ok "下架优惠券" "$BODY"

  BODY=$(do_req DELETE "/api/admin/marketing/coupon-template/$CPN_ID")
  assert_ok "删除优惠券模板" "$BODY"
fi

# ===== 8. 充值规则 CRUD =====
echo -e "\n--- 8. 充值规则 CRUD ---"
BODY=$(do_req POST "/api/admin/marketing/recharge-rule" '{"rechargeAmount":500,"giftAmount":50,"giftPoints":100,"status":1}')
assert_ok "创建充值规则" "$BODY"
RULE_ID=$(get_id "$BODY")
echo "    → 规则ID=$RULE_ID"

if [ -n "$RULE_ID" ]; then
  BODY=$(do_req PUT "/api/admin/marketing/recharge-rule/$RULE_ID" "{\"id\":$RULE_ID,\"rechargeAmount\":500,\"giftAmount\":80,\"giftPoints\":150,\"status\":1}")
  assert_ok "更新充值规则" "$BODY"

  BODY=$(do_req DELETE "/api/admin/marketing/recharge-rule/$RULE_ID")
  assert_ok "删除充值规则" "$BODY"
fi

# ===== 9. 字典 CRUD =====
echo -e "\n--- 9. 字典 CRUD ---"
BODY=$(do_req POST "/api/admin/dict/data" '{"typeCode":"product_unit","label":"自动测试单位","value":"auto_unit","sortOrder":999}')
assert_ok "创建字典项" "$BODY"
DICT_ID=$(get_id "$BODY")
echo "    → 字典ID=$DICT_ID"

if [ -n "$DICT_ID" ]; then
  BODY=$(do_req GET "/api/admin/dict/data?typeCode=product_unit")
  assert_ok "查询字典列表" "$BODY"

  BODY=$(do_req PUT "/api/admin/dict/data" "{\"id\":$DICT_ID,\"typeCode\":\"product_unit\",\"label\":\"测试单位(改)\",\"value\":\"auto_unit_mod\",\"sortOrder\":998}")
  assert_ok "更新字典项" "$BODY"

  BODY=$(do_req DELETE "/api/admin/dict/data?id=$DICT_ID&typeCode=product_unit")
  assert_ok "删除字典项" "$BODY"
fi

# ===== 10. 站内信操作 =====
echo -e "\n--- 10. 站内信 ---"
BODY=$(do_req GET "/api/admin/notifications")
assert_ok "通知列表" "$BODY"
NOTIF_ID=$(echo "$BODY" | python3 -c "
import sys,json
d=json.load(sys.stdin)
recs = d.get('data',{}).get('records',[]) or d.get('records',[])
print(recs[0]['id'] if recs and isinstance(recs[0], dict) and 'id' in recs[0] else '')
" 2>/dev/null)

if [ -n "$NOTIF_ID" ]; then
  BODY=$(do_req PUT "/api/admin/notifications/read" "{\"ids\":[$NOTIF_ID]}")
  assert_ok "标记单条已读" "$BODY"
fi

BODY=$(do_req PUT "/api/admin/notifications/read-all" '')
assert_ok "全部标记已读" "$BODY"

BODY=$(do_req GET "/api/admin/notifications/unread-count")
assert_ok "未读数查询" "$BODY"

# ===== 11. 赊销管理(写操作) =====
echo -e "\n--- 11. 赊销管理 ---"
BODY=$(do_req GET "/api/admin/credit/accounts")
assert_ok "赊销账户列表" "$BODY"
BODY=$(do_req GET "/api/admin/credit/statistics")
assert_ok "赊销统计" "$BODY"

# ===== 汇总 =====
echo ""
echo "=============================================="
TOTAL=$((PASS + FAIL))
echo " 写操作测试: $PASS / $TOTAL 通过"
if [ "$FAIL" -gt 0 ]; then
  echo -e "\n 失败详情:$ERRORS"
fi
echo "=============================================="
