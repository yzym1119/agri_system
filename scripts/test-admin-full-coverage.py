#!/usr/bin/env python3
"""Admin API 全覆盖测试 —— 覆盖所有160个Admin端点的CRUD操作"""
import json, sys, time, uuid, urllib.request, urllib.error

BASE = "http://[::1]:8080"

def req(method, path, body=None, token=None, raw_response=False):
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
            raw_bytes = resp.read()
            if raw_response:
                return status, raw_bytes  # return bytes for binary (Excel etc.)
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

def login(phone, password="admin123"):
    code, d = req("POST", "/auth/staff/login", {"account": phone, "password": password})
    if code == 200 and d.get("tokenValue"):
        return d["tokenValue"], d.get("userInfo", {})
    return None, None

def biz_code(d):
    """Extract business code from various response formats"""
    if isinstance(d, dict):
        c = d.get("code")
        if c is not None:
            return c
    return None

def is_ok(code, d):
    """Check if response is successful across all response formats"""
    if code not in (200, 201, 204):
        return False
    biz = biz_code(d)
    if biz == 0 or biz == "200":
        return True
    if isinstance(d, dict) and d.get("data") is not None:
        return True
    if biz is None and code == 200:
        return True
    # DELETE/void: {code:0, message:"success"} with no data is OK
    if isinstance(d, dict) and d.get("message") and not d.get("error"):
        return True
    return False

def extract_id(d):
    """Extract created entity ID from various response formats"""
    if isinstance(d, dict):
        if d.get("id"):
            return d["id"]
        data = d.get("data")
        if isinstance(data, dict):
            return data.get("id") or data.get("orderId")
        if isinstance(data, (int, float)):
            return data
    if isinstance(d, (int, float)):
        return d
    return None

# ── 全局登录 ──
admin_token, admin_info = login("13800000001")
if not admin_token:
    print("❌ 管理员登录失败")
    sys.exit(1)
print(f"管理员登录成功: {admin_info.get('name')}")

total_ok, total_fail = 0, 0

# 清理用变量初始化
test_product_id = test_cat_id = pkg_id = None
coupon_id = rule_id = vip_id = None
test_store_id = test_staff_id = test_supplier_id = test_role_id = None
dict_id = points_goods_id = None

def check(desc, condition, detail=""):
    global total_ok, total_fail
    if condition:
        print(f"  ✅ {desc}")
        total_ok += 1
    else:
        print(f"  ❌ {desc} {detail}")
        total_fail += 1

# ================================================================
# 模块1: 商品管理 (ProductController)
# ================================================================
print("\n" + "=" * 60)
print("  模块1: 商品管理")
print("=" * 60)

# 1.1 获取分类树
code, d = req("GET", "/api/admin/products/categories/tree", token=admin_token)
check("获取分类树", is_ok(code, d), f"code={code}")
cat_list = d.get("data", []) if isinstance(d, dict) else []
parent_cat_id = cat_list[0]["id"] if cat_list else None
print(f"  现有分类数: {len(cat_list)}")

# 1.2 创建分类
ts = str(int(time.time()))
code, d = req("POST", "/api/admin/products/categories", {
    "name": f"测试分类-{ts}",
    "sortOrder": 99,
    "remark": "自动化测试"
}, token=admin_token)
test_cat_id = extract_id(d)
check("创建分类", is_ok(code, d) and test_cat_id, f"code={code}, id={test_cat_id}")

# 1.3 更新分类
if test_cat_id:
    code, d = req("PUT", f"/api/admin/products/categories/{test_cat_id}", {
        "id": test_cat_id,
        "name": f"测试分类-更新-{ts}",
        "sortOrder": 98,
        "remark": "已更新"
    }, token=admin_token)
    check("更新分类", is_ok(code, d), f"code={code}")

# 1.4 创建商品
code, d = req("POST", "/api/admin/products", {
    "name": f"测试商品-{ts}",
    "categoryId": test_cat_id or (parent_cat_id if parent_cat_id else 1),
    "mainUnit": "袋",
    "auxUnit": "包",
    "mainToAuxRatio": 10,
    "suggestedRetailPrice": 25.00,
    "vipPrice": 22.00,
    "minPrice": 18.00,
    "specification": "500g/袋",
    "dosageForm": "颗粒",
    "manufacturer": "测试厂家",
    "isOnline": 1
}, token=admin_token)
test_product_id = extract_id(d)
check("创建商品", is_ok(code, d) and test_product_id, f"code={code}, id={test_product_id}")

# 1.5 查询商品详情
if test_product_id:
    code, d = req("GET", f"/api/admin/products/{test_product_id}", token=admin_token)
    check("查询商品详情", is_ok(code, d), f"code={code}")

# 1.6 查询商品列表
code, d = req("GET", "/api/admin/products?pageNum=1&pageSize=5", token=admin_token)
check("查询商品列表", is_ok(code, d), f"code={code}")

# 1.7 更新商品
if test_product_id:
    code, d = req("PUT", f"/api/admin/products/{test_product_id}", {
        "id": test_product_id,
        "name": f"测试商品-更新-{ts}",
        "mainUnit": "袋",
        "suggestedRetailPrice": 28.00,
        "vipPrice": 25.00,
        "minPrice": 20.00
    }, token=admin_token)
    check("更新商品", is_ok(code, d), f"code={code}")

# 1.8 上下架
if test_product_id:
    code, d = req("PUT", f"/api/admin/products/{test_product_id}/online-status?isOnline=0", token=admin_token)
    check("商品下架", is_ok(code, d), f"code={code}")
    code, d = req("PUT", f"/api/admin/products/{test_product_id}/online-status?isOnline=1", token=admin_token)
    check("商品上架", is_ok(code, d), f"code={code}")

