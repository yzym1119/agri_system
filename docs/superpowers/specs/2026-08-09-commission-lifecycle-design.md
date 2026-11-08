> 文档状态：历史阶段设计/计划，非当前全量事实；按文件日期和提交阅读。被覆盖部分以[统一文档中心](../../current/README.md)为准；未覆盖且仍有效的规则须对照当前代码确认，不直接执行旧迁移/模式切换。

# 提成生命周期:冻结 → 解冻 → 实发 → 回冲

- **状态**:设计稿(待评审)
- **日期**:2026-08-09
- **模块**:core-domain / core-application / core-infrastructure / api-admin(无新端点)
- **优先级**:P1

## 1. 背景与问题

当前提成模型是 **"即算即发无冻结"** 的扁平账本:

- 订单核销完成(`OrderFinishedEvent`)→ `PerformanceEventListener` 按 `CommissionRule` 立即写 `SALES_COMMISSION` 提成 log。
- 退款(`OrderRefundedEvent`)→ 按比例写 `REFUND_DEDUCT` 负数对冲。
- 月初定时(`PerformanceSettlementJob`,1 号 04:00)把当月 log 按 `created_at` 求和落成 `StaffSalaryBill`(status=0 草稿)。
- 管理员 `confirmSalaryBill` 把账单 status 0→1(`confirmedAt`)= "实发"。
- 看板 `StaffCommissionDashboardService` 用 `销售额×2%` 估算,**不读提成 log**(非本方案范围)。

**缺口**:提成一产生即等同"已赚取/将发放",没有冻结期,无法应对"退货窗口内退款导致已计提成需回退"的会计正确性;跨期退款(已实发后退款)也无明确回冲口径。

**目标**:在仅销售提成(`SALES_COMMISSION`)上引入 crmeb 风格的 4 阶段生命周期——**冻结 → 到期解冻 → 账单确认(实发) → 退款回冲**,且最小改动、可灰度、不破坏现有结算与幂等。

## 2. 业务决策(已与产品确认)

| 维度 | 决策 |
|---|---|
| 解冻触发 | **到期自动解冻**(订单完成后过 N 天退货窗口) |
| 冻结期 N | **15 天**(可配置) |
| 实发定义 | **月度账单确认**(复用 `confirmSalaryBill`,status 0→1);不动钱包/资金模块 |
| 冻结范围 | **仅 `SALES_COMMISSION`**;`RECHARGE_GUIDE_REWARD`/`NEW_CUSTOMER_REWARD` 等保持即算即发 |
| 跨期回冲 | **回冲进下期账单**(已实发后退款,负数落员工下一个未确认账单;不重算历史账单) |
| 已知后果 | 提成按"解冻月"入账后,与账单 `totalSales`/`newCustomerCount`(按订单月)会有轻微月度错位。**金额(提成)准确**,销售额为辅助指标,保持订单月口径。**接受现状。** |

## 3. 架构方案(方案 A:就地状态机)

在现有 `staff_performance_log` 表加 2 列(`status` / `freeze_until`),不新增表、不改账本主键。

**统一结算口径**:月度账单 = `SUM(amount) WHERE status=2 AND freeze_until BETWEEN [月初, 月末]`。一个查询同时覆盖本月解冻的销售提成(正数)、本月产生的退款扣减(`REFUND_DEDUCT`,freeze_until=创建时间,天生落本月)、本月发生的充值/拉新奖励(非冻结类,同上)。排除冻结中(status=1)。**回冲进下期**是 `freeze_until` 列的天然副产物,无需特殊代码。

**关键设计:退款负数继承被退正数的冻结态**。任何退款都写 `REFUND_DEDUCT` 负数(保持 append-only),但负数复制原正数的 `(status, freeze_until)`:
- 退款时原正数仍**冻结** → 负数 status=1、freeze_until=原正数.freeze_until → 两者**同日解冻**,解冻后净额自动正确(+100 −30=+70)。
- 退款时原正数**已解冻** → 负数 status=2、freeze_until=now → 落当期/下期账单(回冲进下期)。

该规则**统一处理全额与部分退款**(现有 `onOrderRefunded` 已支持按 `refundItems` 行级部分退款),且零账本行变更(mutation)、审计完整。

## 4. 数据模型

### 4.1 迁移 `V118__commission_freeze_lifecycle.sql`

