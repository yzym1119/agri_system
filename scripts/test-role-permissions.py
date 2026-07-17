#!/usr/bin/env python3
"""角色权限隔离测试 —— 验证不同角色看到的菜单和可调用的 API"""
import json, sys, urllib.request, urllib.error

BASE = "http://[::1]:8080"

def req(method, path, body=None, token=None):
    url = BASE + path
    data = json.dumps(body).encode() if body else None
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = token
    r = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(r, timeout=10) as resp:
            status = resp.status
            ct = resp.headers.get("Content-Type", "")
            raw = resp.read().decode()
            if "json" in ct:
                d = json.loads(raw)
                # 登录响应: 直接返回 token 对象
                if isinstance(d, dict) and d.get("tokenValue"):
                    return status, d
                # 业务响应: code=0 表示成功
                return status, d
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

def test_staff(name, phone, expected_role, forbidden_endpoints, allowed_endpoints):
    print(f"\n{'='*60}")
    print(f"  {name} ({phone}) — 期望角色: {expected_role}")
    print(f"{'='*60}")

    token, info = login(phone)
    if not token:
        print(f"  ❌ 登录失败")
        return 0, 1

    role_codes = info.get("extra", {}).get("roleCodes", [])
    perms = info.get("permissions", [])
    print(f"  登录成功: role={role_codes}, 权限数={len(perms)}")

    if expected_role not in role_codes:
        print(f"  ❌ 角色不匹配: 期望 {expected_role}, 实际 {role_codes}")
        return 0, 1

    # 获取菜单
    code, menus = req("GET", "/api/admin/menus/current", token=token)
    if code == 200 and menus.get("data"):
        def count_menu(items):
            n = 0
            for m in items:
                n += 1
                if m.get("children"):
                    n += count_menu(m["children"])
            return n
        menu_count = count_menu(menus["data"])
        print(f"  菜单项数: {menu_count}")

    ok, fail = 0, 0

    # 测试允许的端点
    print(f"\n  ── 允许的端点 ({len(allowed_endpoints)}) ──")
    for desc, method, path in allowed_endpoints:
        code, d = req(method, path, token=token)
        biz_code = d.get("code") if isinstance(d, dict) else None
        # 无权限/未登录/重复请求
        denied = biz_code in (10003, 10004, 10005, 10001) or code in (401, 403)
        # 成功: code=0 或 "200", 或者有 records, 或者 code 字段不存在
        is_ok = (
            code == 200 and
            (biz_code == 0 or biz_code == "200" or biz_code is None or d.get("records") is not None)
        )
        if denied:
            print(f"    ❌ {desc}: 被拒绝 (biz={biz_code})")
            fail += 1
        elif biz_code == 10002:
            print(f"    ⚠️  {desc}: 后端 500 (非权限问题)")
            ok += 1  # 系统繁忙不算权限问题
        elif is_ok:
            print(f"    ✅ {desc}")
            ok += 1
        else:
            print(f"    ⚠️  {desc}: 响应异常 (biz={biz_code})")
            fail += 1

    # 测试禁止的端点
    print(f"\n  ── 禁止的端点 ({len(forbidden_endpoints)}) ──")
    for desc, method, path in forbidden_endpoints:
        code, d = req(method, path, token=token)
        biz_code = d.get("code") if isinstance(d, dict) else None
        # 10003=未登录, 10004=无权限(FORBIDDEN), 10005=重复请求
        denied = biz_code in (10003, 10004, 10005, 10001) or code in (401, 403)
        if denied:
            print(f"    ✅ {desc}: 正确拒绝 (biz={biz_code})")
            ok += 1
        elif biz_code == 10002:
            print(f"    ⚠️  {desc}: 后端 500 (无法判断权限)")
            fail += 1
        else:
            print(f"    ❌ {desc}: 未拒绝! (biz={biz_code})")
            fail += 1

    return ok, fail


total_ok, total_fail = 0, 0

