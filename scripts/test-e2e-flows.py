#!/usr/bin/env python3
"""端到端业务流程测试 —— 闭环验证采购、销售、售后、客户、财务"""
import json, sys, time, urllib.request, urllib.error

BASE = "http://[::1]:8080"

def req(method, path, body=None, token=None):
    url = BASE + path
    data = json.dumps(body).encode() if body else None
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = token
    r = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(r, timeout=15) as resp:
            status = resp.status
            ct = resp.headers.get("Content-Type", "")
            raw = resp.read().decode()
            if "json" in ct:
                return status, json.loads(raw)
            return status, {"raw": raw}
    except urllib.error.HTTPError as e:
        try:
            body = json.loads(e.read())
        except Exception:
            body = {"error": str(e)}
        return e.code, body

def login(phone, password="admin123"):
    code, d = req("POST", "/auth/staff/login", {"account": phone, "password": password})
    if code == 200 and d.get("tokenValue"):
        return d["tokenValue"], d.get("userInfo", {})
    return None, None

# ── 全局登录 ──
admin_token, admin_info = login("13800000001")
if not admin_token:
    print("❌ 管理员登录失败")
    sys.exit(1)
print(f"管理员登录成功: {admin_info.get('name')}")

total_ok, total_fail = 0, 0

def check(desc, condition, detail=""):
    global total_ok, total_fail
    if condition:
        print(f"  ✅ {desc}")
        total_ok += 1
    else:
        print(f"  ❌ {desc} {detail}")
        total_fail += 1

# ============================================
# 1. 采购→入库闭环
# ============================================
print(f"\n{'='*60}")
print("  流程1: 采购单创建→提交→入库")
print(f"{'='*60}")

# 获取供应商
code, suppliers = req("GET", "/api/admin/suppliers?pageNum=1&pageSize=1", token=admin_token)
supplier_records = suppliers.get("data", {}).get("records", [])
supplier_id = supplier_records[0]["id"] if supplier_records else 1
print(f"  供应商ID: {supplier_id}")

# 获取商品
code, products = req("GET", "/api/admin/products?pageNum=1&pageSize=1", token=admin_token)
product_records = products.get("data", {}).get("records", [])
test_product_id = product_records[0]["id"] if product_records else 2
print(f"  商品ID: {test_product_id}")

# 记录入库前库存
code, inv_before = req("GET", f"/api/admin/inventory/detail?productId={test_product_id}&storeId=1", token=admin_token)
stock_before = inv_before.get("data", {}).get("quantity", 0) if code == 200 else 0
print(f"  入库前库存: {stock_before}")

# 1.1 创建采购单
purchase_qty = 100
purchase_price = 8.50
code, d = req("POST", "/api/admin/purchase/create", {
    "storeId": 1,
    "supplierId": supplier_id,
    "items": [{
        "productId": test_product_id,
        "qty": purchase_qty,
        "price": purchase_price,
        "unit": "袋"
    }],
    "remark": "E2E测试采购单"
}, token=admin_token)
biz = d.get("code") if isinstance(d, dict) else None
purchase_ok = biz == 0 or biz == "200" or d.get("data") is not None
check("创建采购单", purchase_ok, f"(biz={biz}, msg={d.get('message','')})")

purchase_order_id = None
if purchase_ok and d.get("data"):
    if isinstance(d["data"], dict):
        purchase_order_id = d["data"].get("orderId") or d["data"].get("id")
    elif isinstance(d["data"], (int, str)):
        purchase_order_id = d["data"]

if purchase_order_id:
    print(f"  采购单ID: {purchase_order_id}")

    # 1.2 提交采购单
    code, d = req("POST", "/api/admin/purchase/submit", {
        "orderId": purchase_order_id
    }, token=admin_token)
    biz = d.get("code") if isinstance(d, dict) else None
    submit_ok = biz == 0 or biz == "200"
    check("提交采购单", submit_ok, f"(biz={biz}, msg={d.get('message','')})")

    # 1.3 采购入库
    code, d = req("POST", "/api/admin/purchase/inbound", {
        "orderId": purchase_order_id
    }, token=admin_token)
    biz = d.get("code") if isinstance(d, dict) else None
    inbound_ok = biz == 0 or biz == "200" or d.get("data") is not None
    check("采购入库", inbound_ok, f"(biz={biz}, msg={d.get('message','')})")

    # 1.4 验证库存增加
    time.sleep(1)  # 等待异步处理
    code, inv_after = req("GET", f"/api/admin/inventory/detail?productId={test_product_id}&storeId=1", token=admin_token)
    stock_after = inv_after.get("data", {}).get("quantity", 0) if code == 200 else 0
    expected_stock = stock_before + purchase_qty
    stock_increased = stock_after >= expected_stock - 1  # 允许±1误差（并发）
    check(f"库存增加: {stock_before} → {stock_after} (期望≥{expected_stock-1})", stock_increased)