```sql
ALTER TABLE staff_performance_log
  ADD COLUMN status TINYINT NOT NULL DEFAULT 2
      COMMENT '1-冻结 2-已解冻' AFTER need_review,
  ADD COLUMN freeze_until DATETIME NULL
      COMMENT '解冻时点:冻结类=created_at+冻结期,非冻结类=created_at';
-- 存量行:status 默认 2(已解冻),freeze_until 回填 created_at,保持原"按发生月计入"行为
UPDATE staff_performance_log SET freeze_until = created_at WHERE freeze_until IS NULL;
ALTER TABLE staff_performance_log ADD INDEX idx_freeze (status, freeze_until);
```

`DEFAULT 2` 是关键:存量行与所有"非销售"类型天生 `已解冻`;仅新写的 `SALES_COMMISSION`(且 `freezeEnabled=true`)会被监听器置为 `1`。

### 4.2 状态机(仅 SALES_COMMISSION 走完整流转)

```
                  onOrderFinished(freezeEnabled=true)
        ┌──────────────────────────────┐
        ▼                               │
   ┌─────────┐  日解冻任务(freeze_until≤now) ┌─────────┐
   │ 1 冻结  │ ──────────────────────────▶ │ 2 已解冻 │
   └─────────┘                              └─────────┘
                                                 │
                                      任意退款(全额/部分)
                                                 │
                                                 ▼
                                   写 REFUND_DEDUCT 负数,
                                   继承原正数 (status, freeze_until)
                                   → 解冻后净额自动正确 / 或落下期账单
```

- 非销售类型(RECHARGE/NEW_CUSTOMER/ADJUSTMENT 等):创建即 status=2、freeze_until=created_at,无状态流转。
- 日解冻任务 `status=1 AND freeze_until<=now → 2`,正负数一起解冻(同 freeze_until)。

### 4.3 新枚举 `PerformanceLogStatus`

`core-domain/.../performance/model/PerformanceLogStatus.java`,与 `PerformanceBizType` 同级:
```java
public enum PerformanceLogStatus {
    FROZEN(1), UNFROZEN(2);
    // getCode() / fromCode()
}
```

## 5. 写入路径(监听器改动)

### 5.1 `PerformanceEventListener.onOrderFinished`

仅 `SALES_COMMISSION` log 构造块加 2 行(其余逻辑不动):
```java
if (properties.isFreezeEnabled()) {
    logEntry.setStatus(PerformanceLogStatus.FROZEN.getCode());           // 1
    logEntry.setFreezeUntil(LocalDateTime.now().plusDays(properties.getFreezeDays()));
} else {
    logEntry.setStatus(PerformanceLogStatus.UNFROZEN.getCode());         // 2 = 即算即发
    logEntry.setFreezeUntil(LocalDateTime.now());
}
```
`onCustomerRecharge` / `onCustomerFirstEffective` 写 log 时显式置 `status=2, freeze_until=now`(计入发生月),不依赖 DB 默认。

### 5.2 `PerformanceEventListener.onOrderRefunded`

现有"按比例造负数"循环里,给负数补继承字段(`staffId`/`bizType=REFUND_DEDUCT`/`amount=deductAmount.negate()` 等不变):
```java
negative.setStatus(logEntry.getStatus());           // 继承原正数冻结态
negative.setFreezeUntil(logEntry.getFreezeUntil()); // 继承解冻时点
```
`markEvent` + refundKey 幂等、按行比例扣减逻辑**完全不动**。

## 6. 日解冻定时任务(新增)

`core-application/.../performance/job/CommissionUnfreezeJob.java`:
```java
@Scheduled(cron = "0 5 2 * * ?")   // 每天 02:05(早于月初 04:00 结算)
@Transactional(rollbackFor = Exception.class)
public void unfreezeDue() {
    int n = logRepository.unfreezeDue(LocalDateTime.now());
    log.info("Commission unfreeze: {} logs", n);
}
```
新增仓储方法 `int unfreezeDue(LocalDateTime now)`:`UPDATE staff_performance_log SET status=2 WHERE status=1 AND freeze_until<=?`。
- 天然幂等(`status=1` 过滤)。
- 安全失败:漏跑只会延迟发放,**绝不超发**。

## 7. 结算与实发

### 7.1 `PerformanceSettlementJob.settleLastMonth`

聚合口径从 `created_at` 切到 `freeze_until + status=2`,**新增仓储方法、不动旧方法**(避免影响潜在调用方):
```java
// 原 sumAmountByStaffAndPeriod(staffId, start, end)   → created_at BETWEEN
// 新 sumPayableByFreezeMonth(staffId, start, end)     → status=2 AND freeze_until BETWEEN
// 原 findStaffIdsByMonth(month)                       → DATE_FORMAT(created_at)
// 新 findStaffIdsWithPayableInMonth(month)            → status=2 AND DATE_FORMAT(freeze_until)
```
账单模型 `StaffSalaryBill` 不变。结算目标仍是上月(`lastMonth`),即"freeze_until 落在上月的可发放提成"。跨月提成(月末下单、次月解冻)自然计入次月账单。