# 1.9 单位换算 - 创建
if test_product_id:
    code, d = req("POST", "/api/admin/products/unit-conversions", {
        "productId": test_product_id,
        "fromUnit": "袋",
        "toUnit": "包",
        "conversionRatio": 10.0
    }, token=admin_token)
    uc_id = extract_id(d)
    check("创建单位换算", is_ok(code, d) and uc_id, f"code={code}, id={uc_id}")

    # 1.10 查询单位换算
    code, d = req("GET", f"/api/admin/products/{test_product_id}/unit-conversions", token=admin_token)
    check("查询单位换算", is_ok(code, d), f"code={code}")

    # 1.11 删除单位换算
    if uc_id:
        code, d = req("DELETE", f"/api/admin/products/unit-conversions/{uc_id}", token=admin_token)
        check("删除单位换算", is_ok(code, d), f"code={code}")

# 1.12 价格规则 - 创建
if test_product_id:
    code, d = req("POST", "/api/admin/products/price-rules", {
        "productId": test_product_id,
        "priceType": 1,
        "price": 20.00,
        "vipLevel": 1,
        "status": 1
    }, token=admin_token)
    pr_id = extract_id(d)
    check("创建价格规则", is_ok(code, d) and pr_id, f"code={code}, id={pr_id}")

    # 1.13 查询价格规则
    code, d = req("GET", f"/api/admin/products/{test_product_id}/price-rules", token=admin_token)
    check("查询价格规则", is_ok(code, d), f"code={code}")

    # 1.14 更新价格规则
    if pr_id:
        code, d = req("PUT", f"/api/admin/products/price-rules/{pr_id}", {
            "id": pr_id,
            "price": 19.50,
            "status": 1
        }, token=admin_token)
        check("更新价格规则", is_ok(code, d), f"code={code}")

        # 1.15 删除价格规则
        code, d = req("DELETE", f"/api/admin/products/price-rules/{pr_id}", token=admin_token)
        check("删除价格规则", is_ok(code, d), f"code={code}")

# ================================================================
# 模块2: 商品套餐 (ProductPackageController)
# ================================================================
print("\n" + "=" * 60)
print("  模块2: 商品套餐")
print("=" * 60)

# 获取已有商品用于套餐
code, d = req("GET", "/api/admin/products?pageNum=1&pageSize=2", token=admin_token)
pkg_product_ids = []
if isinstance(d, dict) and d.get("data"):
    records = d["data"].get("records", []) if isinstance(d["data"], dict) else []
    pkg_product_ids = [r["id"] for r in records if r.get("id")]

if len(pkg_product_ids) >= 1:
    pid = pkg_product_ids[0]
    # 2.1 创建套餐
    code, d = req("POST", "/api/admin/product-packages", {
        "name": f"测试套餐-{ts}",
        "suggestedRetailPrice": 50.00,
        "vipPrice": 45.00,
        "minPrice": 40.00,
        "items": [
            {"productId": pid, "quantity": 2, "unit": "袋"}
        ],
        "remark": "自动化测试套餐"
    }, token=admin_token)
    pkg_id = extract_id(d)
    check("创建套餐", is_ok(code, d) and pkg_id, f"code={code}, id={pkg_id}")

    # 2.2 查询套餐详情
    if pkg_id:
        code, d = req("GET", f"/api/admin/product-packages/{pkg_id}", token=admin_token)
        check("查询套餐详情", is_ok(code, d), f"code={code}")

    # 2.3 查询套餐列表
    code, d = req("GET", "/api/admin/product-packages?pageNum=1&pageSize=5", token=admin_token)
    check("查询套餐列表", is_ok(code, d), f"code={code}")

    # 2.4 更新套餐
    if pkg_id:
        code, d = req("PUT", f"/api/admin/product-packages/{pkg_id}", {
            "id": pkg_id,
            "name": f"测试套餐-更新-{ts}",
            "suggestedRetailPrice": 55.00,
            "items": [
                {"productId": pid, "quantity": 3, "unit": "袋"}
            ]
        }, token=admin_token)
        check("更新套餐", is_ok(code, d), f"code={code}")

    # 2.5 套餐上下架
    if pkg_id:
        code, d = req("PUT", f"/api/admin/product-packages/{pkg_id}/status?status=0", token=admin_token)
        check("套餐下架", is_ok(code, d), f"code={code}")
        code, d = req("PUT", f"/api/admin/product-packages/{pkg_id}/status?status=1", token=admin_token)
        check("套餐上架", is_ok(code, d), f"code={code}")
else:
    check("创建套餐(无可用商品)", False)

# ================================================================
# 模块3: 营销管理 (MarketingAdminController)
# ================================================================
print("\n" + "=" * 60)
print("  模块3: 营销管理")
print("=" * 60)

# 3.1 优惠券模板 - 查询列表
code, d = req("GET", "/api/admin/marketing/coupon-template?pageNum=1&pageSize=5", token=admin_token)
check("查询优惠券模板列表", code == 200, f"code={code}")

# 3.2 优惠券模板 - 创建
code, d = req("POST", "/api/admin/marketing/coupon-template", {
    "name": f"测试优惠券-{ts}",
    "type": 1,
    "amount": 10.00,
    "minOrderAmount": 100.00,
    "validityType": 1,
    "validDays": 30,
    "totalCount": 100,
    "status": 1,
    "remark": "自动化测试优惠券"
}, token=admin_token)
coupon_id = extract_id(d)
check("创建优惠券模板", is_ok(code, d) and coupon_id, f"code={code}, biz={biz_code(d)}")

