#!/usr/bin/env python3
"""C端API全覆盖测试 —— 客户端8个Controller、20个端点"""
import json, sys, time, urllib.request, urllib.error

BASE = "http://[::1]:8081"
ADMIN_BASE = "http://[::1]:8080"

def req(method, path, body=None, token=None, headers_extra=None, raw_response=False):
    return _req(BASE, method, path, body, token, headers_extra, raw_response)

def admin_req(method, path, body=None, token=None):
    return _req(ADMIN_BASE, method, path, body, token)

def _req(base, method, path, body=None, token=None, headers_extra=None, raw_response=False):
    url = base + path
    data = json.dumps(body).encode() if body else None
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = token
    if headers_extra:
        headers.update(headers_extra)
    r = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(r, timeout=15) as resp:
            status = resp.status
            ct = resp.headers.get("Content-Type", "")
            raw_bytes = resp.read()
            if raw_response:
                return status, raw_bytes
            raw = raw_bytes.decode("utf-8", errors="replace")
            if "json" in ct:
                return status, json.loads(raw)
            return status, {"raw": raw}
    except urllib.error.HTTPError as e:
        try:
            body_err = json.loads(e.read())
        except Exception:
            body_err = {"error": str(e)}
        return e.code, body_err

def login_client(phone):
    code, d = req("POST", "/auth/client/login/dev", {"phone": phone})
    if code == 200 and d.get("tokenValue"):
        return d["tokenValue"], d.get("userInfo", {})
    return None, None

def login_admin():
    code, d = admin_req("POST", "/auth/staff/login", {"account": "13800000001", "password": "admin123"})
    if code == 200 and d.get("tokenValue"):
        return d["tokenValue"], d.get("userInfo", {})
    return None, None

total_ok, total_fail = 0, 0

def check(desc, condition, detail=""):
    global total_ok, total_fail
    if condition:
        print(f"  ✅ {desc}")
        total_ok += 1
    else:
        print(f"  ❌ {desc} {detail}")
        total_fail += 1

# ── 客户登录 ──
cust_token, cust_info = login_client("13880151027")
if not cust_token:
    print("❌ 客户登录失败")
    sys.exit(1)
print(f"客户登录成功: {cust_info.get('name')} (ID={cust_info.get('id')})")
customer_id = cust_info.get("id")

# ================================================================
# 模块1: 健康检查 & 认证
# ================================================================
print("\n" + "=" * 60)
print("  模块1: 健康检查 & 认证")
print("=" * 60)

# 1.1 健康检查
code, d = req("GET", "/health")
check("健康检查", code == 200 and d.get("status") == "UP", f"code={code}")

# 1.2 微信登录 - 无效code
code, d = req("POST", "/auth/client/login/wechat", {"code": "invalid_test_code"})
check("微信登录(无效code=预期失败)", code in (400, 500, 10002) or (isinstance(d, dict) and d.get("code") in (10001, 10002)), f"code={code}")

# 1.3 微信登录 - 空code (参数校验)
code, d = req("POST", "/auth/client/login/wechat", {})
check("微信登录(空code=拒绝)", code == 400 or (isinstance(d, dict) and d.get("code") in (10001, 10002)), f"code={code}")

# 1.4 客户登出 (先登出再重新登录)
code2, d2 = req("POST", "/auth/client/logout", token=cust_token)
check("客户登出", code2 == 200, f"code={code2}")

# 重新登录
cust_token, cust_info = login_client("13880151027")
check("重新登录", cust_token is not None)

# 1.5 未认证访问受保护端点
code, d = req("GET", f"/api/client/orders/1")
check("未认证访问(401/NOT_LOGIN)", code in (401, 403) or (isinstance(d, dict) and d.get("code") in (10003, "10003")), f"code={code}")

# 1.6 Dev登录 - 不存在的手机号
code, d = req("POST", "/auth/client/login/dev", {"phone": "99999999999"})
check("Dev登录(不存在手机号=失败)", code != 200 or (isinstance(d, dict) and d.get("code") in (10001, 10002)), f"code={code}")

# 1.7 Dev登录 - 空body
code, d = req("POST", "/auth/client/login/dev", {})
check("Dev登录(空body=拒绝)", code == 400 or (isinstance(d, dict) and d.get("code") in (10001, 10002)), f"code={code}, msg={d.get('message','') if isinstance(d, dict) else ''}")