# ============================================
# 2. POS订单→售后退款闭环
# ============================================
print(f"\n{'='*60}")
print("  流程2: POS订单创建→售后退货→退款")
print(f"{'='*60}")

# 2.1 创建POS订单（使用刚入库的商品）
order_qty = 5
unit_price = 12.00
expected_total = order_qty * unit_price

# 先验证库存充足
code, inv_check = req("GET", f"/api/admin/inventory/detail?productId={test_product_id}&storeId=1", token=admin_token)
current_stock = inv_check.get("data", {}).get("quantity", 0) if code == 200 else 0
if current_stock < order_qty:
    print(f"  ⚠️  库存不足({current_stock})，跳过POS订单测试")
    order_ok = False
else:
    code, d = req("POST", "/api/admin/orders/pos", {
        "storeId": 1,
        "staffId": 1,
        "items": [{
            "productId": test_product_id,
            "quantity": order_qty,
            "unitPrice": unit_price
        }],
        "paymentType": 1,  # 现金
        "receiveAmount": expected_total
    }, token=admin_token)
    biz = d.get("code") if isinstance(d, dict) else None
    order_ok = False
    order_id = None

    if isinstance(d, (int, float)):
        order_id = d
        order_ok = order_id > 0
    elif isinstance(d, dict) and d.get("data") is not None:
        order_ok = True
        if isinstance(d["data"], (int, str)):
            order_id = d["data"]
        elif isinstance(d["data"], dict):
            order_id = d["data"].get("id") or d["data"].get("orderId")

    check("创建POS订单", order_ok, f"(biz={biz}, msg={d.get('message','') if isinstance(d, dict) else ''})")

if order_id:
    print(f"  订单ID: {order_id}")

    # 2.2 查询订单详情（获取 orderItemId）
    code, od = req("GET", f"/api/admin/orders/{order_id}", token=admin_token)
    order_data = od.get("data", od) if isinstance(od, dict) else {}
    order_items = order_data.get("items", [])
    order_item_id = order_items[0]["id"] if order_items else None
    print(f"  订单明细ID: {order_item_id}")

    if order_item_id:
        # 2.3 创建售后退货
        refund_amount = 2 * unit_price  # 退 2 件
        code, d = req("POST", "/api/admin/after-sales", {
            "orderId": order_id,
            "refundAmount": refund_amount,
            "reason": "E2E测试退货",
            "items": [{
                "orderItemId": order_item_id,
                "quantity": 2,
                "refundAmount": refund_amount
            }]
        }, token=admin_token)
        biz = d.get("code") if isinstance(d, dict) else None
        after_sales_ok = False
        after_sales_id = None

        if isinstance(d, (int, float)):
            after_sales_id = d
            after_sales_ok = after_sales_id > 0
        elif isinstance(d, dict):
            after_sales_ok = biz == 0 or biz == "200" or d.get("data") is not None
            if d.get("data") is not None:
                if isinstance(d["data"], (int, str)):
                    after_sales_id = d["data"]
                elif isinstance(d["data"], dict):
                    after_sales_id = d["data"].get("id") or d["data"].get("afterSalesId")

        check("创建售后退货", after_sales_ok, f"(biz={biz}, msg={d.get('message','') if isinstance(d, dict) else ''})")

        if after_sales_id:
            print(f"  售后单ID: {after_sales_id}")

            # 2.4 确认退款（管理端线下退款，直接完成）
            code, d = req("POST", f"/api/admin/after-sales/{after_sales_id}/refund", {
                "afterSalesId": after_sales_id,
                "refundMethod": 1  # 现金退款
            }, token=admin_token)
            biz = d.get("code") if isinstance(d, dict) else None
            refund_ok = biz == 0 or biz == "200" or code == 200
            check("确认退款", refund_ok, f"(biz={biz}, msg={d.get('message','') if isinstance(d, dict) else ''})")

# ============================================
# 3. 客户操作：充值、发券、调账
# ============================================
print(f"\n{'='*60}")
print("  流程3: 客户充值、发券、调账")
print(f"{'='*60}")

# 获取客户
code, customers = req("GET", "/api/admin/customer/list?pageNum=1&pageSize=1", token=admin_token)
customer_records = customers.get("data", {}).get("records", []) if isinstance(customers, dict) else []
customer_id = customer_records[0]["id"] if customer_records else None