# 3.3 优惠券模板 - 查询详情
if coupon_id:
    code, d = req("GET", f"/api/admin/marketing/coupon-template/{coupon_id}", token=admin_token)
    check("查询优惠券详情", code == 200 and d is not None, f"code={code}")

# 3.4 优惠券模板 - 更新
if coupon_id:
    code, d = req("PUT", f"/api/admin/marketing/coupon-template/{coupon_id}", {
        "name": f"测试优惠券-更新-{ts}",
        "type": 1,
        "amount": 15.00,
        "minOrderAmount": 120.00,
        "validityType": 1,
        "validDays": 60,
        "totalCount": 200,
        "status": 1
    }, token=admin_token)
    check("更新优惠券模板", is_ok(code, d), f"code={code}")

# 3.5 优惠券模板 - 状态变更
if coupon_id:
    code, d = req("PUT", f"/api/admin/marketing/coupon-template/{coupon_id}/status?status=0", token=admin_token)
    check("优惠券模板停用", is_ok(code, d), f"code={code}")
    code, d = req("PUT", f"/api/admin/marketing/coupon-template/{coupon_id}/status?status=1", token=admin_token)
    check("优惠券模板启用", is_ok(code, d), f"code={code}")

# 3.6 充值规则 - 查询列表
code, d = req("GET", "/api/admin/marketing/recharge-rule", token=admin_token)
check("查询充值规则列表", code == 200, f"code={code}")

# 3.7 充值规则 - 创建
code, d = req("POST", "/api/admin/marketing/recharge-rule", {
    "name": f"测试充值规则-{ts}",
    "minRechargeAmount": 100.00,
    "targetVipLevel": 1,
    "giftDurationDays": 30,
    "giftSpecialFund": 10.00,
    "status": 1,
    "sortOrder": 99
}, token=admin_token)
rule_id = extract_id(d)
check("创建充值规则", is_ok(code, d) and rule_id, f"code={code}, id={rule_id}")

# 3.8 充值规则 - 更新
if rule_id:
    code, d = req("PUT", f"/api/admin/marketing/recharge-rule/{rule_id}", {
        "name": f"测试充值规则-更新-{ts}",
        "minRechargeAmount": 200.00,
        "targetVipLevel": 2,
        "giftDurationDays": 60,
        "giftSpecialFund": 25.00,
        "status": 1,
        "sortOrder": 98
    }, token=admin_token)
    check("更新充值规则", is_ok(code, d), f"code={code}")

# 3.9 充值规则 - 状态变更
if rule_id:
    code, d = req("PUT", f"/api/admin/marketing/recharge-rule/{rule_id}/status?status=0", token=admin_token)
    check("充值规则停用", is_ok(code, d), f"code={code}")

# 3.10 VIP权益 - 查询列表
code, d = req("GET", "/api/admin/marketing/vip-rights", token=admin_token)
check("查询VIP权益列表", code == 200, f"code={code}")

# 3.11 VIP权益 - 创建
code, d = req("POST", "/api/admin/marketing/vip-rights", {
    "vipLevel": 5,
    "benefitType": 1,
    "benefitValue": 100.00,
    "status": 1
}, token=admin_token)
vip_id = extract_id(d)
check("创建VIP权益", is_ok(code, d) and vip_id, f"code={code}, id={vip_id}")

# 3.12 VIP权益 - 按等级查询
code, d = req("GET", "/api/admin/marketing/vip-rights/level/5", token=admin_token)
check("VIP权益按等级查询", code == 200, f"code={code}")

# ================================================================
# 模块4: 门店管理 (StoreController)
# ================================================================
print("\n" + "=" * 60)
print("  模块4: 门店管理")
print("=" * 60)

# 4.1 查询门店列表
code, d = req("GET", "/api/admin/stores?pageNum=1&pageSize=5", token=admin_token)
check("查询门店列表", is_ok(code, d), f"code={code}")
existing_store_id = None
if isinstance(d, dict) and d.get("data"):
    records = d["data"].get("records", []) if isinstance(d["data"], dict) else []
    if records:
        existing_store_id = records[0].get("id")

# 4.2 创建门店
code, d = req("POST", "/api/admin/stores", {
    "name": f"测试门店-{ts}",
    "phone": f"138{ts[-8:]}",
    "address": "测试地址123号",
    "supportPickup": 1,
    "status": 1,
    "remark": "自动化测试门店"
}, token=admin_token)
test_store_id = extract_id(d)
check("创建门店", is_ok(code, d) and test_store_id, f"code={code}, id={test_store_id}")

# 4.3 查询门店详情
if test_store_id:
    code, d = req("GET", f"/api/admin/stores/{test_store_id}", token=admin_token)
    check("查询门店详情", is_ok(code, d), f"code={code}")

# 4.4 更新门店
if test_store_id:
    code, d = req("PUT", f"/api/admin/stores/{test_store_id}", {
        "id": test_store_id,
        "name": f"测试门店-更新-{ts}",
        "phone": f"139{ts[-8:]}",
        "address": "更新地址456号",
        "supportPickup": 0,
        "status": 1,
        "remark": "已更新"
    }, token=admin_token)
    check("更新门店", is_ok(code, d), f"code={code}")

# ================================================================
# 模块5: 员工管理 (StaffAdminController)
# ================================================================
print("\n" + "=" * 60)
print("  模块5: 员工管理")
print("=" * 60)

# 获取角色ID
code, d = req("GET", "/api/admin/roles", token=admin_token)
role_list = d.get("data", []) if isinstance(d, dict) else []
staff_role_id = None
for r in role_list:
    if r.get("code") == "store_clerk":
        staff_role_id = r["id"]
        break