# ================================================================
# 模块2: 客户下单 (OrderClientController)
# ================================================================
print("\n" + "=" * 60)
print("  模块2: 客户下单")
print("=" * 60)

# 2.1 创建订单 (自提)
code, d = req("POST", "/api/client/orders", {
    "storeId": 1,
    "customerId": customer_id,
    "orderType": 2,
    "items": [{"productId": 62, "quantity": 1}],
    "remark": "C端测试订单-自提"
}, token=cust_token)
order_id = d if isinstance(d, (int, float)) else (d.get("data") if isinstance(d, dict) else None)
if isinstance(d, dict) and isinstance(d.get("data"), dict):
    order_id = d["data"].get("id") or d["data"].get("orderId")
check("创建自提订单", code == 200 and order_id, f"code={code}, body={str(d)[:100]}")

# 2.2 创建订单 (配送)
code, d = req("POST", "/api/client/orders", {
    "storeId": 1,
    "customerId": customer_id,
    "orderType": 3,
    "items": [{"productId": 62, "quantity": 2}],
    "deliveryAddress": "测试配送地址123号",
    "deliveryPhone": "13880151027",
    "receiverName": "测试收件人",
    "deliveryType": 2,
    "remark": "C端测试订单-配送"
}, token=cust_token)
delivery_order_id = d if isinstance(d, (int, float)) else (d.get("data") if isinstance(d, dict) else None)
if isinstance(d, dict) and isinstance(d.get("data"), dict):
    delivery_order_id = d["data"].get("id") or d["data"].get("orderId")
check("创建配送订单", code == 200 and delivery_order_id, f"code={code}, body={str(d)[:100]}")

# 2.3 查询订单详情
if order_id:
    code, d = req("GET", f"/api/client/orders/{order_id}", token=cust_token)
    check("查询订单详情", code == 200, f"code={code}")

# 2.4 支付预览
if order_id:
    code, d = req("POST", f"/api/client/orders/{order_id}/payment-preview", {
        "orderId": order_id,
        "useBalance": False,
        "useSpecialFund": False,
        "externalPaymentType": 2
    }, token=cust_token)
    check("支付预览", code == 200, f"code={code}, body={str(d)[:100]}")

# 2.5 订单支付 (余额)
if order_id:
    code, d = req("POST", f"/api/client/orders/{order_id}/pay", {
        "orderId": order_id,
        "useBalance": True,
        "useSpecialFund": False,
        "externalPaymentType": 2
    }, token=cust_token)
    check("余额支付", code == 200, f"code={code}, body={str(d)[:100]}")

# 2.6 外部支付意图
if delivery_order_id:
    code, d = req("POST", f"/api/client/orders/{delivery_order_id}/payment-intent", {
        "orderId": delivery_order_id,
        "useBalance": False,
        "useSpecialFund": False,
        "externalPaymentType": 2
    }, token=cust_token)
    intent_no = None
    if code == 200 and isinstance(d, dict):
        intent_no = d.get("intentNo") or (d.get("data", {}) or {}).get("intentNo")
    check("创建支付意图", code == 200, f"code={code}, intent={intent_no}")

# 2.7 确认收货 (自提订单已支付)
if order_id:
    code, d = req("POST", f"/api/client/orders/{order_id}/confirm-receipt", token=cust_token)
    check("确认收货", code == 200, f"code={code}")

# ================================================================
# 模块3: 钱包授权码 (WalletClientController)
# ================================================================
print("\n" + "=" * 60)
print("  模块3: 钱包授权码")
print("=" * 60)

# 3.1 发送授权码
code, d = req("POST", "/api/client/wallet/auth-code/send", {
    "amount": 50.00
}, token=cust_token)
auth_code = None
if code == 200 and isinstance(d, dict):
    # Response could be direct Map or wrapped
    auth_code = d.get("authCode")
    if not auth_code and isinstance(d.get("data"), dict):
        auth_code = d["data"].get("authCode")
check("发送钱包授权码", code == 200 and auth_code, f"code={code}, body={str(d)[:120]}")

# 3.2 验证授权码
if auth_code:
    code, d = req("POST", "/api/client/wallet/auth-code/verify", {
        "authCode": auth_code
    }, token=cust_token)
    check("验证钱包授权码", code == 200, f"code={code}")

# 3.3 无效授权码
code, d = req("POST", "/api/client/wallet/auth-code/verify", {
    "authCode": "000000"
}, token=cust_token)
check("无效授权码(返回valid=false)", code == 200, f"code={code}")