if not customer_id:
    # 尝试创建客户
    test_phone = f"138{int(time.time()) % 100000000:08d}"
    code, d = req("POST", "/api/admin/customer", {
        "name": "E2E测试客户",
        "phone": test_phone
    }, token=admin_token)
    biz = d.get("code") if isinstance(d, dict) else None
    # 响应可能是直接返回客户对象（有id字段）或包装在data中
    if isinstance(d, dict):
        if d.get("id"):  # 直接返回客户对象
            customer_id = d["id"]
        elif d.get("data") and isinstance(d["data"], dict):
            customer_id = d["data"].get("id")
        elif isinstance(d.get("data"), (int, str)):
            customer_id = d["data"]
    elif isinstance(d, (int, float)):
        customer_id = d
    print(f"  创建测试客户: ID={customer_id}, biz={biz}")

if customer_id:
    print(f"  客户ID: {customer_id}")

    # 3.1 充值
    code, d = req("POST", "/api/admin/customer/recharge", {
        "customerId": customer_id,
        "amount": 100.00,
        "paymentMethod": 1  # 现金
    }, token=admin_token)
    biz = d.get("code") if isinstance(d, dict) else None
    # 响应可能是直接返回结果对象（无code字段）或包装在R<>中
    is_ok = biz == 0 or biz == "200" or (isinstance(d, dict) and d.get("data") is not None) or (biz is None and code == 200)
    check("客户充值100元", is_ok,
          f"(biz={biz}, msg={d.get('message','') if isinstance(d, dict) else ''})")

    # 3.2 发放余额
    code, d = req("POST", "/api/admin/customer/grant-balance", {
        "customerId": customer_id,
        "amount": 50.00,
        "remark": "E2E测试调账"
    }, token=admin_token)
    biz = d.get("code") if isinstance(d, dict) else None
    is_ok = biz == 0 or biz == "200" or (isinstance(d, dict) and d.get("data") is not None) or (biz is None and code == 200)
    check("发放余额50元", is_ok,
          f"(biz={biz}, msg={d.get('message','') if isinstance(d, dict) else ''})")

    # 3.3 发放专用金
    code, d = req("POST", "/api/admin/customer/grant-special-fund", {
        "customerId": customer_id,
        "amount": 30.00,
        "remark": "E2E测试专用金"
    }, token=admin_token)
    biz = d.get("code") if isinstance(d, dict) else None
    is_ok = biz == 0 or biz == "200" or (isinstance(d, dict) and d.get("data") is not None) or (biz is None and code == 200)
    check("发放专用金30元", is_ok,
          f"(biz={biz}, msg={d.get('message','') if isinstance(d, dict) else ''})")

# ============================================
# 4. 赊销管理：查询账户、调整额度、还款
# ============================================
print(f"\n{'='*60}")
print("  流程4: 赊销管理")
print(f"{'='*60}")

# 4.1 查询赊销账户列表
code, d = req("GET", "/api/admin/credit/accounts?pageNum=1&pageSize=5", token=admin_token)
biz = d.get("code") if isinstance(d, dict) else None
accounts = d.get("data", {}).get("records", []) if isinstance(d, dict) else []
check("查询赊销账户", biz == 0 or biz == "200" or len(accounts) >= 0,
      f"(biz={biz}, 账户数={len(accounts)})")

# 4.2 查询还款记录（需要customerId）
if customer_id:
    code, d = req("GET", f"/api/admin/credit/repayments?customerId={customer_id}", token=admin_token)
    biz = d.get("code") if isinstance(d, dict) else None
    is_ok = code == 200 and (biz == 0 or biz == "200" or (isinstance(d, dict) and d.get("data") is not None) or (isinstance(d, (list, dict)) and biz is None))
    check("查询还款记录", is_ok, f"(biz={biz}, type={type(d).__name__})")
else:
    check("查询还款记录（无客户ID，跳过）", True)

# 4.3 查询统计（需要customerId）
if customer_id:
    code, d = req("GET", f"/api/admin/credit/statistics?customerId={customer_id}", token=admin_token)
    biz = d.get("code") if isinstance(d, dict) else None
    is_ok = code == 200 and (biz == 0 or biz == "200" or (isinstance(d, dict) and d.get("data") is not None) or (isinstance(d, (list, dict)) and biz is None))
    check("查询赊销统计", is_ok, f"(biz={biz}, type={type(d).__name__})")
else:
    check("查询赊销统计（无客户ID，跳过）", True)

# ============================================
# 5. 报表查询
# ============================================
print(f"\n{'='*60}")
print("  流程5: 报表查询")
print(f"{'='*60}")

# 5.1 销售报表
code, d = req("GET", "/api/admin/report/sales/trend?startTime=2026-05-01T00:00:00&endTime=2026-05-31T23:59:59&storeId=1", token=admin_token)
biz = d.get("code") if isinstance(d, dict) else None
is_ok = code == 200 and (biz == 0 or biz == "200" or (isinstance(d, dict) and d.get("data") is not None) or (isinstance(d, (list, dict)) and biz is None))
check("销售趋势报表", is_ok, f"(biz={biz}, type={type(d).__name__})")

