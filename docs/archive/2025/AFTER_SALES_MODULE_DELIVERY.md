> 文档状态：归档历史资料，不是现行开发/部署/建库依据。现行入口见[统一文档中心](../../current/README.md)。

## Module 09：售后模块（After-Sales Module）交付说明

### 1. 交付清单
| 子系统 | 文件 / 组件 | 说明 |
| ------ | ----------- | ---- |
| `core-infrastructure` | `V37__after_sales_simple.sql` | 新增售后主/明细表，含字段、索引及外键约束 |
| `core-domain` | `domain/after_sales/**` | `AfterSales/AfterSalesItem` 聚合 & 仓储接口、单号领域服务 |
| `core-infrastructure` | `infrastructure/after_sales/**` | 售后仓储实现（Mapper + RepositoryImpl） |
| `core-application` | `application/after_sales/**` | DTO、错误码、`AfterSalesApplicationService`、库存/退款编排 |
| `api-client` | `AfterSalesClientController` | 小程序售后 apply/cancel API |
| `api-admin` | `AfterSalesAdminController` | 管理端 create/audit/refund API |
| `scripts` | `repair_flyway_v36.sql` | V36 校验修复脚本（若历史环境需要） |
| `docs` | `AFTER_SALES_MODULE_DELIVERY.md` | 本交付文档 |

### 2. 业务功能覆盖
1. **小程序拦截（Type=1）**
   - 状态仅允许 `WAIT_SHIP`/`WAIT_PICKUP`。
   - 创建待审核售后，提交全额退款明细。
   - 审核通过后，自动回补库存并将订单标记为 `CANCELLED`。
2. **管理端退货（Type=2）**
   - 订单状态需 `SHIPPED/FINISHED`，赊销订单拦截。
   - 店员创建时即视为“审核通过 & 收货完成”，直接进入 `WAIT_REFUND`。
   - 自动调用库存入库：`InventoryApplicationService.inbound`，source=`SALES_RETURN`，costPrice 取商品加权成本（空则回退订单单价）。
3. **退款链路**
   - `refund()` 调用 `OrderPaymentService.refund`，复用订单模块 LIFO 退款策略。
   - 若本次退款金额 **等于** 订单实付金额：订单状态标记为 `8-已退款 (REFUNDED)`。
   - 若本次退款金额 **小于** 订单实付金额：订单状态标记为 `9-部分退款 (PART_REFUNDED)`。
   - 成功后将售后状态置 `FINISHED`，订单状态置 `REFUNDED`。

### 3. 数据库变更
#### 表结构
```
after_sales
  ├─ after_sales_no (唯一)
  ├─ order_id / customer_id / store_id
  ├─ type (1-拦截; 2-退货)
  ├─ status (10/40/50/90)
  ├─ refund_amount, reason, audit_remark, audit_by/time, refund_by/time

after_sales_item
  ├─ after_sales_id, order_item_id, product_id
  ├─ quantity, refund_amount
```
#### 索引与约束
- `uk_after_sales_no`、`idx_order_status`（order_id + status）
- 外键指向 `order` / `order_item` / `product`

### 4. API 速查
| 接口 | Method | Path | 权限 | 说明 |
| ---- | ------ | ---- | ---- | ---- |
| 客户端-申请 | `POST` | `/api/client/after-sales/apply` | 小程序 | 校验未发货状态，创建 Type=1 |
| 客户端-撤销 | `POST` | `/api/client/after-sales/{id}/cancel` | 小程序 | 仅待审核可撤销 |
| 管理端-创建 | `POST` | `/api/admin/after-sales` | Staff | 线下退货，自动待退款 |
| 管理端-审核 | `POST` | `/api/admin/after-sales/{id}/audit` | Staff | 审核小程序申请（通过触发库存回补） |
| 管理端-退款 | `POST` | `/api/admin/after-sales/{id}/refund` | Staff | 触发原路退款 + 状态 FINISHED |

请求 DTO：
- `ClientApplyCommand { orderId, reason }`
- `AdminReturnCommand { orderId, refundAmount, reason, items[{orderItemId, quantity, refundAmount}] }`
- `AfterSalesAuditCommand { afterSalesId, approved, remark }`
- `AfterSalesRefundCommand { afterSalesId, refundAmount? }`

### 5. 关键校验
| 场景 | 校验 |
| ---- | ---- |
| 小程序申请 | 订单状态 ∈ {WAIT_SHIP, WAIT_PICKUP}，且无处理中售后 |
| 管理端退货 | 订单状态 ∈ {SHIPPED, FINISHED}，`payment_method ≠ 5/6`（赊销禁用），无处理中售后 |
| 审核通过 | 自动回补库存（调用 Inventory），Type=1 同时 `order.markAsCancelled()` |
| 退款 | `refundAmount ≤ afterSales.refundAmount`，调用 `OrderPaymentService.refund` |

### 6. 测试
| 用例 | 说明 |
| ---- | ---- |
| `ClientLimitTest` | 模拟已发货订单发起申请 -> 期望抛异常 |
| `AdminReturnTest` | 管理端退货 -> 验证库存回补（调用 inbound）与退款调用 |
| `CreditBanTest` | 赊销订单退货 -> 期望抛异常 |

执行命令：
```
mvn -pl core-application test "-Dtest=ClientLimitTest,AdminReturnTest,CreditBanTest"
```

### 7. Flyway 注意事项
- 若旧环境存在 V36 checksum 不一致，需执行 `scripts/repair_flyway_v36.sql` 或手动更新 `flyway_schema_history` 中版本 36 的记录（checksum = `-1331276597`）。
- 部署售后模块需执行 `V37__after_sales_simple.sql`。

### 8. 仍待评估 / 风险
1. 退款链路目前只调用订单支付服务，若未来引入实单微信退款，需要补充三方 API。
2. 库存回补为即时入库，未做批次合并策略（默认生成新批次），后续可接入真实批次策略。
3. 测试覆盖集中在核心规则，若需联调需补充集成测试（含数据库校验）。

