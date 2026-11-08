> 文档状态：历史阶段设计/计划，非当前全量事实；按文件日期和提交阅读。被覆盖部分以[统一文档中心](../../current/README.md)为准；未覆盖且仍有效的规则须对照当前代码确认，不直接执行旧迁移/模式切换。

# 提成生命周期(冻结→解冻→实发→回冲)Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 `SALES_COMMISSION` 提成上引入"冻结→到期解冻→账单确认实发→退款回冲"生命周期,冻结期 15 天可配置、门控灰度,复用现有账单确认/幂等机制。

**Architecture:** 方案 A(就地状态机)——给 `staff_performance_log` 加 `status`(1-冻结/2-已解冻)+`freeze_until` 两列;订单完成写冻结态 log,日定时任务到期解冻,月结算按 `status=2 AND freeze_until` 聚合;退款负数**继承原正数的 `(status,freeze_until)`** 统一处理全额/部分退款,跨期回冲自动落 `freeze_until` 所在月账单。

**Tech Stack:** Spring Boot 3 / Java 17 / MyBatis-Flex / MySQL 8 / Flyway / JUnit 5 + Mockito。

## Global Constraints

- **JDK**:本机默认 JDK 8,项目要 17。所有 mvn 命令前导:`JAVA17=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home`。下文命令均以 `$JAVA17` 代指该路径,实际执行展开为 `JAVA_HOME=$JAVA17`。
- **离线构建**:`mvn -o`(已装全部依赖)。禁连远程仓库。
- **测试命令**:
  - core-domain 单测:`mvn -o -pl core-domain test -Dtest=<类> -Dsurefire.failIfNoSpecifiedTests=false`
  - core-application 单测:`mvn -o -pl core-application -am test -Dtest=<类> -Dsurefire.failIfNoSpecifiedTests=false`(`-am` 重建 core-domain,避免用 ~/.m2 旧 jar)
  - 若出现 `NoSuchMethodError`(指向 core-domain 新方法),是 `-am` 偶发命中旧 jar,**回退全量**:`mvn -o test`
  - 最终验证(Task 8)必须全量 `mvn -o test`。
- **状态码常量**:Java 代码引用状态码一律用 `PerformanceLogStatus.FROZEN.getCode()` / `UNFROZEN.getCode()`,禁止裸写 `1`/`2`(SQL 迁移注释与 where 字面量除外)。
- **租户**:`StaffPerformanceLog.storeId` 是 `@Column(tenantId=true)`,MyBatis-Flex 自动过滤。新增仓储查询沿用既有行为(与 `sumAmountByStaffAndPeriod` 一致),**不要**特殊处理租户。
- **不动旧方法**:`sumAmountByStaffAndPeriod` / `findStaffIdsByMonth` 保留(防影响其他调用方),结算切到新方法。
- **提交**:每个 Task 末尾 conventional commit,中文 scope,如 `feat(perf): ...`。当前在 master 分支,直接提交(沿用本仓库既有习惯;用户未要求开 PR)。

---

## File Structure

| 文件 | 责任 | 类型 |
|---|---|---|
| `core-domain/.../performance/model/PerformanceLogStatus.java` | 提成状态枚举(1-冻结/2-已解冻) | 新增 |
| `core-domain/.../performance/model/StaffPerformanceLog.java` | 加 `status`/`freezeUntil` 字段 + `markFrozen/markImmediate` | 改 |
| `core-infrastructure/.../db/migration/V118__commission_freeze_lifecycle.sql` | 加列+回填+索引 | 新增 |
| `core-domain/.../performance/repository/StaffPerformanceLogRepository.java` | 加 3 个查询方法 | 改 |
| `core-infrastructure/.../performance/repository/StaffPerformanceLogRepositoryImpl.java` | 实现新方法 | 改 |
| `core-application/.../performance/config/PerformanceProperties.java` | 加 `freezeEnabled`/`freezeDays` | 改 |
| `core-application/.../performance/listener/PerformanceEventListener.java` | onOrderFinished 写冻结态 / onOrderRefunded 负数继承 | 改 |
| `core-application/.../performance/job/CommissionUnfreezeJob.java` | 日解冻定时任务 | 新增 |
| `core-application/.../performance/job/PerformanceSettlementJob.java` | 结算切 freeze_until+status=2 | 改 |
| `core-application/.../performance/service/PerformanceApplicationService.java` | confirmSalaryBill 加固 | 改 |
| `api-admin/src/main/resources/application-dev.yml` | 配置项(可选覆盖) | 改 |

测试(新增):`PerformanceLogStatusTest`、`StaffPerformanceLogFreezeTest`(core-domain);`CommissionFreezeListenerTest`、`CommissionUnfreezeJobTest`、`PerformanceSettlementFreezeTest`、`ConfirmSalaryBillHardeningTest`(core-application);`StaffPerformanceLogRepositoryFreezeTest`(core-infrastructure)。

---

### Task 1: 领域基础——状态枚举 + 模型字段 + 迁移

**Files:**
- Create: `core-domain/src/main/java/com/agri_system/domain/performance/model/PerformanceLogStatus.java`
- Create: `core-infrastructure/src/main/resources/db/migration/V118__commission_freeze_lifecycle.sql`
- Modify: `core-domain/src/main/java/com/agri_system/domain/performance/model/StaffPerformanceLog.java`
- Test: `core-domain/src/test/java/com/agri_system/domain/performance/model/PerformanceLogStatusTest.java`
- Test: `core-domain/src/test/java/com/agri_system/domain/performance/model/StaffPerformanceLogFreezeTest.java`