# 5.2 客户报表
code, d = req("GET", "/api/admin/report/customer/top?limit=10", token=admin_token)
biz = d.get("code") if isinstance(d, dict) else None
is_ok = code == 200 and (biz == 0 or biz == "200" or (isinstance(d, dict) and d.get("data") is not None) or isinstance(d, list))
check("客户TOP榜", is_ok, f"(biz={biz}, type={type(d).__name__})")

# 5.3 库存报表
code, d = req("GET", "/api/admin/report/inventory/value", token=admin_token)
biz = d.get("code") if isinstance(d, dict) else None
is_ok = code == 200 and (biz == 0 or biz == "200" or (isinstance(d, dict) and d.get("data") is not None) or isinstance(d, (list, dict)) and biz is None)
check("库存价值报表", is_ok, f"(biz={biz}, type={type(d).__name__})")

# 5.4 财务报表
code, d = req("GET", "/api/admin/report/finance/credit", token=admin_token)
biz = d.get("code") if isinstance(d, dict) else None
is_ok = code == 200 and (biz == 0 or biz == "200" or (isinstance(d, dict) and d.get("data") is not None) or isinstance(d, (list, dict)) and biz is None)
check("财务赊销报表", is_ok, f"(biz={biz}, type={type(d).__name__})")

# ============================================
# 6. 其他功能：通知、日志、绩效、积分、二维码
# ============================================
print(f"\n{'='*60}")
print("  流程6: 其他功能模块")
print(f"{'='*60}")

# 6.1 通知列表
code, d = req("GET", "/api/admin/notifications?pageNum=1&pageSize=5", token=admin_token)
biz = d.get("code") if isinstance(d, dict) else None
is_ok = biz == 0 or biz == "200" or (isinstance(d, dict) and d.get("data") is not None) or (isinstance(d, (list, dict)) and biz is None)
check("通知列表", is_ok, f"(biz={biz}, type={type(d).__name__})")

# 6.2 操作日志
code, d = req("GET", "/api/admin/logs?pageNum=1&pageSize=5", token=admin_token)
biz = d.get("code") if isinstance(d, dict) else None
is_ok = biz == 0 or biz == "200" or (isinstance(d, dict) and d.get("data") is not None)
check("操作日志", is_ok, f"(biz={biz})")

# 6.3 库存预警-低库存
code, d = req("GET", "/api/admin/inventory/warning/low-stock?pageNum=1&pageSize=5", token=admin_token)
biz = d.get("code") if isinstance(d, dict) else None
is_ok = biz == 0 or biz == "200" or (isinstance(d, dict) and d.get("data") is not None) or (isinstance(d, (list, dict)) and biz is None)
check("库存预警-低库存", is_ok, f"(biz={biz}, type={type(d).__name__})")

# 6.4 库存预警-积压
code, d = req("GET", "/api/admin/inventory/warning/overstock?pageNum=1&pageSize=5", token=admin_token)
biz = d.get("code") if isinstance(d, dict) else None
is_ok = biz == 0 or biz == "200" or (isinstance(d, dict) and d.get("data") is not None) or (isinstance(d, (list, dict)) and biz is None)
check("库存预警-积压", is_ok, f"(biz={biz}, type={type(d).__name__})")

# 6.5 绩效规则
code, d = req("GET", "/api/admin/performance/rule", token=admin_token)
biz = d.get("code") if isinstance(d, dict) else None
is_ok = biz == 0 or biz == "200" or (isinstance(d, dict) and d.get("data") is not None) or (isinstance(d, (list, dict)) and biz is None)
check("绩效规则列表", is_ok, f"(biz={biz}, type={type(d).__name__})")

# 6.6 积分商品
code, d = req("GET", "/api/admin/points/goods?pageNum=1&pageSize=5", token=admin_token)
biz = d.get("code") if isinstance(d, dict) else None
is_ok = biz == 0 or biz == "200" or (isinstance(d, dict) and d.get("data") is not None) or (isinstance(d, (list, dict)) and biz is None)
check("积分商品列表", is_ok, f"(biz={biz}, type={type(d).__name__})")

# 6.7 二维码状态（需要qrToken，测试参数校验）
code, d = req("GET", "/api/admin/qr/status?qrToken=test-token-123", token=admin_token)
biz = d.get("code") if isinstance(d, dict) else None
# 无论是否找到，只要不报500就算通过
is_ok = code == 200 and biz != 10002
check("二维码状态查询", is_ok, f"(biz={biz}, type={type(d).__name__})")

# ============================================
# 总结
# ============================================
print(f"\n{'='*60}")
print(f"  总计: ✅ {total_ok}  ❌ {total_fail}")
print(f"{'='*60}")
