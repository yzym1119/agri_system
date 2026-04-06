# 系统接口契约文档

> **重要提示：** 本文档基于当前后端代码生成，如后端接口有更新，请同步更新本文档。
>
> **更新日期：** 2025-12-28
>
> **文档版本：** v2.0

## 目录

- [1. 认证授权模块 (Auth)](#1-认证授权模块-auth)
- [2. 商品管理模块 (Product)](#2-商品管理模块-product)
- [3. 订单模块 (Order)](#3-订单模块-order)
- [4. 客户管理模块 (Customer)](#4-客户管理模块-customer)
- [5. 积分商城模块 (Points Mall)](#5-积分商城模块-points-mall)
- [6. 赊销管理模块 (Credit)](#6-赊销管理模块-credit)
- [7. 库存管理模块 (Inventory)](#7-库存管理模块-inventory)
- [8. 售后管理模块 (After Sales)](#8-售后管理模块-after-sales)
- [9. 员工管理模块 (Staff)](#9-员工管理模块-staff)
- [10. 权限管理模块 (RBAC)](#10-权限管理模块-rbac)
- [11. 报表模块 (Report)](#11-报表模块-report)
- [12. 字典模块 (Dict)](#12-字典模块-dict)
- [13. 采购管理模块 (Purchase)](#13-采购管理模块-purchase)
- [14. 门店管理模块 (Store)](#14-门店管理模块-store)
- [15. 套餐管理模块 (Product Package)](#15-套餐管理模块-product-package)
- [16. 钱包管理模块 (Wallet)](#16-钱包管理模块-wallet)
- [17. 打印模块 (Print)](#17-打印模块-print)
- [18. 其他模块](#18-其他模块)

---

## 1. 认证授权模块 (Auth)

### 1.1 员工登录

**接口描述：** 员工账号密码登录

**Method & URL：** `POST /auth/staff/login`

**鉴权要求：** 无需鉴权

**关键入参：**
- `code` (String): 员工编码或手机号
- `password` (String): 密码

**关键响应：**
- `tokenName` (String): Token名称
- `tokenValue` (String): Token值
- `userInfo` (Object): 用户信息
  - `id` (Long): 员工ID
  - `name` (String): 员工姓名
  - `role` (String): 角色

---

### 1.2 员工登出

**接口描述：** 员工登出

**Method & URL：** `POST /auth/staff/logout`

**鉴权要求：** 需要员工登录

**关键入参：** 无

**关键响应：** 无

---

### 1.3 获取当前员工信息

**接口描述：** 获取当前登录员工的基本信息和权限

**Method & URL：** `GET /auth/staff/info`

**鉴权要求：** 需要员工登录

**关键入参：** 无

**关键响应：**
- `id` (Long): 员工ID
- `name` (String): 员工姓名
- `role` (String): 角色

---

### 1.4 客户微信登录

**接口描述：** 通过微信code登录，自动注册新用户

**Method & URL：** `POST /auth/client/login/wechat`

**鉴权要求：** 无需鉴权

**关键入参：**
- `code` (String): 微信登录code

**关键响应：**
- `tokenName` (String): Token名称
- `tokenValue` (String): Token值
- `userInfo` (Object): 用户信息
  - `id` (Long): 客户ID
  - `name` (String): 客户姓名
  - `type` (Integer): 客户类型（1-普通，2-VIP）

---

### 1.5 客户登出

**接口描述：** 客户登出

**Method & URL：** `POST /auth/client/logout`

**鉴权要求：** 需要客户登录

**关键入参：** 无

**关键响应：** 无

---

## 2. 商品管理模块 (Product)

### 2.1 商品列表查询

**接口描述：** 分页查询商品列表

**Method & URL：** `GET /api/admin/products`

**鉴权要求：** 需要登录，具备商品查看权限

**关键入参：**
- `pageNum` (Integer): 页码，默认1
- `pageSize` (Integer): 每页大小，默认10
- `name` (String): 商品名称关键词
- `categoryId` (Long): 分类ID
- `status` (Integer): 商品状态

**关键响应：**
- `records` (Array): 商品列表
  - `id` (Long): 商品ID
  - `name` (String): 商品名称
  - `categoryName` (String): 分类名称
  - `unit` (String): 单位
  - `price` (BigDecimal): 价格
  - `stock` (BigDecimal): 库存
- `total` (Long): 总数
- `size` (Integer): 每页大小
- `current` (Integer): 当前页

---

### 2.2 商品详情查询

**接口描述：** 根据ID查询商品详情

**Method & URL：** `GET /api/admin/products/{id}`

**鉴权要求：** 需要登录，具备商品查看权限

**关键入参：**
- `id` (Long): 商品ID (路径参数)

**关键响应：**
- `id` (Long): 商品ID
- `name` (String): 商品名称
- `categoryId` (Long): 分类ID
- `unit` (String): 单位
- `price` (BigDecimal): 价格
- `costPrice` (BigDecimal): 成本价
- `stock` (BigDecimal): 库存
- `minStock` (BigDecimal): 最低库存
- `status` (Integer): 状态

---

### 2.3 创建商品

**接口描述：** 创建新商品

**Method & URL：** `POST /api/admin/products`

**鉴权要求：** 需要登录，具备商品创建权限

**关键入参：**
- `name` (String): 商品名称
- `categoryId` (Long): 分类ID
- `unit` (String): 单位
- `price` (BigDecimal): 价格
- `costPrice` (BigDecimal): 成本价
- `stock` (BigDecimal): 库存
- `minStock` (BigDecimal): 最低库存

**关键响应：**
- `id` (Long): 新创建的商品ID

---

### 2.4 更新商品

**接口描述：** 更新商品信息

**Method & URL：** `PUT /api/admin/products/{id}`

**鉴权要求：** 需要登录，具备商品编辑权限

**关键入参：**
- `id` (Long): 商品ID (路径参数)
- `name` (String): 商品名称
- `categoryId` (Long): 分类ID
- `unit` (String): 单位
- `price` (BigDecimal): 价格
- `costPrice` (BigDecimal): 成本价
- `stock` (BigDecimal): 库存
- `minStock` (BigDecimal): 最低库存

**关键响应：** 无

---

### 2.5 删除商品

**接口描述：** 删除商品

**Method & URL：** `DELETE /api/admin/products/{id}`

**鉴权要求：** 需要登录，具备商品删除权限

**关键入参：**
- `id` (Long): 商品ID (路径参数)

**关键响应：** 无

---

## 3. 订单模块 (Order)

### 3.1 POS收银订单创建

**接口描述：** 创建POS收银订单

**Method & URL：** `POST /api/admin/orders/pos`

**鉴权要求：** 需要员工登录，具备订单创建权限

**关键入参：**
- `storeId` (Long): 门店ID
- `customerId` (Long): 客户ID
- `items` (Array): 商品项列表
  - `productId` (Long): 商品ID
  - `quantity` (BigDecimal): 数量
  - `unitPrice` (BigDecimal): 单价
- `paymentType` (Integer): 支付方式 (1=现金, 2=微信, 3=余额, 4=专用金, 5=优惠券, 6=赊销, 10=混合支付)
- `discountAmount` (BigDecimal): 优惠金额
- `receiveAmount` (BigDecimal): 实收金额
- `walletAuthCode` (String): 钱包验证码（余额支付时需要）
- `useSpecialFund` (Boolean): 是否使用专用金
- `couponId` (Long): 优惠券ID
- `remark` (String): 订单备注

**关键响应：**
- `id` (Long): 订单ID

---

### 3.2 订单列表查询

**接口描述：** 分页查询订单列表

**Method & URL：** `GET /api/admin/orders`

**鉴权要求：** 需要登录，具备订单查看权限

**关键入参：**
- `pageNum` (Integer): 页码，默认1
- `pageSize` (Integer): 每页大小，默认10
- `orderNo` (String): 订单号
- `customerId` (Long): 客户ID
- `status` (Integer): 订单状态
- `startDate` (String): 开始日期
- `endDate` (String): 结束日期

**关键响应：**
- `records` (Array): 订单列表
  - `id` (Long): 订单ID
  - `orderNo` (String): 订单号
  - `customerName` (String): 客户名称
  - `totalAmount` (BigDecimal): 订单总额
  - `payAmount` (BigDecimal): 实付金额
  - `paymentMethod` (Integer): 支付方式
  - `status` (Integer): 订单状态
  - `createdAt` (String): 创建时间
- `total` (Long): 总数

---

### 3.3 订单详情查询

**接口描述：** 查询订单详情

**Method & URL：** `GET /api/admin/orders/{id}`

**鉴权要求：** 需要登录，具备订单查看权限

**关键入参：**
- `id` (Long): 订单ID (路径参数)

**关键响应：**
- `id` (Long): 订单ID
- `orderNo` (String): 订单号
- `customerName` (String): 客户名称
- `customerPhone` (String): 客户电话
- `totalAmount` (BigDecimal): 订单总额
- `payAmount` (BigDecimal): 实付金额
- `paymentMethod` (Integer): 支付方式
- `status` (Integer): 订单状态
- `remark` (String): 订单备注
- `operatorName` (String): 操作员姓名
- `items` (Array): 商品明细
  - `productName` (String): 商品名称
  - `specification` (String): 规格
  - `manufacturer` (String): 厂家
  - `quantity` (BigDecimal): 数量
  - `unit` (String): 单位
  - `unitPrice` (BigDecimal): 单价
  - `amount` (BigDecimal): 金额

---

### 3.4 订单退款

**接口描述：** 对订单进行退款操作

**Method & URL：** `POST /api/admin/orders/{id}/refund`

**鉴权要求：** 需要登录，具备订单退款权限

**关键入参：**
- `id` (Long): 订单ID (路径参数)
- `refundAmount` (BigDecimal): 退款金额
- `refundReason` (String): 退款原因

**关键响应：**
- `refundId` (Long): 退款记录ID

---

## 4. 客户管理模块 (Customer)

### 4.1 客户列表查询

**接口描述：** 分页查询客户列表

**Method & URL：** `GET /api/admin/customers`

**鉴权要求：** 需要登录，具备客户查看权限

**关键入参：**
- `pageNum` (Integer): 页码，默认1
- `pageSize` (Integer): 每页大小，默认10
- `name` (String): 客户姓名关键词
- `phone` (String): 手机号码
- `type` (Integer): 客户类型

**关键响应：**
- `records` (Array): 客户列表
  - `id` (Long): 客户ID
  - `name` (String): 客户姓名
  - `phone` (String): 手机号码
  - `type` (Integer): 客户类型
  - `balance` (BigDecimal): 账户余额
  - `creditLimit` (BigDecimal): 赊销额度
  - `creditUsed` (BigDecimal): 已使用额度
- `total` (Long): 总数

---

### 4.2 客户详情查询

**接口描述：** 查询客户详情及相关信息

**Method & URL：** `GET /api/admin/customers/{id}`

**鉴权要求：** 需要登录，具备客户查看权限

**关键入参：**
- `id` (Long): 客户ID (路径参数)

**关键响应：**
- `id` (Long): 客户ID
- `name` (String): 客户姓名
- `phone` (String): 手机号码
- `type` (Integer): 客户类型
- `balance` (BigDecimal): 账户余额
- `creditLimit` (BigDecimal): 赊销额度
- `creditUsed` (BigDecimal): 已使用额度
- `points` (BigDecimal): 积分余额

---

### 4.3 创建客户

**接口描述：** 创建新客户

**Method & URL：** `POST /api/admin/customers`

**鉴权要求：** 需要登录，具备客户创建权限

**关键入参：**
- `name` (String): 客户姓名
- `phone` (String): 手机号码
- `type` (Integer): 客户类型

**关键响应：**
- `id` (Long): 新创建的客户ID

---

### 4.4 更新客户

**接口描述：** 更新客户信息

**Method & URL：** `PUT /api/admin/customers/{id}`

**鉴权要求：** 需要登录，具备客户编辑权限

**关键入参：**
- `id` (Long): 客户ID (路径参数)
- `name` (String): 客户姓名
- `phone` (String): 手机号码
- `type` (Integer): 客户类型

**关键响应：** 无

---

### 4.5 客户消费订单查询

**接口描述：** 查询客户的消费订单记录

**Method & URL：** `GET /api/admin/customers/{id}/orders`

**鉴权要求：** 需要登录，具备客户查看权限

**关键入参：**
- `id` (Long): 客户ID (路径参数)
- `pageNum` (Integer): 页码，默认1
- `pageSize` (Integer): 每页大小，默认10

**关键响应：**
- `records` (Array): 订单列表
  - `orderNo` (String): 订单号
  - `totalAmount` (BigDecimal): 订单金额
  - `payAmount` (BigDecimal): 实付金额
  - `paymentMethod` (Integer): 支付方式
  - `status` (Integer): 订单状态
  - `createdAt` (String): 下单时间

---

### 4.6 客户赊销记录查询

**接口描述：** 查询客户的赊销记录

**Method & URL：** `GET /api/admin/customers/{id}/credit/accounts`

**鉴权要求：** 需要登录，具备赊销查看权限

**关键入参：**
- `id` (Long): 客户ID (路径参数)
- `pageNum` (Integer): 页码，默认1
- `pageSize` (Integer): 每页大小，默认10

**关键响应：**
- `records` (Array): 赊销记录列表
  - `orderNo` (String): 关联订单号
  - `creditAmount` (BigDecimal): 赊销金额
  - `paidAmount` (BigDecimal): 已还金额
  - `remainingAmount` (BigDecimal): 剩余金额
  - `status` (Integer): 还款状态
  - `createdAt` (String): 赊销时间

---

## 5. 积分商城模块 (Points Mall)

### 5.1 积分商品列表

**接口描述：** 查询积分商城的商品列表

**Method & URL：** `GET /api/client/points/goods`

**鉴权要求：** 需要客户登录

**关键入参：**
- `pageNum` (Integer): 页码，默认1
- `pageSize` (Integer): 每页大小，默认10
- `categoryId` (Long): 分类ID

**关键响应：**
- `records` (Array): 商品列表
  - `id` (Long): 商品ID
  - `name` (String): 商品名称
  - `points` (BigDecimal): 所需积分
  - `price` (BigDecimal): 现金价格
  - `stock` (Integer): 库存
  - `status` (Integer): 商品状态

---

### 5.2 积分商品兑换

**接口描述：** 使用积分兑换商品

**Method & URL：** `POST /api/client/points/orders`

**鉴权要求：** 需要客户登录

**关键入参：**
- `goodsId` (Long): 积分商品ID
- `quantity` (Integer): 兑换数量
- `receiverName` (String): 收货人姓名
- `receiverPhone` (String): 收货人电话
- `receiverAddress` (String): 收货地址

**关键响应：**
- `orderId` (Long): 兑换订单ID

---

## 6. 赊销管理模块 (Credit)

### 6.1 录入还款并执行核销

**接口描述：** 为客户录入还款并自动核销欠款

**Method & URL：** `POST /api/admin/credit/repay`

**鉴权要求：** 需要登录，具备 `credit:repay` 权限

**关键入参：**
- `customerId` (Long): 客户ID
- `amount` (BigDecimal): 还款金额
- `paymentMethod` (Integer): 还款方式
- `remark` (String): 备注

**关键响应：**
- `id` (Long): 还款记录ID
- `customerId` (Long): 客户ID
- `amount` (BigDecimal): 还款金额
- `createdAt` (String): 还款时间

---

### 6.2 查询欠款单据

**接口描述：** 分页查询客户的欠款记录

**Method & URL：** `GET /api/admin/credit/accounts`

**鉴权要求：** 需要登录，具备 `credit:account:view` 权限

**关键入参：**
- `customerId` (Long): 客户ID
- `status` (Integer): 还款状态
- `pageNum` (Integer): 页码，默认1
- `pageSize` (Integer): 每页大小，默认10

**关键响应：**
- `records` (Array): 欠款记录列表
  - `id` (Long): 记录ID
  - `orderNo` (String): 订单号
  - `creditAmount` (BigDecimal): 赊销金额
  - `paidAmount` (BigDecimal): 已还金额
  - `remainingAmount` (BigDecimal): 剩余金额
  - `status` (Integer): 状态
  - `createdAt` (String): 赊销时间
- `total` (Long): 总数

---

### 6.3 查询还款记录

**接口描述：** 查询客户的还款记录

**Method & URL：** `GET /api/admin/credit/repayments`

**鉴权要求：** 需要登录，具备 `credit:repayment:view` 权限

**关键入参：**
- `customerId` (Long): 客户ID

**关键响应：**
- 还款记录列表
  - `id` (Long): 还款ID
  - `amount` (BigDecimal): 还款金额
  - `paymentMethod` (Integer): 还款方式
  - `createdAt` (String): 还款时间
  - `remark` (String): 备注

---

### 6.4 查询客户赊销统计

**接口描述：** 查询客户的赊销统计信息

**Method & URL：** `GET /api/admin/credit/statistics`

**鉴权要求：** 需要登录，具备 `credit:statistics:view` 权限

**关键入参：**
- `customerId` (Long): 客户ID

**关键响应：**
- `totalCredit` (BigDecimal): 累计赊销金额
- `totalPaid` (BigDecimal): 累计还款金额
- `remainingDebt` (BigDecimal): 剩余欠款
- `creditLimit` (BigDecimal): 赊销额度
- `availableCredit` (BigDecimal): 可用额度

---

### 6.5 调整客户赊销额度

**接口描述：** 修改客户的赊销额度

**Method & URL：** `PUT /api/admin/credit/limit`

**鉴权要求：** 需要登录，具备 `credit:limit:update` 权限

**关键入参：**
- `customerId` (Long): 客户ID
- `creditLimit` (BigDecimal): 新额度

**关键响应：** 无

---

### 6.6 生成赊销对账单

**接口描述：** 生成客户的赊销对账单

**Method & URL：** `POST /api/admin/credit/statements`

**鉴权要求：** 需要登录，具备 `credit:statement:generate` 权限

**关键入参：**
- `customerId` (Long): 客户ID
- `startDate` (String): 开始日期
- `endDate` (String): 结束日期

**关键响应：** 无

---

## 7. 库存管理模块 (Inventory)

### 7.1 库存列表查询

**接口描述：** 查询商品库存信息

**Method & URL：** `GET /api/admin/inventory`

**鉴权要求：** 需要登录，具备库存查看权限

**关键入参：**
- `pageNum` (Integer): 页码，默认1
- `pageSize` (Integer): 每页大小，默认10
- `productName` (String): 商品名称关键词
- `storeId` (Long): 门店ID

**关键响应：**
- `records` (Array): 库存记录
  - `productId` (Long): 商品ID
  - `productName` (String): 商品名称
  - `storeId` (Long): 门店ID
  - `storeName` (String): 门店名称
  - `quantity` (BigDecimal): 当前库存
  - `minStock` (BigDecimal): 最低库存
  - `status` (String): 库存状态

---

### 7.2 库存调整

**接口描述：** 调整商品库存数量

**Method & URL：** `POST /api/admin/inventory/adjust`

**鉴权要求：** 需要登录，具备库存调整权限

**关键入参：**
- `productId` (Long): 商品ID
- `storeId` (Long): 门店ID
- `quantity` (BigDecimal): 调整数量（正数增加，负数减少）
- `reason` (String): 调整原因
- `remark` (String): 备注

**关键响应：**
- `id` (Long): 库存调整记录ID

---

### 7.3 库存盘点

**接口描述：** 执行库存盘点

**Method & URL：** `POST /api/admin/inventory/check`

**鉴权要求：** 需要登录，具备库存盘点权限

**关键入参：**
- `storeId` (Long): 门店ID
- `items` (Array): 盘点商品列表
  - `productId` (Long): 商品ID
  - `actualQuantity` (BigDecimal): 实际数量
- `remark` (String): 盘点备注

**关键响应：**
- `checkId` (Long): 盘点记录ID

---

## 8. 售后管理模块 (After Sales)

### 8.1 创建售后申请

**接口描述：** 客户发起售后申请

**Method & URL：** `POST /api/client/after-sales`

**鉴权要求：** 需要客户登录

**关键入参：**
- `orderId` (Long): 原订单ID
- `type` (Integer): 售后类型（1=退货，2=换货，3=维修）
- `reason` (String): 售后原因
- `description` (String): 问题描述
- `items` (Array): 售后商品
  - `orderItemId` (Long): 订单商品项ID
  - `quantity` (Integer): 数量

**关键响应：**
- `id` (Long): 售后申请ID

---

### 8.2 售后申请列表

**接口描述：** 查询售后申请列表

**Method & URL：** `GET /api/admin/after-sales`

**鉴权要求：** 需要登录，具备售后查看权限

**关键入参：**
- `pageNum` (Integer): 页码，默认1
- `pageSize` (Integer): 每页大小，默认10
- `status` (Integer): 售后状态

**关键响应：**
- `records` (Array): 售后申请列表
  - `id` (Long): 申请ID
  - `orderNo` (String): 订单号
  - `customerName` (String): 客户姓名
  - `type` (Integer): 售后类型
  - `status` (Integer): 申请状态
  - `createdAt` (String): 申请时间

---

### 8.3 处理售后申请

**接口描述：** 审核和处理售后申请

**Method & URL：** `PUT /api/admin/after-sales/{id}`

**鉴权要求：** 需要登录，具备售后处理权限

**关键入参：**
- `id` (Long): 售后申请ID (路径参数)
- `status` (Integer): 新状态
- `handleResult` (String): 处理结果
- `refundAmount` (BigDecimal): 退款金额

**关键响应：** 无

---

## 9. 员工管理模块 (Staff)

### 9.1 员工列表查询

**接口描述：** 查询员工列表

**Method & URL：** `GET /api/admin/staff`

**鉴权要求：** 需要登录，具备员工管理权限

**关键入参：**
- `pageNum` (Integer): 页码，默认1
- `pageSize` (Integer): 每页大小，默认10
- `name` (String): 员工姓名关键词
- `status` (Integer): 员工状态

**关键响应：**
- `records` (Array): 员工列表
  - `id` (Long): 员工ID
  - `name` (String): 员工姓名
  - `code` (String): 员工编码
  - `phone` (String): 手机号码
  - `role` (String): 角色
  - `status` (Integer): 状态

---

### 9.2 创建员工

**接口描述：** 创建新员工

**Method & URL：** `POST /api/admin/staff`

**鉴权要求：** 需要登录，具备员工创建权限

**关键入参：**
- `name` (String): 员工姓名
- `code` (String): 员工编码
- `phone` (String): 手机号码
- `password` (String): 密码
- `roleId` (Long): 角色ID

**关键响应：**
- `id` (Long): 新员工ID

---

### 9.3 更新员工

**接口描述：** 更新员工信息

**Method & URL：** `PUT /api/admin/staff/{id}`

**鉴权要求：** 需要登录，具备员工编辑权限

**关键入参：**
- `id` (Long): 员工ID (路径参数)
- `name` (String): 员工姓名
- `phone` (String): 手机号码
- `roleId` (Long): 角色ID
- `status` (Integer): 状态

**关键响应：** 无

---

## 10. 权限管理模块 (RBAC)

### 10.1 角色列表查询

**接口描述：** 查询角色列表

**Method & URL：** `GET /api/admin/roles`

**鉴权要求：** 需要登录，具备角色管理权限

**关键入参：**
- `pageNum` (Integer): 页码，默认1
- `pageSize` (Integer): 每页大小，默认10

**关键响应：**
- `records` (Array): 角色列表
  - `id` (Long): 角色ID
  - `name` (String): 角色名称
  - `code` (String): 角色编码
  - `permissions` (Array): 权限列表

---

### 10.2 创建角色

**接口描述：** 创建新角色

**Method & URL：** `POST /api/admin/roles`

**鉴权要求：** 需要登录，具备角色创建权限

**关键入参：**
- `name` (String): 角色名称
- `code` (String): 角色编码
- `permissionIds` (Array): 权限ID列表

**关键响应：**
- `id` (Long): 新角色ID

---

### 10.3 权限列表查询

**接口描述：** 查询权限列表

**Method & URL：** `GET /api/admin/permissions`

**鉴权要求：** 需要登录，具备权限管理权限

**关键入参：** 无

**关键响应：**
- 权限树形结构列表
  - `id` (Long): 权限ID
  - `name` (String): 权限名称
  - `code` (String): 权限编码
  - `type` (Integer): 权限类型
  - `parentId` (Long): 父权限ID
  - `children` (Array): 子权限

---

## 11. 报表模块 (Report)

### 11.1 销售报表

**接口描述：** 查询销售统计报表

**Method & URL：** `GET /api/admin/reports/sales`

**鉴权要求：** 需要登录，具备报表查看权限

**关键入参：**
- `startDate` (String): 开始日期
- `endDate` (String): 结束日期
- `storeId` (Long): 门店ID
- `groupBy` (String): 分组方式（day/month）

**关键响应：**
- `totalSales` (BigDecimal): 总销售额
- `totalOrders` (Integer): 总订单数
- `data` (Array): 统计数据
  - `date` (String): 日期
  - `sales` (BigDecimal): 销售额
  - `orders` (Integer): 订单数
  - `customers` (Integer): 客户数

---

### 11.2 库存报表

**接口描述：** 查询库存统计报表

**Method & URL：** `GET /api/admin/reports/inventory`

**鉴权要求：** 需要登录，具备报表查看权限

**关键入参：**
- `storeId` (Long): 门店ID
- `lowStock` (Boolean): 只显示库存不足商品

**关键响应：**
- `totalProducts` (Integer): 商品总数
- `lowStockProducts` (Integer): 库存不足商品数
- `data` (Array): 商品库存详情
  - `productName` (String): 商品名称
  - `currentStock` (BigDecimal): 当前库存
  - `minStock` (BigDecimal): 最低库存
  - `status` (String): 库存状态

---

### 11.3 财务报表

**接口描述：** 查询财务统计报表

**Method & URL：** `GET /api/admin/reports/finance`

**鉴权要求：** 需要登录，具备财务报表权限

**关键入参：**
- `startDate` (String): 开始日期
- `endDate` (String): 结束日期
- `type` (String): 报表类型（income/expense/profit）

**关键响应：**
- `totalIncome` (BigDecimal): 总收入
- `totalExpense` (BigDecimal): 总支出
- `netProfit` (BigDecimal): 净利润
- `data` (Array): 明细数据

---

## 12. 字典模块 (Dict)

### 12.1 字典列表查询

**接口描述：** 查询字典列表

**Method & URL：** `GET /api/admin/dict`

**鉴权要求：** 无需鉴权（公共接口）

**关键入参：**
- `type` (String): 字典类型

**关键响应：**
- 字典项列表
  - `value` (String): 值
  - `label` (String): 标签
  - `type` (String): 类型

---

### 12.2 字典类型查询

**接口描述：** 查询字典类型列表

**Method & URL：** `GET /api/admin/dict/types`

**鉴权要求：** 需要登录，具备字典管理权限

**关键入参：** 无

**关键响应：**
- 字典类型列表
  - `type` (String): 类型编码
  - `name` (String): 类型名称
  - `description` (String): 描述

---

## 13. 采购管理模块 (Purchase)

### 13.1 采购订单列表

**接口描述：** 查询采购订单列表

**Method & URL：** `GET /api/admin/purchase/orders`

**鉴权要求：** 需要登录，具备采购管理权限

**关键入参：**
- `pageNum` (Integer): 页码，默认1
- `pageSize` (Integer): 每页大小，默认10
- `status` (Integer): 订单状态

**关键响应：**
- `records` (Array): 采购订单列表
  - `id` (Long): 订单ID
  - `orderNo` (String): 订单号
  - `supplierName` (String): 供应商名称
  - `totalAmount` (BigDecimal): 订单总额
  - `status` (Integer): 订单状态
  - `createdAt` (String): 创建时间

---

### 13.2 创建采购订单

**接口描述：** 创建采购订单

**Method & URL：** `POST /api/admin/purchase/orders`

**鉴权要求：** 需要登录，具备采购创建权限

**关键入参：**
- `supplierId` (Long): 供应商ID
- `items` (Array): 采购商品列表
  - `productId` (Long): 商品ID
  - `quantity` (BigDecimal): 采购数量
  - `unitPrice` (BigDecimal): 单价
- `remark` (String): 备注

**关键响应：**
- `id` (Long): 采购订单ID

---

## 14. 门店管理模块 (Store)

### 14.1 门店列表查询

**接口描述：** 查询门店列表

**Method & URL：** `GET /api/admin/stores`

**鉴权要求：** 需要登录，具备门店查看权限

**关键入参：**
- `pageNum` (Integer): 页码，默认1
- `pageSize` (Integer): 每页大小，默认10

**关键响应：**
- `records` (Array): 门店列表
  - `id` (Long): 门店ID
  - `name` (String): 门店名称
  - `address` (String): 地址
  - `phone` (String): 电话
  - `status` (Integer): 状态

---

### 14.2 创建门店

**接口描述：** 创建新门店

**Method & URL：** `POST /api/admin/stores`

**鉴权要求：** 需要登录，具备门店创建权限

**关键入参：**
- `name` (String): 门店名称
- `address` (String): 地址
- `phone` (String): 电话
- `managerId` (Long): 店长ID

**关键响应：**
- `id` (Long): 新门店ID

---

## 15. 套餐管理模块 (Product Package)

### 15.1 套餐列表查询

**接口描述：** 查询商品套餐列表

**Method & URL：** `GET /api/admin/product-packages`

**鉴权要求：** 需要登录，具备套餐查看权限

**关键入参：**
- `pageNum` (Integer): 页码，默认1
- `pageSize` (Integer): 每页大小，默认10

**关键响应：**
- `records` (Array): 套餐列表
  - `id` (Long): 套餐ID
  - `name` (String): 套餐名称
  - `price` (BigDecimal): 套餐价格
  - `originalPrice` (BigDecimal): 原价
  - `status` (Integer): 状态
  - `items` (Array): 套餐商品

---

### 15.2 创建套餐

**接口描述：** 创建商品套餐

**Method & URL：** `POST /api/admin/product-packages`

**鉴权要求：** 需要登录，具备套餐创建权限

**关键入参：**
- `name` (String): 套餐名称
- `price` (BigDecimal): 套餐价格
- `items` (Array): 套餐商品
  - `productId` (Long): 商品ID
  - `quantity` (BigDecimal): 数量

**关键响应：**
- `id` (Long): 新套餐ID

---

## 16. 钱包管理模块 (Wallet)

### 16.1 生成并发送钱包验证码

**接口描述：** 小程序调用生成钱包验证码（用于余额支付）

**Method & URL：** `POST /api/client/public/wallet/auth-code/send`

**鉴权要求：** 无需鉴权（公共接口）

**关键入参：**
- `customerId` (Long): 客户ID

**关键响应：**
- `status` (String): 状态
- `authCode` (String): 验证码（测试环境返回）
- `message` (String): 消息

---

### 16.2 验证并消费钱包验证码

**接口描述：** 验证钱包验证码并标记为已使用

**Method & URL：** `POST /api/client/public/wallet/auth-code/verify`

**鉴权要求：** 无需鉴权（公共接口）

**关键入参：**
- `customerId` (Long): 客户ID
- `authCode` (String): 验证码
- `orderId` (Long): 订单ID（可选）
- `amount` (BigDecimal): 支付金额（可选）

**关键响应：**
- `status` (String): 状态
- `message` (String): 消息

---

## 17. 打印模块 (Print)

### 17.1 生成小票PDF

**接口描述：** 生成订单小票PDF文件

**Method & URL：** `POST /api/admin/print/receipt`

**鉴权要求：** 需要登录

**关键入参：**
- `orderId` (Long): 订单ID
- `paperWidth` (Integer): 纸张宽度（可选，默认80）
- `orientation` (String): 方向（portrait/landscape，可选）

**关键响应：**
- `fileUrl` (String): PDF文件URL

---

## 18. 其他模块

### 18.1 健康检查

**接口描述：** 系统健康检查接口

**Method & URL：** `GET /health`

**鉴权要求：** 无需鉴权

**关键入参：** 无

**关键响应：**
- `status` (String): 状态
- `timestamp` (String): 时间戳

---

### 18.2 日志查询

**接口描述：** 查询系统操作日志

**Method & URL：** `GET /api/admin/logs`

**鉴权要求：** 需要登录，具备日志查看权限

**关键入参：**
- `pageNum` (Integer): 页码，默认1
- `pageSize` (Integer): 每页大小，默认10
- `startDate` (String): 开始日期
- `endDate` (String): 结束日期
- `userId` (Long): 操作用户ID

**关键响应：**
- `records` (Array): 日志列表
  - `id` (Long): 日志ID
  - `userName` (String): 操作用户名
  - `action` (String): 操作类型
  - `resource` (String): 操作资源
  - `ip` (String): IP地址
  - `createdAt` (String): 操作时间

---

## 附录

### 支付方式枚举

- `1`: 现金
- `2`: 微信
- `3`: 余额
- `4`: 专用金
- `5`: 优惠券
- `6`: 赊销
- `10`: 混合支付

### 订单状态枚举

- `1`: 待支付
- `2`: 已支付
- `3`: 已发货
- `4`: 已完成
- `5`: 已取消
- `6`: 退款中
- `7`: 已退款

### 客户类型枚举

- `1`: 普通客户
- `2`: VIP客户

### 权限编码说明

权限编码格式：`模块:操作:范围`

- `product:view` - 商品查看
- `product:create` - 商品创建
- `product:edit` - 商品编辑
- `product:delete` - 商品删除
- `order:view` - 订单查看
- `order:create` - 订单创建
- `customer:view` - 客户查看
- `credit:account:view` - 赊销台账查看
- `credit:repayment:view` - 还款记录查看
- `inventory:view` - 库存查看
- `inventory:adjust` - 库存调整

---

*本文档基于系统后端代码自动生成，最后更新时间：2025-12-28*