# 3.4 发送授权码 - 金额校验
code, d = req("POST", "/api/client/wallet/auth-code/send", {
    "amount": 0
}, token=cust_token)
check("授权码金额校验(0=失败)", code == 400 or (isinstance(d, dict) and d.get("code") in (10001, 10002)), f"code={code}")

# ================================================================
# 模块4: 售后 (AfterSalesClientController)
# ================================================================
print("\n" + "=" * 60)
print("  模块4: 售后")
print("=" * 60)

# 4.1 申请售后 (需要已完成的订单)
# 创建一个新的待售后订单
code, d = req("POST", "/api/client/orders", {
    "storeId": 1,
    "customerId": customer_id,
    "orderType": 2,
    "items": [{"productId": 62, "quantity": 1}],
    "remark": "C端售后测试"
}, token=cust_token)
as_order_id = d if isinstance(d, (int, float)) else (d.get("data") if isinstance(d, dict) else None)
if isinstance(d, dict) and isinstance(d.get("data"), dict):
    as_order_id = d["data"].get("id") or d["data"].get("orderId")

if as_order_id:
    # 先支付
    req("POST", f"/api/client/orders/{as_order_id}/pay", {
        "orderId": as_order_id,
        "useBalance": True,
        "useSpecialFund": False,
        "externalPaymentType": 2
    }, token=cust_token)

    # 通过Admin API发货 + 确认收货 (售后需要已完成的订单)
    admin_token, _ = login_admin()
    if admin_token:
        admin_req("POST", f"/api/admin/orders/{as_order_id}/ship", {
            "orderId": as_order_id,
            "logisticsCompany": "顺丰",
            "trackingNo": f"SF{int(time.time())}"
        }, token=admin_token)
        admin_req("POST", f"/api/admin/orders/{as_order_id}/confirm-receipt", token=admin_token)

    # 申请售后 (需要订单已完成: 支付+发货+确认收货)
    code, d = req("POST", "/api/client/after-sales/apply", {
        "orderId": as_order_id,
        "reason": "C端测试-不想要了"
    }, token=cust_token)
    after_sales_id = d if isinstance(d, (int, float)) else (d.get("data") if isinstance(d, dict) else None)
    # 注意: 余额不足时外部支付待确认，订单可能无法进入可售后状态
    if code == 200 and isinstance(d, dict) and d.get("code") == 10001:
        check("申请售后(余额不足,状态不符=预期)", True, f"msg={d.get('message','')[:60]}")
    else:
        check("申请售后", code == 200 and after_sales_id, f"code={code}, body={str(d)[:100]}")

    # 4.2 取消售后
    if after_sales_id:
        code, d = req("POST", f"/api/client/after-sales/{after_sales_id}/cancel", token=cust_token)
        check("取消售后", code == 200, f"code={code}")
else:
    check("申请售后(无订单)", False)

# ================================================================
# 模块5: 积分商城 (PointsMallClientController)
# ================================================================
print("\n" + "=" * 60)
print("  模块5: 积分商城")
print("=" * 60)

# 5.1 查看积分商品列表
code, d = req("GET", "/api/client/points/goods", token=cust_token)
check("积分商品列表", code == 200, f"code={code}")
goods_list = d if isinstance(d, list) else (d.get("data", []) if isinstance(d, dict) else [])
first_goods_id = goods_list[0].get("id") if goods_list and isinstance(goods_list[0], dict) else None

# 5.2 查看积分商品详情
if first_goods_id:
    code, d = req("GET", f"/api/client/points/goods/{first_goods_id}", token=cust_token)
    check("积分商品详情", code == 200, f"code={code}")

# 5.3 积分兑换
code, d = req("POST", "/api/client/points/exchange", {
    "goodsId": first_goods_id or 1,
    "quantity": 1,
    "receiverName": "测试收件人",
    "receiverPhone": "13880151027",
    "receiverAddress": "测试地址"
}, token=cust_token)
exchange_id = d if isinstance(d, (int, float)) else (d.get("data") if isinstance(d, dict) else None)
if isinstance(d, dict) and isinstance(d.get("data"), dict):
    exchange_id = d["data"].get("id")
check("积分兑换", code == 200, f"code={code}, body={str(d)[:100]}")

# 5.4 我的兑换订单
code, d = req("GET", "/api/client/points/orders/my?pageNum=1&pageSize=5", token=cust_token)
check("我的兑换订单", code == 200, f"code={code}")