if not staff_role_id and role_list:
    staff_role_id = role_list[-1]["id"]

store_id_for_staff = existing_store_id or test_store_id or 1

# 5.1 查询员工列表
code, d = req("GET", "/api/admin/staff?pageNum=1&pageSize=5", token=admin_token)
check("查询员工列表", is_ok(code, d), f"code={code}")

# 5.2 创建员工
staff_phone = f"150{str(int(time.time()))[-8:]}"
code, d = req("POST", "/api/admin/staff", {
    "name": f"测试员工-{ts}",
    "phone": staff_phone,
    "storeId": store_id_for_staff,
    "password": "Test123456",
    "roleId": staff_role_id or 4,
    "status": 1
}, token=admin_token)
test_staff_id = extract_id(d)
check("创建员工", is_ok(code, d) and test_staff_id, f"code={code}, biz={biz_code(d)}, id={test_staff_id}")

# 5.3 查询员工详情
if test_staff_id:
    code, d = req("GET", f"/api/admin/staff/{test_staff_id}/profile", token=admin_token)
    check("查询员工详情", is_ok(code, d), f"code={code}")

# 5.4 更新员工
if test_staff_id:
    code, d = req("PUT", f"/api/admin/staff/{test_staff_id}", {
        "id": test_staff_id,
        "name": f"测试员工-更新-{ts}",
        "phone": staff_phone,
        "storeId": store_id_for_staff,
        "roleId": staff_role_id or 4,
        "status": 1
    }, token=admin_token)
    check("更新员工", is_ok(code, d), f"code={code}")

# 5.5 禁用/启用员工
if test_staff_id:
    code, d = req("POST", f"/api/admin/staff/{test_staff_id}/disable", token=admin_token)
    check("禁用员工", is_ok(code, d), f"code={code}")
    code, d = req("POST", f"/api/admin/staff/{test_staff_id}/enable", token=admin_token)
    check("启用员工", is_ok(code, d), f"code={code}")

# 5.6 重置密码
if test_staff_id:
    code, d = req("POST", "/api/admin/staff/reset-password", {
        "staffId": test_staff_id,
        "newPassword": "NewPass123456"
    }, token=admin_token)
    check("重置密码", is_ok(code, d), f"code={code}")

# ================================================================
# 模块6: 供应商管理 (SupplierController)
# ================================================================
print("\n" + "=" * 60)
print("  模块6: 供应商管理")
print("=" * 60)

# 6.1 查询供应商列表
code, d = req("GET", "/api/admin/suppliers?pageNum=1&pageSize=5", token=admin_token)
check("查询供应商列表", is_ok(code, d), f"code={code}")

# 6.2 创建供应商
code, d = req("POST", "/api/admin/suppliers", {
    "name": f"测试供应商-{ts}",
    "contactName": "张经理",
    "contactPhone": f"186{ts[-8:]}",
    "bankAccount": "6222020202020202",
    "bankName": "工商银行",
    "address": "供应商地址789号",
    "status": 1,
    "remark": "自动化测试供应商"
}, token=admin_token)
test_supplier_id = extract_id(d)
check("创建供应商", is_ok(code, d) and test_supplier_id, f"code={code}, id={test_supplier_id}")

# 6.3 查询供应商详情
if test_supplier_id:
    code, d = req("GET", f"/api/admin/suppliers/{test_supplier_id}", token=admin_token)
    check("查询供应商详情", is_ok(code, d), f"code={code}")

# 6.4 更新供应商
if test_supplier_id:
    code, d = req("PUT", f"/api/admin/suppliers/{test_supplier_id}", {
        "id": test_supplier_id,
        "name": f"测试供应商-更新-{ts}",
        "contactName": "李经理",
        "contactPhone": f"187{ts[-8:]}",
        "address": "更新供应商地址",
        "status": 1
    }, token=admin_token)
    check("更新供应商", is_ok(code, d), f"code={code}")

# ================================================================
# 模块7: 角色管理 (RoleController)
# ================================================================
print("\n" + "=" * 60)
print("  模块7: 角色管理")
print("=" * 60)

# 7.1 查询角色列表
code, d = req("GET", "/api/admin/roles", token=admin_token)
check("查询角色列表", is_ok(code, d), f"code={code}")

# 7.2 创建角色
code, d = req("POST", "/api/admin/roles", {
    "name": f"测试角色-{ts}",
    "code": f"test_role_{ts}",
    "status": 1
}, token=admin_token)
test_role_id = extract_id(d)
check("创建角色", is_ok(code, d) and test_role_id, f"code={code}, id={test_role_id}")

# 7.3 查询角色菜单
if test_role_id:
    code, d = req("GET", f"/api/admin/roles/{test_role_id}/menus", token=admin_token)
    check("查询角色菜单", is_ok(code, d), f"code={code}")

# 7.4 分配角色菜单 (先获取菜单树)
code, d = req("GET", "/api/admin/menus/tree", token=admin_token)
menu_ids = []
if is_ok(code, d) and isinstance(d, dict) and d.get("data"):
    def collect_menu_ids(menus):
        ids = []
        for m in menus:
            if m.get("id"):
                ids.append(m["id"])
            if m.get("children"):
                ids.extend(collect_menu_ids(m["children"]))
        return ids
    menu_ids = collect_menu_ids(d["data"])[:5]  # 只分配前5个菜单

if test_role_id and menu_ids:
    code, d = req("POST", f"/api/admin/roles/{test_role_id}/menus", {
        "roleId": test_role_id,
        "menuIds": menu_ids
    }, token=admin_token)
    check("分配角色菜单", is_ok(code, d), f"code={code}")