# ── 店长测试 ──
ok, fail = test_staff("张店长", "13900000101", "STORE_MANAGER",
    forbidden_endpoints=[
        ("禁用员工", "POST", "/api/admin/staff/2/disable"),
        ("保存字典", "POST", "/api/admin/dict/data"),
        ("创建营销规则", "POST", "/api/admin/marketing/recharge-rule"),
        ("修改商品", "PUT", "/api/admin/products/1"),
        ("赊销还款", "POST", "/api/admin/credit/repay"),
    ],
    allowed_endpoints=[
        ("查看工作台", "GET", "/api/admin/report/dashboard/today"),
        ("查看订单列表", "GET", "/api/admin/orders?pageNum=1&pageSize=5"),
        ("查看客户列表", "GET", "/api/admin/customer/list?pageNum=1&pageSize=5"),
        ("查看商品列表", "GET", "/api/admin/products?pageNum=1&pageSize=5"),
        ("查看库存", "GET", "/api/admin/inventory/list?pageNum=1&pageSize=5"),
    ],
)
total_ok += ok
total_fail += fail

# ── 收银员测试 ──
ok, fail = test_staff("李收银", "13900000102", "CASHIER",
    forbidden_endpoints=[
        ("查看商品列表", "GET", "/api/admin/products?pageNum=1&pageSize=5"),
        ("查看库存", "GET", "/api/admin/inventory/list?pageNum=1&pageSize=5"),
        ("查看报表", "GET", "/api/admin/report/dashboard/today"),
        ("创建员工", "POST", "/api/admin/staff"),
        ("赊销管理", "GET", "/api/admin/credit/accounts?pageNum=1&pageSize=5"),
        ("售后列表", "GET", "/api/admin/after-sales"),
    ],
    allowed_endpoints=[
        ("查看订单列表", "GET", "/api/admin/orders?pageNum=1&pageSize=5"),
        ("查看客户列表", "GET", "/api/admin/customer/list?pageNum=1&pageSize=5"),
    ],
)
total_ok += ok
total_fail += fail

# ── 仓管测试 ──
ok, fail = test_staff("王仓管", "13900000103", "WAREHOUSE",
    forbidden_endpoints=[
        ("查看订单列表", "GET", "/api/admin/orders?pageNum=1&pageSize=5"),
        ("查看客户列表", "GET", "/api/admin/customer/list?pageNum=1&pageSize=5"),
        ("查看报表", "GET", "/api/admin/report/dashboard/today"),
        ("赊销管理", "GET", "/api/admin/credit/accounts?pageNum=1&pageSize=5"),
        ("创建员工", "POST", "/api/admin/staff"),
    ],
    allowed_endpoints=[
        ("查看商品列表", "GET", "/api/admin/products?pageNum=1&pageSize=5"),
        ("查看库存", "GET", "/api/admin/inventory/list?pageNum=1&pageSize=5"),
        ("查看采购单", "GET", "/api/admin/purchase/list?pageNum=1&pageSize=5"),
    ],
)
total_ok += ok
total_fail += fail

# ── 财务测试 ──
ok, fail = test_staff("赵财务", "13900000104", "FINANCE",
    forbidden_endpoints=[
        ("查看商品列表", "GET", "/api/admin/products?pageNum=1&pageSize=5"),
        ("查看库存", "GET", "/api/admin/inventory/list?pageNum=1&pageSize=5"),
        ("查看订单列表", "GET", "/api/admin/orders?pageNum=1&pageSize=5"),
        ("创建员工", "POST", "/api/admin/staff"),
        ("采购单列表", "GET", "/api/admin/purchase/list?pageNum=1&pageSize=5"),
    ],
    allowed_endpoints=[
        ("查看报表", "GET", "/api/admin/report/dashboard/today"),
        ("查看客户列表", "GET", "/api/admin/customer/list?pageNum=1&pageSize=5"),
        ("赊销账户列表", "GET", "/api/admin/credit/accounts?pageNum=1&pageSize=5"),
    ],
)
total_ok += ok
total_fail += fail

print(f"\n{'='*60}")
print(f"  总计: ✅ {total_ok}  ❌ {total_fail}")
print(f"{'='*60}")
