## Module 12：绩效模块（Performance Module）交付说明

### 1. 交付清单
| 子系统 | 文件 / 组件 | 说明 |
| ------ | ----------- | ---- |
| `core-infrastructure` | `V45__performance_enhanced.sql` | 新增/扩展绩效相关表、一次性二维码、成本快照 |
| `core-domain` | `domain/performance/**` | 领域模型（规则、流水、事件幂等、月目标、工资单、关系、二维码） |
| `core-infrastructure` | `infrastructure/performance/**` | Mapper + Repository 实现 |
| `core-application` | `application/performance/**` | DTO、应用服务、事件监听、作业、二维码服务、配置 |
| `core-application` | `application/order/service/OrderApplicationService` | 发布 `OrderFinishedEvent` / `OrderRefundedEvent`、写成本快照 |
| `api-admin` | `PerformanceAdminController`, `PerformanceStaffController`, `QrAdminController` | 管理端接口：提成规则、工资单、员工业绩看板、生成QR |
| `api-client` | `QrClientController` | 小程序二维码绑定接口 |
| `core-application` | `test/application/performance/PerformanceModuleTest` | 绩效模块综合单测 |
| `docs` | `PERFORMANCE_MODULE_DELIVERY.md` | 本交付文档 |

### 2. 业务功能覆盖
1. **事件驱动绩效计算**
   - `OrderFinishedEvent`：逐行计算销售提成，基于 `order_item.cost_price_snapshot`，落表 `staff_performance_log` 并控制 `MAX_COMMISSION_RATIO`。
   - `OrderRefundedEvent`：按比例冲减正向绩效，生成 `REFUND_DEDUCT` 负流水。
   - `CustomerRechargeEvent`：根据 `commission_rule`（类别=RECHARGE）发放引导奖励。
   - `CustomerFirstEffectiveEvent`：新客首单满足阈值后一次性发放拉新奖励，并写 `staff_customer_relation.first_effective_at`。

2. **提成规则体系**
   - `commission_rule` 扩展字段：`rule_category`、`target_id`、`calc_mode(FIXED/PROFIT_PERCENT/SALES_PERCENT)`、`calc_value`、`max_per_item/max_per_order`、`enabled`。
   - 支持「销售」、「套餐推广」、「拉新」、「充值引导」、「品牌推广」等多类型规则，优先级 + 员工/商品定向。

3. **一次性二维码防作弊**
   - `one_time_qr` 记录：一次性 token、门店、员工、临时订单标识、状态（CREATED/BOUND/USED/EXPIRED）、过期时间。
   - `OneTimeQrService`：生成二维码（90s 失效）、绑定客户（写 `CustomerBindApprovedEvent`）。
   - 扫码→小程序 `qrBind`，后端校验 token，一次性写 customer/staff 关系并触发审批事件。

4. **拉新关系与审核**
   - `staff_customer_relation`：记录绑定来源、状态（pending/approved/rejected）、首单生效时间 `first_effective_at`。
   - 审核通过事件：`CustomerBindApprovedEvent`（默认 QR 自动通过，可扩展人工流程）。
   - 触发逻辑：绑定来源=QR、与订单 staff 匹配、首单满足阈值（300 元/30 元毛利/白名单品类+200 元）时发布 `CustomerFirstEffectiveEvent`。

5. **绩效流水 & 幂等**
   - 统一写 `staff_performance_log`：字段含 `biz_type`、`calc_desc`、`need_review`。
   - 幂等表 `performance_event_record(event_key)`：所有事件先写入，重复消费自动跳过。
   - 安全阈值：`commission <= profit * MAX_RATIO(默认0.2)`，超过即命中待复核。

6. **月度目标 & 工资单**
   - `staff_monthly_goal`：配置销售/提成/新客目标。
   - `staff_salary_bill`：`PerformanceSettlementJob` 每月 1 日 04:00 汇总上月流水、销售额（按 staff+month 汇总 `order.pay_amount`）、新客数量，生成工资单，支持确认。
   - `PerformanceApplicationService` + `PerformanceAdminController` 提供规则管理、目标配置、工资单确认接口。

7. **员工业绩看板**
   - `PerformanceStaffController`：查询个人月度提成、销售额、新客、流水明细。
   - 结果来自工资单与 `staff_performance_log`。

### 3. 数据库变更（V45）
```
ALTER TABLE order_item ADD COLUMN cost_price_snapshot DECIMAL(10,2);
CREATE TABLE staff_performance_log (... biz_type, amount, need_review ...);
CREATE TABLE performance_event_record (... event_key UNIQUE ...);
CREATE TABLE staff_monthly_goal (... target_sales_amount ...);
CREATE TABLE staff_salary_bill (... total_commission ...);
CREATE TABLE staff_customer_relation (... bind_source, first_effective_at ...);
CREATE TABLE one_time_qr (... qr_token UNIQUE, expire_at ...);
ALTER TABLE commission_rule ADD rule_category / target_id / calc_mode / calc_value / caps / enabled;
```