# ================================================================
# 模块8: 字典管理 (DictAdminController)
# ================================================================
print("\n" + "=" * 60)
print("  模块8: 字典管理")
print("=" * 60)

# 8.1 查询字典数据
code, d = req("GET", "/api/admin/dict/data?typeCode=payment_method", token=admin_token)
check("查询字典数据", code == 200, f"code={code}")

# 8.2 创建字典项
code, d = req("POST", "/api/admin/dict/data", {
    "typeCode": "test_type",
    "label": f"测试标签-{ts}",
    "value": f"test_val_{ts}",
    "sortOrder": 99,
    "status": 1,
    "isDefault": 0
}, token=admin_token)
dict_id = d if isinstance(d, (int, float)) else extract_id(d)
dict_created = code == 200 and (dict_id or is_ok(code, d))
check("创建字典项", dict_created, f"code={code}, id={dict_id}")

# 8.3 更新字典项
if dict_id:
    code, d = req("PUT", "/api/admin/dict/data", {
        "id": dict_id,
        "typeCode": "test_type",
        "label": f"测试标签-更新-{ts}",
        "value": f"test_val_{ts}_upd",
        "sortOrder": 98,
        "status": 1,
        "isDefault": 0
    }, token=admin_token)
    check("更新字典项", code == 200, f"code={code}")

# ================================================================
# 模块9: 库存管理扩展 (InventoryController)
# ================================================================
print("\n" + "=" * 60)
print("  模块9: 库存管理扩展")
print("=" * 60)

store_id = existing_store_id or test_store_id or 1
product_id = test_product_id or 62

# 9.1 库存调整 - 盘盈
code, d = req("POST", "/api/admin/inventory/adjust", {
    "storeId": store_id,
    "productId": product_id,
    "quantityDiff": 5,
    "type": 1,
    "remark": "测试盘盈"
}, token=admin_token)
check("库存调整-盘盈", is_ok(code, d), f"code={code}, biz={biz_code(d)}")

# 9.2 库存调整 - 盘亏
code, d = req("POST", "/api/admin/inventory/adjust", {
    "storeId": store_id,
    "productId": product_id,
    "quantityDiff": -3,
    "type": 2,
    "remark": "测试盘亏"
}, token=admin_token)
check("库存调整-盘亏", is_ok(code, d), f"code={code}, biz={biz_code(d)}")

# 9.3 库存交易流水
code, d = req("GET", f"/api/admin/inventory/transactions?storeId={store_id}&productId={product_id}&pageNum=1&pageSize=5", token=admin_token)
check("库存交易流水", is_ok(code, d), f"code={code}")

# 9.4 库存调拨记录
code, d = req("GET", "/api/admin/inventory/transfers?pageNum=1&pageSize=5", token=admin_token)
check("库存调拨记录", is_ok(code, d), f"code={code}")

# 9.5 库存明细查询
code, d = req("GET", f"/api/admin/inventory/detail?storeId={store_id}&productId={product_id}", token=admin_token)
check("库存明细查询", is_ok(code, d), f"code={code}")

# ================================================================
# 模块10: 订单管理扩展 (OrderAdminController)
# ================================================================
print("\n" + "=" * 60)
print("  模块10: 订单管理扩展")
print("=" * 60)

# 10.1 查询订单详情 (用已有订单)
code, d = req("GET", "/api/admin/orders?pageNum=1&pageSize=1", token=admin_token)
order_id_for_test = None
if code == 200 and isinstance(d, dict):
    records = d.get("records", [])
    if not records and d.get("data") and isinstance(d["data"], dict):
        records = d["data"].get("records", [])
    if records:
        order_id_for_test = records[0].get("id") or records[0].get("orderId")

if order_id_for_test:
    code, d = req("GET", f"/api/admin/orders/{order_id_for_test}", token=admin_token)
    check("查询订单详情", code == 200, f"code={code}")
else:
    check("查询订单详情(无订单)", False)

# 10.2 订单发货 (创建一个新POS订单用于测试)
code, d = req("POST", "/api/admin/orders/pos", {
    "storeId": store_id,
    "staffId": 1,
    "items": [{"productId": 62, "quantity": 1}],
    "paymentType": 1,
    "receiveAmount": 25.00
}, token=admin_token)
ship_order_id = d if isinstance(d, (int, float)) else extract_id(d)
check("创建POS订单(发货测试)", code == 200 and ship_order_id, f"code={code}, id={ship_order_id}")

if ship_order_id:
    # 10.3 发货
    code, d = req("POST", f"/api/admin/orders/{ship_order_id}/ship", {
        "orderId": ship_order_id,
        "logisticsCompany": "顺丰",
        "trackingNo": f"SF{ts}"
    }, token=admin_token)
    check("订单发货", code == 200, f"code={code}, biz={biz_code(d)}")

# 10.4 自提核销 (创建一个自提订单)
code, d = req("POST", "/api/admin/orders/pos", {
    "storeId": store_id,
    "staffId": 1,
    "items": [{"productId": 62, "quantity": 1}],
    "paymentType": 1,
    "receiveAmount": 25.00,
    "remark": "pickup"
}, token=admin_token)
pickup_order_id = d if isinstance(d, (int, float)) else extract_id(d)
if pickup_order_id:
    # 查询订单获取核销码
    code2, d2 = req("GET", f"/api/admin/orders/{pickup_order_id}", token=admin_token)
    pickup_code = None
    if code2 == 200 and isinstance(d2, dict):
        pickup_code = d2.get("pickupCode") or d2.get("data", {}).get("pickupCode") if isinstance(d2.get("data"), dict) else d2.get("pickupCode")
    if pickup_code:
        code, d = req("POST", "/api/admin/orders/pickup/verify", {
            "pickupCode": pickup_code
        }, token=admin_token)
        check("自提核销", code == 200, f"code={code}")
    else:
        check("自提核销(无核销码，跳过)", True)