### 7.2 实发(复用,不改)

`PerformanceApplicationService.confirmSalaryBill(id)` 已存在:管理员确认 → `status 0→1, confirmedAt=now` = 实发。退款发生在确认后 → `REFUND_DEDUCT` 负数(freeze_until=now)自动落员工下一个未确认账单 = 回冲进下期,零额外代码。

## 8. 配置(门控)

`PerformanceProperties`(经 `PerformanceAutoConfiguration` 注册)新增:
```java
/** 提成冻结开关;关闭时回退即算即发(旧行为),上线灰度用 */
private boolean freezeEnabled = false;
/** 冻结期天数(订单完成后过 N 天解冻) */
private int freezeDays = 15;
```
`application-{dev,prod,test}.yml` 加 `performance.freeze-enabled` / `performance.freeze-days`。
- `freezeEnabled=false`(默认):等同现在即算即发,日解冻任务空转,结算行为不变。可安全部署、随时开。
- 配置变更只影响新 log(已冻结行 freeze_until 在创建时定格)。

## 9. 错误处理 & 幂等

| 路径 | 已有保障 | 新增/加固 |
|---|---|---|
| onOrderFinished | `markEvent` 幂等 | 按 `freezeEnabled` 决定冻结态 |
| onOrderRefunded | `markEvent`+refundKey 幂等 | 负数继承正数 `(status,freeze_until)` |
| 日解冻任务 | — | `status=1` 过滤天然幂等;漏跑只延迟不超发 |
| 月结算 | upsert(可重跑) | 切 freeze_until+status=2 口径 |
| confirmSalaryBill | — | **加固**:仅 status=0 可确认,重复确认报错(防二次实发);`confirmedAt` 只在首次置 |

## 10. 测试(约 8~10 个)

- **监听器**(`core-application`):`onOrderFinished` 冻结开→status=1+freeze_until;关→status=2+now。`onOrderRefunded` 负数继承冻结态正数 vs 已解冻正数两种。
- **仓储**(`core-infrastructure` 集成或单测):`unfreezeDue` 只解冻到期行、幂等;`sumPayableByFreezeMonth` 排除 status=1。
- **结算**:跨月提成(月末下单、次月解冻)计入次月账单,不进本月。
- **confirmSalaryBill 加固**:重复确认报错。

现有 140 测试不回归(全 reactor `mvn -o test`)。

## 11. 改动清单汇总

| # | 文件 | 类型 |
|---|---|---|
| 1 | `db/migration/V118__commission_freeze_lifecycle.sql` | 新增 |
| 2 | `core-domain/.../performance/model/PerformanceLogStatus.java` | 新增枚举 |
| 3 | `core-domain/.../performance/model/StaffPerformanceLog.java` | 加 status/freezeUntil 字段 |
| 4 | `core-domain/.../performance/repository/StaffPerformanceLogRepository.java` | 加 unfreezeDue / sumPayableByFreezeMonth / findStaffIdsWithPayableInMonth |
| 5 | `core-infrastructure/.../performance/repository/StaffPerformanceLogRepositoryImpl.java` | 实现新方法 |
| 6 | `core-application/.../performance/config/PerformanceProperties.java` | 加 freezeEnabled/freezeDays |
| 7 | `core-application/.../performance/listener/PerformanceEventListener.java` | onOrderFinished/onOrderRefunded 增量 |
| 8 | `core-application/.../performance/job/CommissionUnfreezeJob.java` | 新增 |
| 9 | `core-application/.../performance/job/PerformanceSettlementJob.java` | 切 freeze_until+status=2 口径 |
| 10 | `core-application/.../performance/service/PerformanceApplicationService.java` | confirmSalaryBill 加固 |
| 11 | `application-{dev,prod,test}.yml` | 配置项 |
| 12 | 测试若干 | 新增 |

无新权限码、无新端点、无菜单种子迁移。`实发`/幂等/确认接口全部复用现有。

## 12. 范围之外

- 钱包提现、赊销账期(P2)。
- 可配置审批流(Flowable,P2)。
- 看板 `StaffCommissionDashboardService` 改为读真实提成(现为 2% 估算)——可作为后续小改进,本方案只保证数据层支持(status/freeze_until 已可查询"冻结中/可发放"拆分)。
- `RECHARGE_GUIDE_REWARD`/`NEW_CUSTOMER_REWARD` 的冻结(业务上退款极少,按决策保持即算即发)。