**Interfaces:**
- Produces: `PerformanceLogStatus.FROZEN`(code 1)/`UNFROZEN`(code 2),`getCode()`/`fromCode(int)`;`StaffPerformanceLog.status`(Integer)/`freezeUntil`(LocalDateTime)/`markFrozen(int days)`/`markImmediate()`。后续所有 Task 依赖这些。

- [ ] **Step 1: 写失败测试 `PerformanceLogStatusTest`**

```java
package com.agri_system.domain.performance.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PerformanceLogStatusTest {
    @Test
    void codesAndRoundTrip() {
        assertEquals(1, PerformanceLogStatus.FROZEN.getCode());
        assertEquals(2, PerformanceLogStatus.UNFROZEN.getCode());
        assertEquals(PerformanceLogStatus.FROZEN, PerformanceLogStatus.fromCode(1));
        assertEquals(PerformanceLogStatus.UNFROZEN, PerformanceLogStatus.fromCode(2));
    }

    @Test
    void unknownCodeThrows() {
        assertThrows(IllegalArgumentException.class, () -> PerformanceLogStatus.fromCode(9));
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `JAVA_HOME=$JAVA17 mvn -o -pl core-domain test -Dtest=PerformanceLogStatusTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: 编译失败(`PerformanceLogStatus` 不存在)。

- [ ] **Step 3: 实现 `PerformanceLogStatus`**

```java
package com.agri_system.domain.performance.model;

/** 提成流水状态:1-冻结(退货窗口内) 2-已解冻(可发放) */
public enum PerformanceLogStatus {
    FROZEN(1),
    UNFROZEN(2);

    private final int code;

    PerformanceLogStatus(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static PerformanceLogStatus fromCode(int code) {
        for (PerformanceLogStatus s : values()) {
            if (s.code == code) {
                return s;
            }
        }
        throw new IllegalArgumentException("未知提成状态码: " + code);
    }
}
```

- [ ] **Step 4: 写失败测试 `StaffPerformanceLogFreezeTest`**

```java
package com.agri_system.domain.performance.model;

import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;

class StaffPerformanceLogFreezeTest {
    @Test
    void markFrozenSetsFrozenStateAndFutureUntil() {
        StaffPerformanceLog log = new StaffPerformanceLog();
        LocalDateTime before = LocalDateTime.now();
        log.markFrozen(15);
        assertEquals(PerformanceLogStatus.FROZEN.getCode(), log.getStatus());
        assertNotNull(log.getFreezeUntil());
        assertTrue(log.getFreezeUntil().isAfter(before.plusDays(14)));
    }

    @Test
    void markImmediateSetsUnfrozenStateAndNowUntil() {
        StaffPerformanceLog log = new StaffPerformanceLog();
        LocalDateTime before = LocalDateTime.now();
        log.markImmediate();
        assertEquals(PerformanceLogStatus.UNFROZEN.getCode(), log.getStatus());
        assertNotNull(log.getFreezeUntil());
        assertFalse(log.getFreezeUntil().isBefore(before));
    }
}
```

- [ ] **Step 5: 运行确认失败(缺字段/方法)**

Run: 同 Step 2 但 `-Dtest=StaffPerformanceLogFreezeTest`
Expected: 编译失败(`getStatus`/`getFreezeUntil`/`markFrozen` 不存在)。

- [ ] **Step 6: 给 `StaffPerformanceLog` 加字段与方法**

在 `StaffPerformanceLog` 类体内(`needReview` 字段之后、`createdAt` 之前)加:
```java
    /** 1-冻结 2-已解冻 */
    private Integer status;

    /** 解冻时点:冻结类=created_at+冻结期,非冻结类=created_at */
    private LocalDateTime freezeUntil;
```
在 `createdAt` 字段之后、`negativeOf` 静态方法之前加实例方法:
```java
    /** 标记为冻结(退货窗口内) */
    public void markFrozen(int freezeDays) {
        this.status = PerformanceLogStatus.FROZEN.getCode();
        this.freezeUntil = LocalDateTime.now().plusDays(freezeDays);
    }

    /** 标记为立即解冻(即算即发,旧行为) */
    public void markImmediate() {
        this.status = PerformanceLogStatus.UNFROZEN.getCode();
        this.freezeUntil = LocalDateTime.now();
    }
```

- [ ] **Step 7: 运行两个测试确认通过**

Run: `JAVA_HOME=$JAVA17 mvn -o -pl core-domain test -Dtest='PerformanceLogStatusTest,StaffPerformanceLogFreezeTest' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS。

- [ ] **Step 8: 写 Flyway 迁移 `V118__commission_freeze_lifecycle.sql`**

```sql
-- 提成冻结生命周期:status(1-冻结 2-已解冻) + freeze_until(解冻时点)
ALTER TABLE staff_performance_log
  ADD COLUMN status TINYINT NOT NULL DEFAULT 2
      COMMENT '1-冻结 2-已解冻' AFTER need_review,
  ADD COLUMN freeze_until DATETIME NULL
      COMMENT '解冻时点:冻结类=created_at+冻结期,非冻结类=created_at';

-- 存量行:status 默认 2(已解冻);freeze_until 回填 created_at,保持原"按发生月计入"行为
UPDATE staff_performance_log SET freeze_until = created_at WHERE freeze_until IS NULL;

-- 日解冻任务按 (status, freeze_until) 扫描
ALTER TABLE staff_performance_log ADD INDEX idx_freeze (status, freeze_until);
```

- [ ] **Step 9: 提交**

```bash
git add core-domain/src/main/java/com/agri_system/domain/performance/model/PerformanceLogStatus.java \
        core-domain/src/main/java/com/agri_system/domain/performance/model/StaffPerformanceLog.java \
        core-domain/src/test/java/com/agri_system/domain/performance/model/PerformanceLogStatusTest.java \
        core-domain/src/test/java/com/agri_system/domain/performance/model/StaffPerformanceLogFreezeTest.java \
        core-infrastructure/src/main/resources/db/migration/V118__commission_freeze_lifecycle.sql