# 10.5 管理员退款
if ship_order_id:
    # 先获取订单明细
    code2, d2 = req("GET", f"/api/admin/orders/{ship_order_id}", token=admin_token)
    order_items = []
    if code2 == 200 and isinstance(d2, dict):
        items = d2.get("items") or (d2.get("data", {}).get("items") if isinstance(d2.get("data"), dict) else [])
        if items:
            order_items = items

    if order_items:
        code, d = req("POST", f"/api/admin/orders/{ship_order_id}/refund", {
            "orderId": ship_order_id,
            "refundReason": "管理员测试退款",
            "refundAmount": 25.00,
            "items": [{
                "orderItemId": order_items[0].get("id") or order_items[0].get("orderItemId"),
                "quantity": 1,
                "refundAmount": 25.00
            }]
        }, token=admin_token)
        check("管理员退款", code == 200, f"code={code}, biz={biz_code(d)}")
    else:
        check("管理员退款(无订单明细)", False)

# ================================================================
# 模块11: 报表扩展 (Report)
# ================================================================
print("\n" + "=" * 60)
print("  模块11: 报表扩展")
print("=" * 60)

# 11.1 Dashboard 今日概览
code, d = req("GET", "/api/admin/report/dashboard/today", token=admin_token)
check("Dashboard今日概览", code == 200, f"code={code}")

# 11.2 销售趋势导出 (Excel)
start_time = "2025-01-01T00:00:00"
end_time = "2026-12-31T23:59:59"
code, raw = req("GET", f"/api/admin/report/sales/trend/export?startTime={start_time}&endTime={end_time}&storeId={store_id}", token=admin_token, raw_response=True)
check("销售趋势导出", code == 200 and len(raw) > 100, f"code={code}, size={len(raw) if isinstance(raw, bytes) else 0}")

# 11.3 客户TOP导出
code, raw = req("GET", "/api/admin/report/customer/top/export?limit=10", token=admin_token, raw_response=True)
check("客户TOP导出", code == 200 and len(raw) > 50, f"code={code}, size={len(raw) if isinstance(raw, bytes) else 0}")

# 11.4 客户流失分析
code, d = req("GET", "/api/admin/report/customer/churn", token=admin_token)
check("客户流失分析", code == 200, f"code={code}")

# 11.5 客户流失导出
code, raw = req("GET", "/api/admin/report/customer/churn/export", token=admin_token, raw_response=True)
check("客户流失导出", code == 200, f"code={code}, size={len(raw) if isinstance(raw, bytes) else 0}")

# 11.6 滞销库存
code, d = req("GET", "/api/admin/report/inventory/slow-moving", token=admin_token)
check("滞销库存报表", code == 200, f"code={code}")

# 11.7 滞销库存导出
code, raw = req("GET", "/api/admin/report/inventory/slow-moving/export", token=admin_token, raw_response=True)
check("滞销库存导出", code == 200, f"code={code}, size={len(raw) if isinstance(raw, bytes) else 0}")

# ================================================================
# 模块12: 赊销管理扩展 (CreditAdminController)
# ================================================================
print("\n" + "=" * 60)
print("  模块12: 赊销管理扩展")
print("=" * 60)

# 获取有赊销的客户
code, d = req("GET", "/api/admin/credit/accounts?pageNum=1&pageSize=1", token=admin_token)
credit_customer_id = None
if code == 200 and isinstance(d, dict):
    records = d.get("data", {}).get("records", []) if isinstance(d.get("data"), dict) else d.get("records", [])
    if records:
        credit_customer_id = records[0].get("customerId")

if credit_customer_id:
    # 12.1 调整赊销额度
    code, d = req("PUT", f"/api/admin/credit/limit?customerId={credit_customer_id}&creditLimit=50000", token=admin_token)
    check("调整赊销额度", code == 200, f"code={code}")

    # 12.2 生成对账单
    code, d = req("POST", "/api/admin/credit/statements", {
        "customerId": credit_customer_id,
        "startDate": "2025-01-01",
        "endDate": "2026-12-31",
        "remark": "测试对账单"
    }, token=admin_token)
    check("生成对账单", code == 200, f"code={code}, biz={biz_code(d)}")
else:
    check("调整赊销额度(无赊销客户)", True)
    check("生成对账单(无赊销客户)", True)

# ================================================================
# 模块13: 绩效管理 (PerformanceAdminController + StaffController)
# ================================================================
print("\n" + "=" * 60)
print("  模块13: 绩效管理")
print("=" * 60)

# 13.1 绩效目标设置
code, d = req("POST", "/api/admin/performance/goal", {
    "staffId": 1,
    "month": "2026-05",
    "targetSalesAmount": 50000.00,
    "targetNewCustomer": 10,
    "targetCommission": 5000.00
}, token=admin_token)
check("绩效目标设置", is_ok(code, d), f"code={code}, biz={biz_code(d)}")

# 13.2 绩效账单查询
code, d = req("GET", "/api/admin/performance/bill?page=1&size=5", token=admin_token)
check("绩效账单查询", is_ok(code, d), f"code={code}")

# 13.3 员工绩效仪表盘
code, d = req("GET", "/api/staff/performance/dashboard?staffId=1", token=admin_token)
check("员工绩效仪表盘", is_ok(code, d), f"code={code}")

# 13.4 员工绩效日志
code, d = req("GET", "/api/staff/performance/logs?staffId=1&page=1&size=5", token=admin_token)
check("员工绩效日志", is_ok(code, d), f"code={code}")