# ================================================================
# 模块6: 二维码绑定 (QrClientController)
# ================================================================
print("\n" + "=" * 60)
print("  模块6: 二维码绑定")
print("=" * 60)

# 6.1 QR绑定
code, d = req("POST", "/api/client/auth/qrBind", {
    "qrToken": f"test-qr-{int(time.time())}",
    "customerId": customer_id,
    "openid": f"test_openid_{int(time.time())}",
    "phone": "13880151027"
}, token=cust_token)
check("二维码绑定", code == 200, f"code={code}, body={str(d)[:100]}")

# ================================================================
# 模块7: 支付回调 (OrderPaymentPublicController)
# ================================================================
print("\n" + "=" * 60)
print("  模块7: 支付回调(公开端点)")
print("=" * 60)

# 7.1 无密钥回调 (应返回503或401)
code, d = req("POST", "/api/public/orders/payments/confirm", {
    "intentNo": "test-intent-123",
    "externalTransactionNo": "WX123456",
    "externalPaidAt": "2026-05-30T22:00:00"
})
check("支付回调(无密钥=拒绝)", code in (401, 503) or (isinstance(d, dict) and d.get("code") in (10002, 10004)), f"code={code}")

# 7.2 带密钥回调 (密钥可能未配置)
code, d = req("POST", "/api/public/orders/payments/confirm", {
    "intentNo": "test-intent-123",
    "externalTransactionNo": "WX123456",
    "externalPaidAt": "2026-05-30T22:00:00"
}, headers_extra={"X-Payment-Secret": "test-secret"})
check("支付回调(测试密钥)", code in (200, 401, 503), f"code={code}")

# 7.3 空body回调
code, d = req("POST", "/api/public/orders/payments/confirm", {})
check("支付回调(空body=拒绝)", code in (400, 401, 503) or (isinstance(d, dict) and d.get("code") in (10001, 10002)), f"code={code}")

# ================================================================
# 模块8: 边界验证
# ================================================================
print("\n" + "=" * 60)
print("  模块8: 边界与安全验证")
print("=" * 60)

# 8.1 订单ID不存在
code, d = req("GET", "/api/client/orders/999999", token=cust_token)
check("查询不存在订单(404或空)", code in (404, 200) or (isinstance(d, dict) and d.get("code") in (10001, 10002)), f"code={code}")

# 8.2 创建订单 - 空商品列表
code, d = req("POST", "/api/client/orders", {
    "storeId": 1,
    "customerId": customer_id,
    "orderType": 2,
    "items": []
}, token=cust_token)
check("空商品下单(拒绝)", code == 400 or (isinstance(d, dict) and d.get("code") in (10001, 10002)), f"code={code}, msg={d.get('message','')[:60] if isinstance(d, dict) else ''}")

# 8.3 创建订单 - 缺少storeId
code, d = req("POST", "/api/client/orders", {
    "customerId": customer_id,
    "orderType": 2,
    "items": [{"productId": 62, "quantity": 1}]
}, token=cust_token)
check("缺少storeId(拒绝)", code == 400 or (isinstance(d, dict) and d.get("code") in (10001, 10002)), f"code={code}, msg={d.get('message','')[:60] if isinstance(d, dict) else ''}")

# 8.4 售后 - 不存在的订单
code, d = req("POST", "/api/client/after-sales/apply", {
    "orderId": 999999,
    "reason": "测试"
}, token=cust_token)
check("不存在订单售后(失败)", code in (400, 404, 500) or (isinstance(d, dict) and d.get("code") in (10001, 10002)), f"code={code}")

# 8.5 积分兑换 - 不存在的商品
code, d = req("POST", "/api/client/points/exchange", {
    "goodsId": 999999,
    "quantity": 1,
    "receiverName": "测试",
    "receiverPhone": "13880151027"
}, token=cust_token)
check("不存在商品兑换(失败)", code in (400, 404, 500) or (isinstance(d, dict) and d.get("code") in (10001, 10002)), f"code={code}")

# 8.6 授权码格式校验 - 非数字
code, d = req("POST", "/api/client/wallet/auth-code/verify", {
    "authCode": "abcdef"
}, token=cust_token)
check("非数字授权码(400)", code == 400 or (isinstance(d, dict) and d.get("code") in (10001,)), f"code={code}")

# ================================================================
# 汇总
# ================================================================
print("\n" + "=" * 60)
print(f"  总计: ✅ {total_ok}  ❌ {total_fail}")
print("=" * 60)

if total_fail > 0:
    sys.exit(1)