git commit -m "feat(perf): 提成状态枚举 + status/freeze_until 字段 + V118 迁移"
```

---

### Task 2: 仓储层——新增 3 个查询方法

**Files:**
- Modify: `core-domain/src/main/java/com/agri_system/domain/performance/repository/StaffPerformanceLogRepository.java`
- Modify: `core-infrastructure/src/main/java/com/agri_system/infrastructure/performance/repository/StaffPerformanceLogRepositoryImpl.java`
- Test: `core-infrastructure/src/test/java/com/agri_system/infrastructure/performance/repository/StaffPerformanceLogRepositoryFreezeTest.java`

**Interfaces:**
- Consumes: `PerformanceLogStatus`(Task 1)。
- Produces:
  - `int unfreezeDue(LocalDateTime now)` — 把到期冻结行置为已解冻,返回受影响行数。
  - `BigDecimal sumPayableByFreezeMonth(Long staffId, LocalDateTime start, LocalDateTime end)` — `SUM(amount) WHERE staff_id=? AND status=2 AND freeze_until BETWEEN start AND end`。
  - `List<Long> findStaffIdsWithPayableInMonth(String month)` — `DISTINCT staff_id WHERE status=2 AND DATE_FORMAT(freeze_until,'%Y-%m')=month`。Task 6 结算依赖这三个。

- [ ] **Step 1: 写失败测试 `StaffPerformanceLogRepositoryFreezeTest`**

```java
package com.agri_system.infrastructure.performance.repository;