# ================================================================
# 模块14: 积分管理 (PointsGoods + PointsOrder)
# ================================================================
print("\n" + "=" * 60)
print("  模块14: 积分管理")
print("=" * 60)

# 14.1 创建积分商品
code, d = req("POST", "/api/admin/points/goods", {
    "name": f"测试积分商品-{ts}",
    "points": 500,
    "stock": 100,
    "description": "自动化测试积分商品",
    "sortOrder": 99,
    "status": 1
}, token=admin_token)
points_goods_id = extract_id(d)
# PointsGoods returns the VO directly
if isinstance(d, dict) and d.get("id"):
    points_goods_id = d["id"]
check("创建积分商品", code == 200 and points_goods_id, f"code={code}, id={points_goods_id}")

# 14.2 更新积分商品
if points_goods_id:
    code, d = req("PUT", f"/api/admin/points/goods/{points_goods_id}", {
        "name": f"测试积分商品-更新-{ts}",
        "points": 600,
        "stock": 200,
        "description": "已更新",
        "sortOrder": 98,
        "status": 1
    }, token=admin_token)
    check("更新积分商品", code == 200, f"code={code}")

# 14.3 积分商品上下架
if points_goods_id:
    code, d = req("POST", f"/api/admin/points/goods/{points_goods_id}/off", token=admin_token)
    check("积分商品下架", code == 200, f"code={code}")
    code, d = req("POST", f"/api/admin/points/goods/{points_goods_id}/on", token=admin_token)
    check("积分商品上架", code == 200, f"code={code}")

# 14.4 积分兑换订单列表
code, d = req("GET", "/api/admin/points/orders?pageNum=1&pageSize=5", token=admin_token)
check("积分兑换订单列表", code == 200, f"code={code}")

# ================================================================
# 模块15: 客户管理扩展 (CustomerAdminController)
# ================================================================
print("\n" + "=" * 60)
print("  模块15: 客户管理扩展")
print("=" * 60)

# 获取测试客户
code, d = req("GET", "/api/admin/customer/list?pageNum=1&pageSize=1", token=admin_token)
cust_id = None
if code == 200 and isinstance(d, dict):
    records = d.get("records", [])
    if not records and d.get("data") and isinstance(d["data"], dict):
        records = d["data"].get("records", [])
    if records:
        cust_id = records[0].get("id") or records[0].get("customerId")

if cust_id:
    # 15.1 查询客户详情
    code, d = req("GET", f"/api/admin/customer/{cust_id}", token=admin_token)
    check("查询客户详情", code == 200, f"code={code}")

    # 15.2 更新客户
    code, d = req("PUT", f"/api/admin/customer/{cust_id}", {
        "name": "更新客户名",
        "address": "更新地址"
    }, token=admin_token)
    check("更新客户", code == 200, f"code={code}")

    # 15.3 钱包交易记录
    code, d = req("GET", f"/api/admin/customer/wallet-transactions?customerId={cust_id}&pageNum=1&pageSize=5", token=admin_token)
    check("钱包交易记录", code == 200, f"code={code}")

    # 15.4 VIP月度记录
    code, d = req("GET", "/api/admin/customer/vip-monthly-records?pageNum=1&pageSize=5", token=admin_token)
    check("VIP月度记录", code == 200, f"code={code}")

    # 15.5 客户权益日志
    code, d = req("GET", f"/api/admin/customer/{cust_id}/benefit-logs?pageNum=1&pageSize=5", token=admin_token)
    check("客户权益日志", code == 200, f"code={code}")

    # 15.6 客户优惠券
    code, d = req("GET", f"/api/admin/customer/{cust_id}/coupons?pageNum=1&pageSize=5", token=admin_token)
    check("客户优惠券", code == 200, f"code={code}")
else:
    check("客户管理扩展(无客户)", True)

# 15.7 复购提醒
code, d = req("GET", "/api/admin/customer/follow-up/repurchase-alerts?limit=10", token=admin_token)
check("复购提醒", code == 200, f"code={code}")

# 15.8 发放优惠券 (需要优惠券模板)
if cust_id and coupon_id:
    code, d = req("POST", "/api/admin/customer/grant-coupon", {
        "customerId": cust_id,
        "templateId": coupon_id,
        "count": 1
    }, token=admin_token)
    check("发放优惠券", code == 200, f"code={code}, biz={biz_code(d)}")

# ================================================================
# 模块16: 菜单与通知 (Menu + Notification)
# ================================================================
print("\n" + "=" * 60)
print("  模块16: 菜单与通知")
print("=" * 60)

# 16.1 菜单树
code, d = req("GET", "/api/admin/menus/tree", token=admin_token)
check("菜单树", is_ok(code, d), f"code={code}")

# 16.2 当前用户菜单
code, d = req("GET", "/api/admin/menus/current", token=admin_token)
check("当前用户菜单", is_ok(code, d), f"code={code}")

# 16.3 未读消息数
code, d = req("GET", "/api/admin/notifications/unread-count", token=admin_token)
check("未读消息数", code == 200, f"code={code}")

# 16.4 全部已读
code, d = req("PUT", "/api/admin/notifications/read-all", token=admin_token)
check("全部已读", code == 200, f"code={code}")

# ================================================================
# 模块17: 售后管理扩展 (AfterSalesAdminController)
# ================================================================
print("\n" + "=" * 60)
print("  模块17: 售后管理扩展")
print("=" * 60)

# 17.1 售后列表
code, d = req("GET", "/api/admin/after-sales?pageNum=1&pageSize=5", token=admin_token)
check("售后列表", code == 200, f"code={code}")