### 4. API 速查
| 接口 | Method | Path | 权限 | 说明 |
| ---- | ------ | ---- | ---- | ---- |
| 提成规则-创建 | `POST` | `/api/admin/performance/rule` | ADMIN | 新增/修改规则 |
| 提成规则-列表 | `GET` | `/api/admin/performance/rule` | ADMIN | 条件查询 |
| 设置月目标 | `POST` | `/api/admin/performance/goal` | ADMIN | 配置员工月目标 |
| 工资单分页 | `GET` | `/api/admin/performance/bill` | ADMIN | month/status 分页 |
| 工资单确认 | `POST` | `/api/admin/performance/bill/{id}/confirm` | ADMIN | 状态改为 confirmed |
| 员工业绩看板 | `GET` | `/api/staff/performance/dashboard` | STAFF/ADMIN | 返回当月汇总 |
| 绩效流水 | `GET` | `/api/staff/performance/logs` | STAFF/ADMIN | 分页流水 |
| 生成一次性 QR | `POST` | `/api/admin/qr/create` | STAFF/ADMIN | 入参 orderTempToken/staff/store |
| 查询 QR 状态 | `GET` | `/api/admin/qr/status` | STAFF/ADMIN | 轮询绑定状态 |
| 小程序扫码绑定 | `POST` | `/api/client/auth/qrBind` | Client | 绑定客户并触发事件 |

所有 DTO/VO 均已补充 `@Schema` 注解，Controller 补充 `@Operation`。

### 5. 事件与幂等
| 事件 | 触发点 | Listener 行为 |
| ---- | ------- | ------------- |
| `OrderFinishedEvent` | POS 下单完成 / 自提核销 / 其他流转 | 逐行计算销售提成；校验成本快照；触发拉新判定 |
| `OrderRefundedEvent` | `OrderApplicationService.refund` | 生成负流水（比例冲减） |
| `CustomerRechargeEvent` | `WalletRechargeService.recharge` | 依据规则发充值奖励 |
| `CustomerBindApprovedEvent` | `OneTimeQrService.bind` | 写/更新 `staff_customer_relation` |
| `CustomerFirstEffectiveEvent` | 满足拉新阈值 | 发放拉新奖励流水 |

幂等：`performance_event_record.event_key = type:bizId[:extra]`，唯一索引确保只执行一次。

### 6. 测试
| 用例 | 说明 |
| ---- | ---- |
| `PerformanceModuleTest#calculationTest` | 验证提成计算命中毛利上限 |
| `PerformanceModuleTest#rechargeRewardTest` | 充值引导奖励写流水 |
| `PerformanceModuleTest#newCustomerTest` | 首次生效事件触发拉新奖励 |
| `PerformanceModuleTest#refundDeductTest` | 退款按比例写负流水 |
| `PerformanceModuleTest#monthlySettlementTest` | 月结任务生成工资单 |

执行命令：
```
mvn -pl core-application test "-Dtest=PerformanceModuleTest"
```

### 7. 上线检查清单
1. V45 Flyway 脚本已在测试环境验证，包含所有新增表与列。
2. `order_item.cost_price_snapshot` 历史回填脚本（与采购模块同步）确保老订单毛利正确。
3. POS、自提流程验证 `OrderFinishedEvent` 可正常触发，日志中可见 TraceId。
4. 退款流程验证 `staff_performance_log` 负流水写入。
5. 小程序扫码→POS 绑定→首单完成全链路验证（包含过期、重复扫码）。
6. `PerformanceSettlementJob` 在测试环境手动触发一次，确认工资单生成与确认接口流程。
7. 新增配置项 `performance.max-commission-ratio` 等在 `application-*.yml` 设置合理默认值（可选）。

### 8. 已知风险 / TODO
1. `OneTimeQrService` 目前未对 QR 使用次数做并发锁，极端情况下需结合 Redis 分布式锁。
2. 绩效规则暂不支持批量导入与生效区间，后续需叠加审批流。
3. `StaffPerformanceApplicationService.dashboard` 的目标值来源为 `staff_monthly_goal`，如需更多指标（环比、品类占比）需拓展报表层。
4. `PerformanceSettlementJob` 依赖 `order.pay_amount` 汇总，如有拆单/合单场景需增强。
5. `CustomerFirstEffectiveEvent` 当前阈值通过配置项控制，后续建议接入运营后台动态配置。

---
**交付日期：** 2025-01-XX  
**版本：** 1.0.0  
**状态：** ✅ 已完成