import com.agri_system.domain.performance.model.StaffPerformanceLog;
import com.agri_system.domain.performance.model.PerformanceLogStatus;
import com.agri_system.infrastructure.performance.mapper.StaffPerformanceLogMapper;
import com.mybatisflex.core.query.QueryWrapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class StaffPerformanceLogRepositoryFreezeTest {

    @Test
    void unfreezeDueSetsUnfrozenAndReturnsCount() {
        StaffPerformanceLogMapper mapper = mock(StaffPerformanceLogMapper.class);
        when(mapper.updateByQuery(any(StaffPerformanceLog.class), any(QueryWrapper.class))).thenReturn(7);
        StaffPerformanceLogRepositoryImpl repo = new StaffPerformanceLogRepositoryImpl(mapper);

        int n = repo.unfreezeDue(LocalDateTime.now());

        assertEquals(7, n);
        ArgumentCaptor<StaffPerformanceLog> cap = ArgumentCaptor.forClass(StaffPerformanceLog.class);
        verify(mapper).updateByQuery(cap.capture(), any(QueryWrapper.class));
        assertEquals(PerformanceLogStatus.UNFROZEN.getCode(), cap.getValue().getStatus());
    }

    @Test
    void sumPayableReturnsZeroWhenMapperReturnsNull() {
        StaffPerformanceLogMapper mapper = mock(StaffPerformanceLogMapper.class);
        when(mapper.selectOneByQueryAs(any(QueryWrapper.class), eq(BigDecimal.class))).thenReturn(null);
        StaffPerformanceLogRepositoryImpl repo = new StaffPerformanceLogRepositoryImpl(mapper);

        assertEquals(BigDecimal.ZERO, repo.sumPayableByFreezeMonth(1L, LocalDateTime.now(), LocalDateTime.now()));
    }

    @Test
    void findStaffIdsDelegatesToMapper() {
        StaffPerformanceLogMapper mapper = mock(StaffPerformanceLogMapper.class);
        StaffPerformanceLogRepositoryImpl repo = new StaffPerformanceLogRepositoryImpl(mapper);

        repo.findStaffIdsWithPayableInMonth("2026-08");

        verify(mapper).selectListByQueryAs(any(QueryWrapper.class), eq(Long.class));
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `JAVA_HOME=$JAVA17 mvn -o -pl core-infrastructure -am test -Dtest=StaffPerformanceLogRepositoryFreezeTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: 编译失败(接口无新方法)。

- [ ] **Step 3: 接口加方法签名**

在 `StaffPerformanceLogRepository.java` 接口末尾(`findByStaffAndMonthRange` 之后)加:
```java
    /** 到期解冻:把 status=1 且 freeze_until<=now 的行置为 status=2,返回受影响行数 */
    int unfreezeDue(LocalDateTime now);

    /** 按 freeze_until 月份聚合可发放(status=2)提成 */
    java.math.BigDecimal sumPayableByFreezeMonth(Long staffId, LocalDateTime start, LocalDateTime end);

    /** 找出 freeze_until 落在指定月份、且有可发放提成的员工 */
    java.util.List<Long> findStaffIdsWithPayableInMonth(String month);
```

- [ ] **Step 4: Impl 实现**

在 `StaffPerformanceLogRepositoryImpl.java` 末尾(最后一个 `@Override` 方法之后)加。需 import `com.agri_system.domain.performance.model.PerformanceLogStatus`:
```java
    @Override
    public int unfreezeDue(LocalDateTime now) {
        StaffPerformanceLog update = new StaffPerformanceLog();
        update.setStatus(PerformanceLogStatus.UNFROZEN.getCode());
        QueryWrapper query = QueryWrapper.create()
                .where("status = ?", PerformanceLogStatus.FROZEN.getCode())
                .and("freeze_until <= ?", now);
        return mapper.updateByQuery(update, query);
    }

    @Override
    public java.math.BigDecimal sumPayableByFreezeMonth(Long staffId, LocalDateTime start, LocalDateTime end) {
        QueryWrapper query = QueryWrapper.create()
                .select("COALESCE(SUM(amount),0) AS total")
                .where("staff_id = ?", staffId)
                .and("status = ?", PerformanceLogStatus.UNFROZEN.getCode())
                .and("freeze_until BETWEEN ? AND ?", start, end);
        java.math.BigDecimal result = mapper.selectOneByQueryAs(query, java.math.BigDecimal.class);
        return result == null ? java.math.BigDecimal.ZERO : result;
    }

    @Override
    public java.util.List<Long> findStaffIdsWithPayableInMonth(String month) {
        QueryWrapper query = QueryWrapper.create()
                .select("DISTINCT staff_id")
                .where("status = ?", PerformanceLogStatus.UNFROZEN.getCode())
                .and("DATE_FORMAT(freeze_until,'%Y-%m') = ?", month);
        return mapper.selectListByQueryAs(query, Long.class);
    }
```

- [ ] **Step 5: 运行确认通过**

Run: 同 Step 2。
Expected: PASS(3 个测试)。

- [ ] **Step 6: 提交**

```bash
git add core-domain/src/main/java/com/agri_system/domain/performance/repository/StaffPerformanceLogRepository.java \
        core-infrastructure/src/main/java/com/agri_system/infrastructure/performance/repository/StaffPerformanceLogRepositoryImpl.java \
        core-infrastructure/src/test/java/com/agri_system/infrastructure/performance/repository/StaffPerformanceLogRepositoryFreezeTest.java
git commit -m "feat(perf): 仓储新增 unfreezeDue/sumPayableByFreezeMonth/findStaffIdsWithPayableInMonth"
```

---

### Task 3: 配置门控——freezeEnabled / freezeDays

**Files:**
- Modify: `core-application/src/main/java/com/agri_system/application/performance/config/PerformanceProperties.java`
- Modify: `api-admin/src/main/resources/application-dev.yml`
- Test: `core-application/src/test/java/com/agri_system/application/performance/config/PerformancePropertiesDefaultsTest.java`

**Interfaces:**
- Produces: `PerformanceProperties.isFreezeEnabled()`(默认 false)、`getFreezeDays()`(默认 15)。Task 4 监听器依赖。

- [ ] **Step 1: 写失败测试**

```java
package com.agri_system.application.performance.config;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PerformancePropertiesDefaultsTest {
    @Test
    void freezeDefaults() {
        PerformanceProperties p = new PerformanceProperties();
        assertFalse(p.isFreezeEnabled(), "冻结默认关闭(灰度安全)");
        assertEquals(15, p.getFreezeDays());
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `JAVA_HOME=$JAVA17 mvn -o -pl core-application -am test -Dtest=PerformancePropertiesDefaultsTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: 编译失败(getter 不存在)。

- [ ] **Step 3: 加字段**

在 `PerformanceProperties` 类末尾(`qrExpireSeconds` 字段之后)加:
```java
    /**
     * 提成冻结开关;关闭时回退即算即发(旧行为),上线灰度用
     */
    private boolean freezeEnabled = false;

    /**
     * 冻结期天数(订单完成后过 N 天解冻)
     */
    private int freezeDays = 15;
```

- [ ] **Step 4: 运行确认通过**

Run: 同 Step 2。
Expected: PASS。

- [ ] **Step 5: yml 加可选覆盖**

在 `api-admin/src/main/resources/application-dev.yml` 末尾加(若已有 `performance:` 段则并入):
```yaml
performance:
  freeze-enabled: false   # 提成冻结开关;true 启用 15 天退货窗口冻结
  freeze-days: 15
```
(prod/test 不加 → 用类默认值 false/15,即未启用前完全等同旧行为。)

- [ ] **Step 6: 提交**

```bash
git add core-application/src/main/java/com/agri_system/application/performance/config/PerformanceProperties.java \
        core-application/src/test/java/com/agri_system/application/performance/config/PerformancePropertiesDefaultsTest.java \
        api-admin/src/main/resources/application-dev.yml
git commit -m "feat(perf): PerformanceProperties 加 freezeEnabled/freezeDays 门控"
```

---

### Task 4: 监听器——写冻结态 + 退款负数继承

**Files:**
- Modify: `core-application/src/main/java/com/agri_system/application/performance/listener/PerformanceEventListener.java`
- Test: `core-application/src/test/java/com/agri_system/application/performance/listener/CommissionFreezeListenerTest.java`

**Interfaces:**
- Consumes: `StaffPerformanceLog.markFrozen/markImmediate`(Task 1)、`PerformanceProperties.isFreezeEnabled/getFreezeDays`(Task 3)。
- Produces: 写入的 `SALES_COMMISSION` log 带正确 `status`/`freeze_until`;`REFUND_DEDUCT` 负数继承原正数冻结态。

- [ ] **Step 1: 写失败测试 `CommissionFreezeListenerTest`**

监听器依赖多,用 `@ExtendWith(MockitoExtension.class)` + `@Mock` + `@InjectMocks`,`LENIENT` 宽松。`onOrderFinished` 路径需把订单设为无客户(`customerId=null`)以跳过新客分支:

```java
package com.agri_system.application.performance.listener;

import com.agri_system.application.customer.service.CustomerSettlementService;
import com.agri_system.application.performance.config.PerformanceProperties;
import com.agri_system.application.performance.event.OrderFinishedEvent;
import com.agri_system.application.performance.event.OrderRefundedEvent;
import com.agri_system.domain.order.model.Order;
import com.agri_system.domain.order.model.OrderItem;
import com.agri_system.domain.order.repository.OrderItemRepository;
import com.agri_system.domain.order.repository.OrderRepository;
import com.agri_system.domain.performance.model.PerformanceBizType;
import com.agri_system.domain.performance.model.PerformanceLogStatus;
import com.agri_system.domain.performance.model.StaffPerformanceLog;
import com.agri_system.domain.performance.repository.*;
import com.agri_system.domain.performance.service.PerformanceDomainService;
import com.agri_system.domain.product.model.Product;
import com.agri_system.domain.product.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CommissionFreezeListenerTest {

    @Mock PerformanceProperties properties;
    @Mock PerformanceDomainService performanceDomainService;
    @Mock CommissionRuleRepository commissionRuleRepository;
    @Mock StaffPerformanceLogRepository logRepository;
    @Mock PerformanceEventRecordRepository eventRecordRepository;
    @Mock OrderRepository orderRepository;
    @Mock OrderItemRepository orderItemRepository;
    @Mock ProductRepository productRepository;
    @Mock StaffCustomerRelationRepository relationRepository;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock CustomerSettlementService customerSettlementService;

    @InjectMocks PerformanceEventListener listener;

    private void stubFinishedCommon(Order order, OrderItem item, Product product) {
        when(eventRecordRepository.existsByEventKey(any())).thenReturn(false);
        when(eventRecordRepository.save(any())).thenReturn(null);
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(orderItemRepository.findByOrderId(order.getId())).thenReturn(List.of(item));
        when(productRepository.findById(item.getProductId())).thenReturn(Optional.of(product));
        // 计算结果给出正提成,触发写 log
        when(performanceDomainService.calculateSalesCommission(any(), any(), any(), anyBoolean(), any()))
                .thenReturn(new PerformanceDomainService.CommissionResult(
                        new BigDecimal("10.00"), "desc", false));
    }

    @Test
    void finishedWritesFrozenWhenEnabled() {
        when(properties.isFreezeEnabled()).thenReturn(true);
        when(properties.getFreezeDays()).thenReturn(15);
        Order order = orderWithStaff();
        OrderItem item = itemWithProduct();
        stubFinishedCommon(order, item, product());

        listener.onOrderFinished(new OrderFinishedEvent(order.getId()));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<StaffPerformanceLog>> cap = ArgumentCaptor.forClass(List.class);
        verify(logRepository).saveBatch(cap.capture());
        StaffPerformanceLog saved = cap.getValue().get(0);
        assertEquals(PerformanceLogStatus.FROZEN.getCode(), saved.getStatus());
        assertNotNull(saved.getFreezeUntil());
        assertTrue(saved.getFreezeUntil().isAfter(LocalDateTime.now().plusDays(14)));
    }

    @Test
    void finishedWritesImmediateWhenDisabled() {
        when(properties.isFreezeEnabled()).thenReturn(false);
        Order order = orderWithStaff();
        OrderItem item = itemWithProduct();
        stubFinishedCommon(order, item, product());

        listener.onOrderFinished(new OrderFinishedEvent(order.getId()));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<StaffPerformanceLog>> cap = ArgumentCaptor.forClass(List.class);
        verify(logRepository).saveBatch(cap.capture());
        StaffPerformanceLog saved = cap.getValue().get(0);
        assertEquals(PerformanceLogStatus.UNFROZEN.getCode(), saved.getStatus());
        assertNotNull(saved.getFreezeUntil());
    }

    @Test
    void refundNegativeInheritsFrozenPositive() {
        // 原正数仍冻结
        StaffPerformanceLog positive = new StaffPerformanceLog();
        positive.setStatus(PerformanceLogStatus.FROZEN.getCode());
        LocalDateTime until = LocalDateTime.now().plusDays(10);
        positive.setFreezeUntil(until);
        positive.setStaffId(2L);
        positive.setOrderItemId(50L);
        positive.setAmount(new BigDecimal("100"));

        Order order = new Order();
        order.setId(7L);
        order.setStaffId(2L);
        order.setPayAmount(new BigDecimal("100"));

        when(eventRecordRepository.existsByEventKey(any())).thenReturn(false);
        when(eventRecordRepository.save(any())).thenReturn(null);
        when(orderRepository.findById(7L)).thenReturn(Optional.of(order));
        when(logRepository.findByOrderId(7L)).thenReturn(List.of(positive));

        listener.onOrderRefunded(new OrderRefundedEvent(7L, new BigDecimal("100"), null, "rk1"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<StaffPerformanceLog>> cap = ArgumentCaptor.forClass(List.class);
        verify(logRepository).saveBatch(cap.capture());
        StaffPerformanceLog neg = cap.getValue().get(0);
        assertEquals(PerformanceBizType.REFUND_DEDUCT.getCode(), neg.getBizType());
        // 关键断言:负数继承原正数冻结态
        assertEquals(PerformanceLogStatus.FROZEN.getCode(), neg.getStatus());
        assertEquals(until, neg.getFreezeUntil());
    }

    @Test
    void refundNegativeInheritsUnfrozenPositive() {
        StaffPerformanceLog positive = new StaffPerformanceLog();
        positive.setStatus(PerformanceLogStatus.UNFROZEN.getCode());
        LocalDateTime until = LocalDateTime.now().minusDays(1);
        positive.setFreezeUntil(until);
        positive.setStaffId(2L);
        positive.setOrderItemId(50L);
        positive.setAmount(new BigDecimal("100"));

        Order order = new Order();
        order.setId(7L);
        order.setStaffId(2L);
        order.setPayAmount(new BigDecimal("100"));

        when(eventRecordRepository.existsByEventKey(any())).thenReturn(false);
        when(eventRecordRepository.save(any())).thenReturn(null);
        when(orderRepository.findById(7L)).thenReturn(Optional.of(order));
        when(logRepository.findByOrderId(7L)).thenReturn(List.of(positive));

        listener.onOrderRefunded(new OrderRefundedEvent(7L, new BigDecimal("100"), null, "rk2"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<StaffPerformanceLog>> cap = ArgumentCaptor.forClass(List.class);
        verify(logRepository).saveBatch(cap.capture());
        StaffPerformanceLog neg = cap.getValue().get(0);
        assertEquals(PerformanceLogStatus.UNFROZEN.getCode(), neg.getStatus());
    }

    // —— 工厂方法 ——
    private Order orderWithStaff() {
        Order o = new Order();
        o.setId(1L);
        o.setStaffId(2L);
        o.setStoreId(3L);
        o.setCustomerId(null); // 跳过新客分支
        o.setPayAmount(new BigDecimal("100"));
        return o;
    }
    private OrderItem itemWithProduct() {
        OrderItem i = new OrderItem();
        i.setId(11L);
        i.setProductId(20L);
        i.setUnitPrice(new BigDecimal("100"));
        i.setQuantity(BigDecimal.ONE);
        i.setCostPriceSnapshot(new BigDecimal("20"));
        return i;
    }
    private Product product() {
        Product p = new Product();
        p.setId(20L);
        p.setAvgCostPrice(new BigDecimal("20"));
        return p;
    }
}
```

> 注:`CommissionResult` 的确切构造器/record 名以 `PerformanceDomainService` 实际定义为准(Step 3 实现前先 `codegraph_node PerformanceDomainService.CommissionResult` 核对字段顺序与类型;若为 record `CommissionResult(BigDecimal amount, String desc, boolean needReview)`,上面的 `new ...("10.00","desc",false)` 即对)。`OrderRefundedEvent` 构造参数顺序同理先核对(Task 1 前已读到的监听器源里 `event.orderId()/refundAmount()/refundItems()/refundKey()` 即字段访问器,确认构造器签名)。

- [ ] **Step 2: 运行确认失败**

Run: `JAVA_HOME=$JAVA17 mvn -o -pl core-application -am test -Dtest=CommissionFreezeListenerTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: 测试失败(saved log status 为 null——监听器尚未写冻结态)。

- [ ] **Step 3: 改 `onOrderFinished` 写冻结态**

定位 `onOrderFinished` 内构造 `logEntry` 的块(`logEntry.setNeedReview(...)` 之后、`logs.add(logEntry)` 之前),插入:
```java
            if (properties.isFreezeEnabled()) {
                logEntry.markFrozen(properties.getFreezeDays());
            } else {
                logEntry.markImmediate();
            }
```

- [ ] **Step 4: 改 `onOrderRefunded` 负数继承冻结态**

定位 `onOrderRefunded` 内构造 `negative` 的块(`negative.setNeedReview(0);` 之后、`negatives.add(negative);` 之前),插入:
```java
            negative.setStatus(logEntry.getStatus());
            negative.setFreezeUntil(logEntry.getFreezeUntil());
```

- [ ] **Step 5: 运行确认通过**

Run: 同 Step 2。
Expected: 4 个测试 PASS。

- [ ] **Step 6: 提交**

```bash
git add core-application/src/main/java/com/agri_system/application/performance/listener/PerformanceEventListener.java \
        core-application/src/test/java/com/agri_system/application/performance/listener/CommissionFreezeListenerTest.java
git commit -m "feat(perf): 监听器写冻结态 + 退款负数继承原正数 (status,freeze_until)"
```

---

### Task 5: 日解冻定时任务

**Files:**
- Create: `core-application/src/main/java/com/agri_system/application/performance/job/CommissionUnfreezeJob.java`
- Test: `core-application/src/test/java/com/agri_system/application/performance/job/CommissionUnfreezeJobTest.java`

**Interfaces:**
- Consumes: `StaffPerformanceLogRepository.unfreezeDue(LocalDateTime)`(Task 2)。
- Produces: `@Scheduled(cron="0 5 2 * * ?")` 每日 02:05 把到期冻结提成解冻。

- [ ] **Step 1: 写失败测试**

```java
package com.agri_system.application.performance.job;

import com.agri_system.domain.performance.repository.StaffPerformanceLogRepository;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import static org.mockito.Mockito.*;

class CommissionUnfreezeJobTest {
    @Test
    void unfreezeDueDelegatesToRepository() {
        StaffPerformanceLogRepository repo = mock(StaffPerformanceLogRepository.class);
        when(repo.unfreezeDue(any())).thenReturn(12);

        CommissionUnfreezeJob job = new CommissionUnfreezeJob(repo);
        job.unfreezeDue();

        verify(repo).unfreezeDue(any(LocalDateTime.class));
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `JAVA_HOME=$JAVA17 mvn -o -pl core-application -am test -Dtest=CommissionUnfreezeJobTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: 编译失败(`CommissionUnfreezeJob` 不存在)。

- [ ] **Step 3: 实现 `CommissionUnfreezeJob`**

```java
package com.agri_system.application.performance.job;

import com.agri_system.domain.performance.repository.StaffPerformanceLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 提成日解冻任务:每天 02:05 把到期(status=1 且 freeze_until<=now)的冻结提成置为已解冻。
 * 早于月初 04:00 结算,保证结算时本月应解冻的均已解冻。
 * 天然幂等(status=1 过滤);漏跑只会延迟发放,绝不超发。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CommissionUnfreezeJob {

    private final StaffPerformanceLogRepository logRepository;

    @Scheduled(cron = "0 5 2 * * ?")
    @Transactional(rollbackFor = Exception.class)
    public void unfreezeDue() {
        int n = logRepository.unfreezeDue(LocalDateTime.now());
        log.info("Commission unfreeze: {} logs", n);
    }
}
```

- [ ] **Step 4: 运行确认通过**

Run: 同 Step 2。
Expected: PASS。

- [ ] **Step 5: 提交**

```bash
git add core-application/src/main/java/com/agri_system/application/performance/job/CommissionUnfreezeJob.java \
        core-application/src/test/java/com/agri_system/application/performance/job/CommissionUnfreezeJobTest.java
git commit -m "feat(perf): 新增提成日解冻定时任务 CommissionUnfreezeJob"
```

---

### Task 6: 结算切 freeze_until + status=2 口径

**Files:**
- Modify: `core-application/src/main/java/com/agri_system/application/performance/job/PerformanceSettlementJob.java`
- Test: `core-application/src/test/java/com/agri_system/application/performance/job/PerformanceSettlementFreezeTest.java`

**Interfaces:**
- Consumes: `sumPayableByFreezeMonth` / `findStaffIdsWithPayableInMonth`(Task 2)。
- Produces: 月结算按解冻月聚合可发放提成;跨月提成(月末下单、次月解冻)计入次月账单。

- [ ] **Step 1: 写失败测试**

验证结算调用新方法(而非旧的 `sumAmountByStaffAndPeriod`/`findStaffIdsByMonth`):
```java
package com.agri_system.application.performance.job;

import com.agri_system.domain.order.repository.OrderRepository;
import com.agri_system.domain.performance.model.StaffSalaryBill;
import com.agri_system.domain.performance.repository.StaffCustomerRelationRepository;
import com.agri_system.domain.performance.repository.StaffPerformanceLogRepository;
import com.agri_system.domain.performance.repository.StaffSalaryBillRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PerformanceSettlementFreezeTest {

    @Mock StaffPerformanceLogRepository logRepository;
    @Mock StaffSalaryBillRepository salaryBillRepository;
    @Mock OrderRepository orderRepository;
    @Mock StaffCustomerRelationRepository relationRepository;

    @InjectMocks PerformanceSettlementJob job;

    @Test
    void usesFreezeMonthAggregation() {
        when(logRepository.findStaffIdsWithPayableInMonth(anyString())).thenReturn(List.of(2L));
        when(logRepository.sumPayableByFreezeMonth(eq(2L), any(), any())).thenReturn(new BigDecimal("100.00"));
        when(orderRepository.sumPayAmountByStaffAndMonth(eq(2L), anyString())).thenReturn(new BigDecimal("500.00"));
        when(relationRepository.countFirstEffectiveByMonth(eq(2L), anyString())).thenReturn(1L);
        when(salaryBillRepository.findByStaffAndMonth(eq(2L), anyString())).thenReturn(Optional.of(new StaffSalaryBill()));
        when(salaryBillRepository.save(any())).thenReturn(null);

        job.settleLastMonth();

        // 用新方法
        verify(logRepository).findStaffIdsWithPayableInMonth(anyString());
        verify(logRepository).sumPayableByFreezeMonth(eq(2L), any(), any());
        // 不再用旧方法
        verify(logRepository, never()).findStaffIdsByMonth(anyString());
        verify(logRepository, never()).sumAmountByStaffAndPeriod(anyLong(), any(), any());
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `JAVA_HOME=$JAVA17 mvn -o -pl core-application -am test -Dtest=PerformanceSettlementFreezeTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: 失败(仍调用旧方法,`never()` 断言不满足)。

- [ ] **Step 3: 改 `PerformanceSettlementJob.settleLastMonth`**

把方法体内两处调用替换:
- `logRepository.findStaffIdsByMonth(target.toString())` → `logRepository.findStaffIdsWithPayableInMonth(target.toString())`
- `logRepository.sumAmountByStaffAndPeriod(staffId, target.atDay(1).atStartOfDay(), target.plusMonths(1).atDay(1).atStartOfDay().minusNanos(1))` → `logRepository.sumPayableByFreezeMonth(staffId, target.atDay(1).atStartOfDay(), target.plusMonths(1).atDay(1).atStartOfDay().minusNanos(1))`

其余(`orderRepository.sumPayAmountByStaffAndMonth`、`relationRepository.countFirstEffectiveByMonth`、账单 upsert)**不变**。

- [ ] **Step 4: 运行确认通过**

Run: 同 Step 2。
Expected: PASS。

- [ ] **Step 5: 提交**

```bash
git add core-application/src/main/java/com/agri_system/application/performance/job/PerformanceSettlementJob.java \
        core-application/src/test/java/com/agri_system/application/performance/job/PerformanceSettlementFreezeTest.java
git commit -m "feat(perf): 月结算切按 freeze_until+status=2 聚合可发放提成"
```

---

### Task 7: confirmSalaryBill 加固(防二次实发)

**Files:**
- Modify: `core-application/src/main/java/com/agri_system/application/performance/service/PerformanceApplicationService.java`
- Test: `core-application/src/test/java/com/agri_system/application/performance/service/ConfirmSalaryBillHardeningTest.java`

**Interfaces:**
- Consumes: `StaffSalaryBill.status`(0=草稿/未确认,1=已确认/实发)。
- Produces: 仅 status=0 可确认;重复确认抛 `IllegalStateException`;`confirmedAt` 只首次置。

- [ ] **Step 1: 写失败测试**

```java
package com.agri_system.application.performance.service;

import com.agri_system.domain.performance.model.StaffSalaryBill;
import com.agri_system.domain.performance.repository.StaffSalaryBillRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConfirmSalaryBillHardeningTest {

    @Mock StaffSalaryBillRepository staffSalaryBillRepository;
    @InjectMocks PerformanceApplicationService service;

    @Test
    void confirmDraftBillSucceeds() {
        StaffSalaryBill bill = new StaffSalaryBill();
        bill.setId(1L);
        bill.setStatus(0);
        when(staffSalaryBillRepository.findById(1L)).thenReturn(Optional.of(bill));

        service.confirmSalaryBill(1L);

        assertEquals(1, bill.getStatus());
        assertNotNull(bill.getConfirmedAt());
        verify(staffSalaryBillRepository).save(bill);
    }

    @Test
    void confirmAlreadyConfirmedThrows() {
        StaffSalaryBill bill = new StaffSalaryBill();
        bill.setId(1L);
        bill.setStatus(1);
        when(staffSalaryBillRepository.findById(1L)).thenReturn(Optional.of(bill));

        assertThrows(IllegalStateException.class, () -> service.confirmSalaryBill(1L));
        verify(staffSalaryBillRepository, never()).save(any());
    }
}
```

> 注:`PerformanceApplicationService` 构造器注入了多个仓储(见文件头部 `@RequiredArgsConstructor`)。`@InjectMocks` 会把未 `@Mock` 的字段留 null,但 `confirmSalaryBill` 只用到 `staffSalaryBillRepository`,其余 null 不影响该路径。若 `@InjectMocks` 因构造器签名不匹配失败,改为在测试里手动 `new PerformanceApplicationService(staffSalaryBillRepository, ...)` 传全部依赖(其余 mock)。先按 `@InjectMocks` 跑,失败再调整。

- [ ] **Step 2: 运行确认失败**

Run: `JAVA_HOME=$JAVA17 mvn -o -pl core-application -am test -Dtest=ConfirmSalaryBillHardeningTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: `confirmAlreadyConfirmedThrows` 失败(当前实现不校验,重复确认直接置 1 不抛)。

- [ ] **Step 3: 加固 `confirmSalaryBill`**

定位 `confirmSalaryBill(Long billId)` 方法体,在 `bill.setStatus(1)` 之前加守卫:
```java
        if (bill.getStatus() != null && bill.getStatus() != 0) {
            throw new IllegalStateException("工资单已确认,不可重复确认:" + billId);
        }
```

- [ ] **Step 4: 运行确认通过**

Run: 同 Step 2。
Expected: 2 个测试 PASS。

- [ ] **Step 5: 提交**

```bash
git add core-application/src/main/java/com/agri_system/application/performance/service/PerformanceApplicationService.java \
        core-application/src/test/java/com/agri_system/application/performance/service/ConfirmSalaryBillHardeningTest.java
git commit -m "feat(perf): confirmSalaryBill 加固,禁止重复确认(防二次实发)"
```

---

### Task 8: 全量构建验证

**Files:** 无(仅验证)。

- [ ] **Step 1: 全量编译**

Run: `JAVA_HOME=$JAVA17 mvn -o clean compile -DskipTests`
Expected: BUILD SUCCESS(全 reactor 编译通过)。

- [ ] **Step 2: 全量测试**

Run: `JAVA_HOME=$JAVA17 mvn -o test`
Expected: 全部通过,含本方案新增测试,**无回归**。记录新增测试数。

- [ ] **Step 3: 修补(若有)**

若 Step 2 出现回归或失败:
- 若为 `NoSuchMethodError` 指向 core-domain 新字段 → 全量已重建,应为真实问题,排查模型/迁移。
- 若为既有测试因结算口径变化失败 → 核对是否预期内(如 `PerformanceModuleTest` 中断言旧 `sumAmountByStaffAndPeriod` 被调用的地方)。
修后重新 Step 2,通过后追加提交 `git commit --allow-empty -m "test(perf): 全量验证通过"`(或带实际改动)。

- [ ] **Step 4: 自检清单(人工确认)**

- [ ] V118 迁移可对干净库执行(本机有 MySQL 可选跑 `mvn -o flyway:migrate`;无则靠测试覆盖)。
- [ ] `freezeEnabled=false` 时行为与改动前一致(即算即发)。
- [ ] 新增定时任务 `CommissionUnfreezeJob` 会被 Spring 扫描(`@Component`,且 `@EnableScheduling` 已存在——`PerformanceSettlementJob` 同机制已生效)。

---

## Self-Review(已执行)

1. **Spec 覆盖**:spec 12 节均有对应 Task——数据模型/迁移(Task 1)、状态机枚举(Task 1)、写入路径(§5→Task 4)、日解冻任务(§6→Task 5)、结算与实发(§7→Task 6+Task 7)、配置(§8→Task 3)、错误处理幂等(Task 4/5/6/7 内含)、测试(§10→各 Task)、改动清单(全部)。看板改造(§12 范围外)明确不做。
2. **占位符扫描**:无 TBD/TODO;Task 4 Step 1 的 `CommissionResult`/`OrderRefundedEvent` 构造器已显式标注"先 codegraph_node 核对"并给出默认假设,非占位。
3. **类型一致性**:`unfreezeDue` / `sumPayableByFreezeMonth` / `findStaffIdsWithPayableInMonth` 在 Task 2(接口+impl)、Task 5(调用)、Task 6(调用)签名一致;`markFrozen`/`markImmediate` 在 Task 1 定义、Task 4 调用一致;`PerformanceLogStatus.FROZEN/UNFROZEN.getCode()` 全程统一。