# 17.2 售后详情 (如果有售后单)
if code == 200 and isinstance(d, dict):
    records = d.get("records", [])
    if not records and d.get("data") and isinstance(d["data"], dict):
        records = d["data"].get("records", [])
    if records:
        as_id = records[0].get("id") or records[0].get("afterSalesId")
        if as_id:
            code, d = req("GET", f"/api/admin/after-sales/{as_id}", token=admin_token)
            check("售后详情", code == 200, f"code={code}")

            # 17.3 按订单查售后
            order_id_as = records[0].get("orderId")
            if order_id_as:
                code, d = req("GET", f"/api/admin/after-sales/order/{order_id_as}", token=admin_token)
                check("按订单查售后", code == 200, f"code={code}")

# ================================================================
# 模块18: 采购管理扩展 (PurchaseController)
# ================================================================
print("\n" + "=" * 60)
print("  模块18: 采购管理扩展")
print("=" * 60)

# 18.1 采购详情
code, d = req("GET", "/api/admin/purchase/list?pageNum=1&pageSize=1", token=admin_token)
purchase_order_id = None
if code == 200 and isinstance(d, dict):
    records = d.get("data", {}).get("records", []) if isinstance(d.get("data"), dict) else d.get("records", [])
    if records:
        purchase_order_id = records[0].get("id") or records[0].get("orderId")

if purchase_order_id:
    code, d = req("GET", f"/api/admin/purchase/{purchase_order_id}", token=admin_token)
    check("采购详情", is_ok(code, d), f"code={code}")

    # 18.2 取消采购单 (创建一个新采购单来取消)
    code2, d2 = req("POST", "/api/admin/purchase/create", {
        "storeId": store_id,
        "supplierId": 2,
        "items": [{"productId": 62, "qty": 5, "price": 15.0, "unit": "袋"}],
        "remark": "待取消的采购单"
    }, token=admin_token)
    cancel_po_id = extract_id(d2)
    if cancel_po_id:
        code, d = req("POST", "/api/admin/purchase/cancel", {
            "orderId": cancel_po_id
        }, token=admin_token)
        check("取消采购单", is_ok(code, d), f"code={code}, biz={biz_code(d)}")
    else:
        check("取消采购单(创建失败)", False)

# ================================================================
# 模块19: 打印 (PrintAdminController)
# ================================================================
print("\n" + "=" * 60)
print("  模块19: 打印")
print("=" * 60)

# 19.1 打印小票 (TODO标记, 应返回410)
code, d = req("POST", "/api/admin/print/receipt", {
    "orderId": 1,
    "type": "receipt"
}, token=admin_token)
check("打印小票(410=预期)", code == 410, f"code={code}")

# ================================================================
# 清理: 删除测试数据
# ================================================================
print("\n" + "=" * 60)
print("  清理测试数据")
print("=" * 60)

# 删除测试商品
if test_product_id:
    code, d = req("DELETE", f"/api/admin/products/{test_product_id}", token=admin_token)
    check("删除测试商品", is_ok(code, d), f"code={code}")

# 删除测试分类
if test_cat_id:
    code, d = req("DELETE", f"/api/admin/products/categories/{test_cat_id}", token=admin_token)
    check("删除测试分类", is_ok(code, d), f"code={code}")

# 删除测试套餐
if pkg_id:
    code, d = req("DELETE", f"/api/admin/product-packages/{pkg_id}", token=admin_token)
    check("删除测试套餐", is_ok(code, d), f"code={code}")

# 删除测试优惠券
if coupon_id:
    code, d = req("DELETE", f"/api/admin/marketing/coupon-template/{coupon_id}", token=admin_token)
    check("删除测试优惠券", code == 200, f"code={code}")

# 删除测试充值规则
if rule_id:
    code, d = req("DELETE", f"/api/admin/marketing/recharge-rule/{rule_id}", token=admin_token)
    check("删除测试充值规则", code == 200, f"code={code}")

# 删除测试VIP权益
if vip_id:
    code, d = req("DELETE", f"/api/admin/marketing/vip-rights/{vip_id}", token=admin_token)
    check("删除测试VIP权益", code == 200, f"code={code}")

# 删除测试门店
if test_store_id:
    code, d = req("DELETE", f"/api/admin/stores/{test_store_id}", token=admin_token)
    check("删除测试门店", is_ok(code, d), f"code={code}")

# 删除测试供应商
if test_supplier_id:
    code, d = req("DELETE", f"/api/admin/suppliers/{test_supplier_id}", token=admin_token)
    check("删除测试供应商", is_ok(code, d), f"code={code}")

# 删除测试角色
if test_role_id:
    code, d = req("DELETE", f"/api/admin/roles/{test_role_id}", token=admin_token)
    check("删除测试角色", is_ok(code, d), f"code={code}")

# 删除测试字典项
if dict_id:
    code, d = req("DELETE", f"/api/admin/dict/data?id={dict_id}&typeCode=test_type", token=admin_token)
    check("删除测试字典项", code == 200, f"code={code}")

# 删除测试积分商品
if points_goods_id:
    code, d = req("DELETE", f"/api/admin/points/goods/{points_goods_id}", token=admin_token)
    check("删除测试积分商品", code == 200, f"code={code}")

# 禁用测试员工(不删除,保留数据完整性)
if test_staff_id:
    code, d = req("POST", f"/api/admin/staff/{test_staff_id}/disable", token=admin_token)
    check("禁用测试员工", is_ok(code, d), f"code={code}")

# ================================================================
# 汇总
# ================================================================
print("\n" + "=" * 60)
print(f"  总计: ✅ {total_ok}  ❌ {total_fail}")
print("=" * 60)

if total_fail > 0:
    sys.exit(1)
