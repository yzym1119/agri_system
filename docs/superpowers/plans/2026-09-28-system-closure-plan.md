# 系统收尾实施计划（按优先级）

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 2026-09-28 全系统只读审计发现的收尾缺口，按「阻塞上线 → 功能缺口 → 打磨 → 文档」的优先级排好并逐项落地。

**Architecture:** 分五个阶段。阶段 1（P0-A）是可以立刻做的小而硬的修复，本文给出逐步 TDD 细节，按顺序执行。阶段 2–5 牵涉独立子系统（支付网关、小程序接口、绩效/通知等），本文只定范围、证据和验收标准；每个阶段开工前再单独写一份细化计划（`docs/superpowers/plans/` 下），避免一份计划跨越太多子系统。

**Tech Stack:** Spring Boot 3 / Java 17 / Maven 多模块 / MyBatis-Flex / Flyway 9.22.3 / MySQL 9.6 / H2（默认测试）/ Sa-Token / Vue 3 + TS + Element Plus（admin-web）。

**Spec:** 无独立 spec。事实依据是 2026-09-28 三份审计报告（后端、前端、计划状态；已并入本文“证据”栏）和 `docs/WORKING_STATE.md`。本文判断均为控制者对照代码核实过的结论。

## Global Constraints

- **不得 push**：任何分支都不推 origin。
- 不在 master 上直接实现：在 worktree `.wt-task-closure`（分支 `closure`，从 master `ed90bdb` 切出）上开发，完成后用 merge commit 合回本地 master。
- **featureStatus（零售事实链激活开关）不得 enable 任何 SKU。**
- 不回归既有测试：默认套件 `./mvnw -q -pl api-admin -am test` 必须 exit 0。
- 所有 Maven 命令都加 JDK 17 前缀：`env JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home PATH=/opt/homebrew/opt/openjdk@17/bin:$PATH ./mvnw ...`，超时 ≥ 600000ms，输出重定向到 `/tmp`。
- 跨模块只跑单个测试时加 `-Dsurefire.failIfNoSpecifiedTests=false`。
- 不得连接或写入 `agri_system`、`tlcloud_*` 库；真实 MySQL 验证只用自建自删的 `agri_retail*` 一次性库（`-Dmysql.it=true` 门控测试已经这样做）。
- 已应用的迁移一律不改（checksum）；新迁移从 **V128** 起。
- 新菜单 ID 从 **2707** 起（2700–2706 已被占用，2702 当前是「库存预警」）。
- 不碰两个 Orca 工作区（cerith、viperfish）；需要其中的改动时手工移植。
- 测试先行：每个行为修复都先写一个会失败的测试。

---

## 优先级总览

| 阶段 | 优先级 | 条目 | 能否立即开工 |
|---|---|---|---|
| 1 | P0-A | 角色编辑接口、工资单重复结算、菜单入口缺失、小程序自行确认支付、dev 配置 | ✅ 本文细化，立即执行 |
| 2 | P0-B | 真实支付网关、外部/现金退款、补偿与对账 | ⛔ 需要商户号、小程序支付资质、回调环境 |
| 3 | P0-C | 小程序端缺失的浏览与“我的”类接口 | ✅ 开工前单独写细化计划 |
| 4 | P1 | 充值事件、扫码绑定、通知落库、定时任务缺陷、开发登录闸门、前端缺口 | ✅ 开工前单独写细化计划 |
| 5 | P2 + 文档 | 调拨审批、分布式锁、孤儿表、分层清理、文档同步 | ✅ 文档部分跟随每个阶段同步 |

---

## 阶段 1（P0-A）：小而硬的修复

### Task 1：角色编辑接口（PUT /api/admin/roles/{id}）

**问题：** `admin-web/src/api/role.ts:31-38` 用 `PUT /api/admin/roles/{id}` 更新角色（调用处 `views/system/role/index.vue:230`），但 `RoleController` 没有 `@PutMapping`，所以编辑角色必然失败。另外，现有的 `StaffAuthorityService.saveRole` 在 code 为空时会重新生成编码，而前端编辑时恰好传 `code: undefined`。所以不能直接复用 `saveRole` 来做更新，否则每编辑一次角色编码就变一次。

**Files:**
- Modify: `core-application/src/main/java/com/agri_system/application/staff/service/StaffAuthorityService.java`（在 `saveRole` 后新增 `updateRole`）
- Modify: `api-admin/src/main/java/com/agri_system/api/admin/controller/RoleController.java`（新增 PUT）
- Create: `core-application/src/test/java/com/agri_system/application/staff/service/StaffAuthorityServiceRoleUpdateTest.java`
- Create: `api-admin/src/test/java/com/agri_system/api/admin/controller/RoleControllerTest.java`

**Interfaces:**
- Produces: `public void updateRole(Long id, RoleSaveCommand command)`。角色不存在时抛 `IllegalArgumentException("角色不存在")`。保留原 code，除非 `command.getCode()` 有值。`command.getMenuIds() != null` 时整体替换菜单授权。
- Produces: `PUT /api/admin/roles/{id}`，请求体 `RoleSaveCommand`，权限码 `role:save`（V49:30 的「编辑角色」按钮就是 `role:save`），返回 `R<Void>`。

- [ ] **Step 1：写失败的服务层测试**

```java
package com.agri_system.application.staff.service;

import com.agri_system.application.common.service.CodeGenerationApplicationService;
import com.agri_system.application.staff.dto.command.RoleSaveCommand;
import com.agri_system.domain.store.repository.StaffRepository;
import com.agri_system.domain.system.auth.model.SysRole;
import com.agri_system.domain.system.auth.repository.SysMenuRepository;
import com.agri_system.domain.system.auth.repository.SysRoleMenuRepository;
import com.agri_system.domain.system.auth.repository.SysRoleRepository;
import com.agri_system.domain.system.auth.repository.SysStaffRoleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StaffAuthorityServiceRoleUpdateTest {

    @Mock StaffRepository staffRepository;
    @Mock SysRoleRepository roleRepository;
    @Mock SysMenuRepository menuRepository;
    @Mock SysRoleMenuRepository roleMenuRepository;
    @Mock SysStaffRoleRepository staffRoleRepository;
    @Mock CodeGenerationApplicationService codeGenerationService;

    @InjectMocks StaffAuthorityService service;

    private SysRole existingRole() {
        SysRole role = new SysRole();
        role.setId(5L);
        role.setName("店长");
        role.setCode("ROLE_0005");
        role.setStatus(1);
        return role;
    }

    @Test
    void updateRoleKeepsExistingCodeAndReplacesMenus() {
        when(roleRepository.findById(5L)).thenReturn(Optional.of(existingRole()));
        RoleSaveCommand command = new RoleSaveCommand();
        command.setName("门店店长");
        command.setStatus(0);
        command.setMenuIds(List.of(100L, 200L));

        service.updateRole(5L, command);

        ArgumentCaptor<SysRole> saved = ArgumentCaptor.forClass(SysRole.class);
        verify(roleRepository).save(saved.capture());
        assertEquals(5L, saved.getValue().getId());
        assertEquals("门店店长", saved.getValue().getName());
        assertEquals("ROLE_0005", saved.getValue().getCode());
        assertEquals(0, saved.getValue().getStatus());
        verifyNoInteractions(codeGenerationService);
        verify(roleMenuRepository).deleteByRoleId(5L);
        verify(roleMenuRepository).batchInsert(5L, List.of(100L, 200L));
    }

    @Test
    void updateRoleLeavesMenusUntouchedWhenMenuIdsAbsent() {
        when(roleRepository.findById(5L)).thenReturn(Optional.of(existingRole()));
        RoleSaveCommand command = new RoleSaveCommand();
        command.setName("门店店长");

        service.updateRole(5L, command);

        verify(roleMenuRepository, never()).deleteByRoleId(anyLong());
        verify(roleMenuRepository, never()).batchInsert(anyLong(), any());
    }

    @Test
    void updateRoleRejectsMissingRole() {
        when(roleRepository.findById(9L)).thenReturn(Optional.empty());
        RoleSaveCommand command = new RoleSaveCommand();
        command.setName("x");

        assertThrows(IllegalArgumentException.class, () -> service.updateRole(9L, command));
        verify(roleRepository, never()).save(any());
    }
}
```

- [ ] **Step 2：确认测试失败**

Run: `<JDK17前缀> ./mvnw -q -pl core-application -am test -Dtest=StaffAuthorityServiceRoleUpdateTest -Dsurefire.failIfNoSpecifiedTests=false > /tmp/closure-t1-red.log 2>&1; echo exit=$?`
Expected：编译失败，报 `cannot find symbol ... updateRole`。

- [ ] **Step 3：实现 `updateRole`**（放在 `saveRole` 方法之后）

```java
    @Transactional(rollbackFor = Exception.class)
    public void updateRole(Long id, RoleSaveCommand command) {
        SysRole role = roleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("角色不存在"));
        role.setName(command.getName());
        if (StringUtils.hasText(command.getCode())) {
            role.setCode(command.getCode().trim());
        }
        if (command.getStatus() != null) {
            role.setStatus(command.getStatus());
        }
        roleRepository.save(role);
        if (command.getMenuIds() != null) {
            roleMenuRepository.deleteByRoleId(id);
            roleMenuRepository.batchInsert(id, command.getMenuIds());
        }
    }
```

- [ ] **Step 4：写控制器测试**

```java
package com.agri_system.api.admin.controller;

import com.agri_system.api.admin.config.TestApplication;
import com.agri_system.application.staff.dto.command.RoleSaveCommand;
import com.agri_system.application.staff.service.StaffAuthorityService;
import com.agri_system.domain.system.auth.repository.SysStaffRoleRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest
@ContextConfiguration(classes = {TestApplication.class, RoleController.class})
class RoleControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean StaffAuthorityService authorityService;
    @MockBean SysStaffRoleRepository staffRoleRepository;

    @Test
    void putUpdatesRoleById() throws Exception {
        RoleSaveCommand body = new RoleSaveCommand();
        body.setName("门店店长");
        body.setStatus(1);
        body.setMenuIds(List.of(100L));

        mockMvc.perform(put("/api/admin/roles/5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());

        ArgumentCaptor<RoleSaveCommand> captor = ArgumentCaptor.forClass(RoleSaveCommand.class);
        verify(authorityService).updateRole(eq(5L), captor.capture());
        assertEquals("门店店长", captor.getValue().getName());
    }
}
```

- [ ] **Step 5：在 `RoleController` 的 `save` 之后新增 PUT**

```java
    @PutMapping("/{id}")
    @Operation(summary = "编辑角色")
    @SaCheckPermission("role:save")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody RoleSaveCommand command) {
        authorityService.updateRole(id, command);
        return R.success();
    }
```

- [ ] **Step 6：跑两个新测试，确认通过**

Run: `<JDK17前缀> ./mvnw -q -pl api-admin -am test -Dtest='StaffAuthorityServiceRoleUpdateTest,RoleControllerTest' -Dsurefire.failIfNoSpecifiedTests=false > /tmp/closure-t1-green.log 2>&1; echo exit=$?`
Expected：exit=0，两个类的 surefire 报告 Failures 0、Errors 0。

- [ ] **Step 7：提交**

```bash
git add core-application/src/main/java/com/agri_system/application/staff/service/StaffAuthorityService.java \
        api-admin/src/main/java/com/agri_system/api/admin/controller/RoleController.java \
        core-application/src/test/java/com/agri_system/application/staff/service/StaffAuthorityServiceRoleUpdateTest.java \
        api-admin/src/test/java/com/agri_system/api/admin/controller/RoleControllerTest.java
git commit -m "fix: 补角色编辑 PUT 接口,编辑时保留原角色编码"
```

---

### Task 2：月度结算不覆盖已确认工资单

**问题：** `PerformanceSettlementJob.java:46-55` 对已有工资单做 upsert 时，无条件写 `status=0` 并覆盖金额。重跑上月结算会把已确认（已实发）的工资单打回待确认，可以再确认一次，等于二次实发。这和 `confirmSalaryBill` 的防重复加固（`PerformanceApplicationService.java:89-99`）自相矛盾。另外 `createdAt` 每次都被刷新，丢失了首次生成时间。

**Files:**
- Modify: `core-application/src/main/java/com/agri_system/application/performance/job/PerformanceSettlementJob.java:46-55`
- Modify: `core-application/src/test/java/com/agri_system/application/performance/job/PerformanceSettlementFreezeTest.java`（追加 2 个测试）

**Interfaces:** 无对外签名变化。工资单状态语义见 `V45__performance_enhanced.sql`：`status` 0 表示待确认，1 表示已确认。

- [ ] **Step 1：在 `PerformanceSettlementFreezeTest` 末尾追加失败测试**（需要新增 import：`java.time.LocalDateTime`、`org.mockito.ArgumentCaptor`、`static org.junit.jupiter.api.Assertions.assertEquals`）

```java
    @Test
    void skipsBillAlreadyConfirmed() {
        StaffSalaryBill confirmed = new StaffSalaryBill();
        confirmed.setId(7L);
        confirmed.setStaffId(2L);
        confirmed.setStatus(1);
        confirmed.setTotalCommission(new BigDecimal("80.00"));
        when(logRepository.findStaffIdsWithPayableInMonth(anyString())).thenReturn(List.of(2L));
        when(logRepository.sumPayableByFreezeMonth(eq(2L), any(), any())).thenReturn(new BigDecimal("100.00"));
        when(orderRepository.sumPayAmountByStaffAndMonth(eq(2L), anyString())).thenReturn(new BigDecimal("500.00"));
        when(relationRepository.countFirstEffectiveByMonth(eq(2L), anyString())).thenReturn(1L);
        when(salaryBillRepository.findByStaffAndMonth(eq(2L), anyString())).thenReturn(Optional.of(confirmed));

        job.settleLastMonth();

        verify(salaryBillRepository, never()).save(any());
    }

    @Test
    void refreshesPendingBillButKeepsOriginalCreatedAt() {
        LocalDateTime firstCreated = LocalDateTime.of(2026, 8, 1, 4, 0);
        StaffSalaryBill pending = new StaffSalaryBill();
        pending.setId(8L);
        pending.setStaffId(2L);
        pending.setStatus(0);
        pending.setCreatedAt(firstCreated);
        when(logRepository.findStaffIdsWithPayableInMonth(anyString())).thenReturn(List.of(2L));
        when(logRepository.sumPayableByFreezeMonth(eq(2L), any(), any())).thenReturn(new BigDecimal("100.00"));
        when(orderRepository.sumPayAmountByStaffAndMonth(eq(2L), anyString())).thenReturn(new BigDecimal("500.00"));
        when(relationRepository.countFirstEffectiveByMonth(eq(2L), anyString())).thenReturn(1L);
        when(salaryBillRepository.findByStaffAndMonth(eq(2L), anyString())).thenReturn(Optional.of(pending));

        job.settleLastMonth();

        ArgumentCaptor<StaffSalaryBill> saved = ArgumentCaptor.forClass(StaffSalaryBill.class);
        verify(salaryBillRepository).save(saved.capture());
        assertEquals(new BigDecimal("100.00"), saved.getValue().getTotalCommission());
        assertEquals(0, saved.getValue().getStatus());
        assertEquals(firstCreated, saved.getValue().getCreatedAt());
    }
```

- [ ] **Step 2：确认失败**

Run: `<JDK17前缀> ./mvnw -q -pl core-application -am test -Dtest=PerformanceSettlementFreezeTest -Dsurefire.failIfNoSpecifiedTests=false > /tmp/closure-t2-red.log 2>&1; echo exit=$?`
Expected：`Tests run: 3, Failures: 2`。`skipsBillAlreadyConfirmed` 因 save 被调用而失败，`refreshesPendingBill...` 因 createdAt 被覆盖而失败。

- [ ] **Step 3：把 `:46-55` 替换为**

```java
            StaffSalaryBill bill = salaryBillRepository.findByStaffAndMonth(staffId, target.toString())
                    .orElse(new StaffSalaryBill());
            if (Integer.valueOf(1).equals(bill.getStatus())) {
                log.warn("Salary bill already confirmed, skip re-settlement: staffId={}, month={}", staffId, target);
                continue;
            }
            bill.setStaffId(staffId);
            bill.setMonth(target.toString());
            bill.setTotalCommission(commission);
            bill.setTotalSales(sales);
            bill.setNewCustomerCount((int) newCustomer);
            bill.setStatus(0);
            if (bill.getCreatedAt() == null) {
                bill.setCreatedAt(LocalDateTime.now());
            }
            salaryBillRepository.save(bill);
```

- [ ] **Step 4：确认通过**：用 Step 2 的命令，Expected `Tests run: 3, Failures: 0, Errors: 0`，exit=0。

- [ ] **Step 5：提交**

```bash
git add core-application/src/main/java/com/agri_system/application/performance/job/PerformanceSettlementJob.java \
        core-application/src/test/java/com/agri_system/application/performance/job/PerformanceSettlementFreezeTest.java
git commit -m "fix: 月度结算跳过已确认工资单,防止重跑结算导致二次实发"
```

---

### Task 3：V128 补菜单入口与授权

**问题：** 后台路由只有在 `sys_menu` 里有同 path 的页面行、且该行授权给了当前角色时才会注册（`admin-web/src/router/registry.ts:416-418, 482-484`）。审计核实了四个入口进不去：

1. 工作台 `/dashboard`（menu 2600）：V112:96 授权列表里把 2600 写成了不存在的 `1250`，因此任何角色都没有被授予 2600。
2. 库存日志 `/inventory/log`（registry.ts:196-204）：只有按钮 206（`inventory:log:list`，type=2），没有页面行。
3. 库存调拨 `/inventory/transfer`（registry.ts:206-214）：只有按钮 203（`inventory:transfer`，type=2），没有页面行。
4. 客户跟进 `/marketing/customer/follow-up`（registry.ts:267-275）：V115:14 建的 2702 被 V117:21-31 覆盖成了「库存预警」，跟进页面行不复存在。

**授权口径（裁定）：** 新页面授给 SUPER_ADMIN（role 1），另外授给已经持有对应按钮权限的角色。这样既不给没有该权限的角色扩权，也让已经有权限的角色能看到页面。迁移里不引用 `sys_role` 表，因为 H2 迁移测试的基线投影里没有这张表；按钮权限一律通过 `sys_role_menu` 推导。

**Files:**
- Create: `core-infrastructure/src/main/resources/db/migration/V128__restore_missing_page_menus.sql`
- Modify: `core-infrastructure/src/test/java/com/agri_system/infrastructure/migration/RetailFactChainMigrationTest.java`（在基线投影里补按钮行，版本上限 127 改为 128，新增一个测试）

**Interfaces:**
- Produces 菜单行：2707 `/inventory/log`（parent 2100，`inventory:log:list`），2708 `/inventory/transfer`（parent 2100，`inventory:transfer`），2709 `/marketing/customer/follow-up`（parent 2300，`customer:followup:manage`）。2600 授给 role 1。

- [ ] **Step 1：修改迁移测试（先失败）**

1a. 在 `migrateFromLegacySchema()` 基线 SQL 的 `sys_role_menu` 建表语句之后、`INSERT INTO store` 之前，插入：

```sql
                -- V128(补页面菜单)按「已持有按钮权限的角色」推导授权,基线需含对应按钮与授权行
                INSERT INTO `sys_menu` (`id`, `parent_id`, `name`, `permission`, `type`, `sort_order`, `path`, `icon`, `visible`) VALUES
                    (203, 200, '调拨', 'inventory:transfer', 2, 30, NULL, NULL, 1),
                    (206, 200, '库存日志', 'inventory:log:list', 2, 60, NULL, NULL, 1),
                    (1409, 0, '客户跟进管理', 'customer:followup:manage', 2, 130, NULL, NULL, 1),
                    (2600, 0, '工作台', 'report:dashboard', 1, 10, '/dashboard', 'Odometer', 1);
                INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (3, 203), (3, 206), (4, 1409);
```

1b. 把 `:170` 和 `:268` 的 `assertEquals("127", ...)` 改为 `"128"`。把 `:260` 的 `fromVersion("127")` 改为 `fromVersion("128")`，把 `:261` 的提示文字改为 `"存在超出 V1..V128 范围的迁移版本"`。`:262-267` 的 `containsAll` 列表末尾追加 `MigrationVersion.fromVersion("128")`，提示文字改为 `"缺少 V119/V120/V121/V123/V124/V125/V126/V127/V128"`。

1c. 在 `workScreenMenuMigrationRegistersFourMenusAndRoleGrants()` 之后新增：

```java
    @Test
    void restoreMissingPageMenusRegistersPagesAndDerivesGrants() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        assertMenuRow(jdbc, 2707L, 2100L, "库存日志", "inventory:log:list", "/inventory/log");
        assertMenuRow(jdbc, 2708L, 2100L, "库存调拨", "inventory:transfer", "/inventory/transfer");
        assertMenuRow(jdbc, 2709L, 2300L, "客户跟进", "customer:followup:manage", "/marketing/customer/follow-up");
        assertEquals(Integer.valueOf(1), jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_role_menu WHERE role_id = 1 AND menu_id = 2600", Integer.class));
        assertEquals(Integer.valueOf(1), jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_role_menu WHERE role_id = 3 AND menu_id = 2707", Integer.class));
        assertEquals(Integer.valueOf(1), jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_role_menu WHERE role_id = 3 AND menu_id = 2708", Integer.class));
        assertEquals(Integer.valueOf(1), jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_role_menu WHERE role_id = 4 AND menu_id = 2709", Integer.class));
        assertEquals(Integer.valueOf(0), jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_role_menu WHERE role_id = 4 AND menu_id IN (2707, 2708)", Integer.class));
    }
```

注：H2 基线（V118 之后）里没有 2702，所以这里不断言它；V128 不写 2702，这一点在 Step 5 的真实 MySQL 门控里核对。

- [ ] **Step 2：确认失败**

Run: `<JDK17前缀> ./mvnw -q -pl core-infrastructure -am test -Dtest=RetailFactChainMigrationTest -Dsurefire.failIfNoSpecifiedTests=false > /tmp/closure-t3-red.log 2>&1; echo exit=$?`
Expected：exit≠0。版本断言报 `expected: <128> but was: <127>`，新测试报 2707 行不存在。

- [ ] **Step 3：写迁移 `V128__restore_missing_page_menus.sql`**

```sql
-- V128: 补齐后台页面入口菜单与授权
-- 1) V112 授权列表把 2600(工作台) 误写为 1250,工作台从未授予任何角色
-- 2) 库存日志/库存调拨只有按钮权限(206/203),没有页面菜单行,路由无法注册
-- 3) 客户跟进页面菜单 2702 被 V117 覆盖为「库存预警」,改用新 id 2709 重建

INSERT INTO sys_menu (id, parent_id, name, permission, type, sort_order, path, icon, visible) VALUES
(2707, 2100, '库存日志', 'inventory:log:list', 1, 30, '/inventory/log', 'Document', 1),
(2708, 2100, '库存调拨', 'inventory:transfer', 1, 40, '/inventory/transfer', 'Switch', 1),
(2709, 2300, '客户跟进', 'customer:followup:manage', 1, 50, '/marketing/customer/follow-up', 'ChatDotRound', 1)
ON DUPLICATE KEY UPDATE
    parent_id = VALUES(parent_id),
    name = VALUES(name),
    permission = VALUES(permission),
    type = VALUES(type),
    sort_order = VALUES(sort_order),
    path = VALUES(path),
    icon = VALUES(icon),
    visible = VALUES(visible);

INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (1, 2600), (1, 2707), (1, 2708), (1, 2709);

-- 已持有对应按钮权限的角色,同步获得页面入口
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT rm.role_id, 2707 FROM sys_role_menu rm
WHERE rm.menu_id IN (SELECT m.id FROM sys_menu m WHERE m.permission = 'inventory:log:list' AND m.type = 2);

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT rm.role_id, 2708 FROM sys_role_menu rm
WHERE rm.menu_id IN (SELECT m.id FROM sys_menu m WHERE m.permission = 'inventory:transfer' AND m.type = 2);

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT rm.role_id, 2709 FROM sys_role_menu rm
WHERE rm.menu_id IN (SELECT m.id FROM sys_menu m WHERE m.permission = 'customer:followup:manage' AND m.type = 2);
```

- [ ] **Step 4：确认 H2 迁移测试通过**：用 Step 2 的命令，Expected exit=0，`RetailFactChainMigrationTest` Failures 0、Errors 0。

- [ ] **Step 5：真实 MySQL 门控**（E2E 会在一次性库 `agri_retail_e2e` 上把全部迁移跑到 LATEST，包括 V128，并在结束后删库）

Run: `<JDK17前缀> ./mvnw -q -pl api-admin -am test -Dmysql.it=true > /tmp/closure-t3-mysql.log 2>&1; echo exit=$?`
Expected：exit=0，`RetailFactChainEndToEndTest` 为 `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`，`mysql -uroot -h127.0.0.1 -N -e "SHOW DATABASES LIKE 'agri_retail%'"` 输出为空。

- [ ] **Step 6：提交**

```bash
git add core-infrastructure/src/main/resources/db/migration/V128__restore_missing_page_menus.sql \
        core-infrastructure/src/test/java/com/agri_system/infrastructure/migration/RetailFactChainMigrationTest.java
git commit -m "fix: V128 补工作台授权与库存日志/调拨/客户跟进页面菜单"
```

---

### Task 4：关闭小程序「自行确认外部支付」入口（配置闸门）

**问题：** `POST /api/client/orders/{orderId}/payment-confirmation`（`OrderClientController.java:85-92`）调用 `OrderApplicationService.confirmExternalPayment`（`:726-733`）。这个方法只校验意图属于当前客户，然后就接受客户自己提交的任意 `externalTransactionNo`：订单直接变成「已支付」并扣库存，中间不向任何网关核实。**已登录客户不付钱就能完成订单。**

**裁定：** 真实网关（阶段 2）接入之前，加配置闸门 `payment.client-confirm.enabled`，默认 `false`，由服务层强制执行。只有 api-client 的 `application-dev.yml` 显式开启，便于本地联调；prod/test 不配，即关闭。网关回调入口 `confirmExternalPaymentCallback` 不受影响。如果这个裁定错了，代价是 dev 以外的环境无法用客户端确认支付，而这正是期望的效果。

**Files:**
- Modify: `core-application/src/main/java/com/agri_system/application/order/service/OrderApplicationService.java`（新增字段和闸门）
- Modify: `core-application/src/test/java/com/agri_system/application/order/service/OrderApplicationServicePaymentFlowTest.java`
- Modify: `api-client/src/main/resources/application-dev.yml`

**Interfaces:** `confirmExternalPayment(OrderExternalPaymentConfirmCommand, Long)` 签名不变。闸门关闭时抛 `IllegalStateException("客户端确认支付已关闭，请等待支付平台回调确认")`，且在读取支付意图之前就抛出。

- [ ] **Step 1：写失败测试并调整既有测试**

1a. 新增 import `org.springframework.test.util.ReflectionTestUtils`。

1b. 在 `confirmExternalPaymentFinalizesOrderWhenPreviewMatches`、`confirmExternalPaymentRejectsWhenExternalAmountChanged`、`confirmExternalPaymentReturnsWhenIntentAlreadyConfirmedWithSameTransaction` 三个测试方法的第一行各加：

```java
        ReflectionTestUtils.setField(orderService, "clientPaymentConfirmEnabled", true);
```

1c. 在这三个测试之后新增：

```java
    @Test
    void confirmExternalPaymentRejectedWhenClientConfirmDisabled() {
        OrderExternalPaymentConfirmCommand command = new OrderExternalPaymentConfirmCommand();
        command.setIntentNo("PI-ANY");
        command.setExternalTransactionNo("forged-transaction");

        assertThrows(IllegalStateException.class,
                () -> orderService.confirmExternalPayment(command, 1L));
        verify(paymentIntentRepository, never()).findByIntentNo(any());
        verify(paymentService, never()).executePayment(any(), any(), any(), any(), any(), anyBoolean(), any(), any(), any());
        verify(inventoryService, never()).outbound(any(), any());
    }
```

- [ ] **Step 2：确认失败**

Run: `<JDK17前缀> ./mvnw -q -pl core-application -am test -Dtest=OrderApplicationServicePaymentFlowTest -Dsurefire.failIfNoSpecifiedTests=false > /tmp/closure-t4-red.log 2>&1; echo exit=$?`
Expected：exit≠0。`ReflectionTestUtils.setField` 报找不到字段 `clientPaymentConfirmEnabled`（IllegalArgumentException），新测试也失败。

- [ ] **Step 3：实现闸门**

3a. import 区新增 `import org.springframework.beans.factory.annotation.Value;`。

3b. 在 `private final ManualPriceAuditService manualPriceAuditService;`（`:96`）之后新增：

```java

    @Value("${payment.client-confirm.enabled:false}")
    private boolean clientPaymentConfirmEnabled;
```

3c. 在 `confirmExternalPayment` 方法体第一行插入：

```java
        if (!clientPaymentConfirmEnabled) {
            throw new IllegalStateException("客户端确认支付已关闭，请等待支付平台回调确认");
        }
```

- [ ] **Step 4：dev 环境显式开启**：在 `api-client/src/main/resources/application-dev.yml` 末尾追加

```yaml

# 客户端自行确认外部支付：仅限本地联调。未接入真实支付网关前，prod/test 必须保持关闭（默认 false）
payment:
  client-confirm:
    enabled: true
```

追加前先用 `grep -n "^payment:" api-client/src/main/resources/application-dev.yml` 确认文件里还没有顶层 `payment:` 键。如果已经存在，就把 `client-confirm` 合并到那个键下面，不要重复声明。

- [ ] **Step 5：确认通过**：用 Step 2 的命令，Expected exit=0，`OrderApplicationServicePaymentFlowTest` Failures 0、Errors 0。

- [ ] **Step 6：提交**

```bash
git add core-application/src/main/java/com/agri_system/application/order/service/OrderApplicationService.java \
        core-application/src/test/java/com/agri_system/application/order/service/OrderApplicationServicePaymentFlowTest.java \
        api-client/src/main/resources/application-dev.yml
git commit -m "fix: 客户端自行确认外部支付加配置闸门,默认关闭,仅 dev 联调开启"
```

---

### Task 5：移植 cerith 的订阅消息 `templates: {}` 修正

**问题：** `api-client/src/main/resources/application-dev.yml:56` 的 `templates:` 下只有注释，YAML 会把它解析成 null。这会覆盖 `WechatSubscribeMessageProperties.templates` 的默认值 `Map.of()`，而 prod/test 两个 yml 早已写成 `templates: {}`。cerith 工作区里有同样的一行修正，还没有提交；master 上缺这一行。cerith 另外两处改动已被 master 上更完整的实现取代，无需移植。

**Files:** Modify: `api-client/src/main/resources/application-dev.yml:56`

- [ ] **Step 1**：把第 56 行 `    templates:` 改为 `    templates: {}`，下面的注释行保持不动。
- [ ] **Step 2**：`grep -n "templates" api-client/src/main/resources/application-*.yml`，Expected：dev/prod/test 三行都是 `templates: {}`。
- [ ] **Step 3**：提交

```bash
git add api-client/src/main/resources/application-dev.yml
git commit -m "fix: dev 环境订阅消息 templates 显式置空 map,与 prod/test 一致"
```

---

### 阶段 1 收口门控

- [ ] **G1 默认回归**：`<JDK17前缀> ./mvnw -q -pl api-admin -am test > /tmp/closure-g1.log 2>&1; echo exit=$?`，Expected exit=0。
- [ ] **G2 api-client 编译**：`<JDK17前缀> ./mvnw -q -pl api-client -am -DskipTests package > /tmp/closure-g2.log 2>&1; echo exit=$?`，Expected exit=0。
- [ ] **G3 前端构建**（本阶段没有改前端，只确认没有破坏）：`cd admin-web && npm run build > /tmp/closure-g3.log 2>&1; echo exit=$?`，Expected exit=0。
- [ ] **G4 真实 MySQL 门控**：即 Task 3 Step 5 的命令，Expected exit=0，且没有残留的 `agri_retail%` 库。
- [ ] **G5 文档**：更新 `docs/WORKING_STATE.md`。删除/改写审计 C 节列出的过时行（L16、L20-24、L112-113、L120-124、L163-170）：零售事实链改为「已交付、休眠待激活，激活按 runbook 执行」，迁移写到 V128，补一句提成冻结生命周期（V118/V122，`freeze-enabled` 默认关闭）。在「已确认问题」中移除本阶段已修复的条目，并把本计划列为「接下来的开发顺序」的依据。单独提交：`docs: 同步 WORKING_STATE 至阶段1收尾后的真实状态`。
- [ ] **G6 合并**：在 master 上执行 `git merge --no-ff closure`（与 `eac5be4`、`ed90bdb` 先例一致），合并后在 master 上重跑 G1。**不 push。**

---

## 阶段 2（P0-B）：真实支付闭环 ⛔ 外部条件阻塞

**前置条件（需要用户确认具备后才开工）：** 微信支付商户号与 API v3 密钥、商户证书，小程序已开通支付；支付宝（如需要）的应用与密钥；可以公网访问的回调地址。

| 条目 | 证据 | 验收标准 |
|---|---|---|
| 统一下单：`createExternalPaymentIntent` 生成平台支付参数（prepay_id、paySign） | `OrderApplicationService.java:688-721` 只落库意图 | 小程序拿到参数后能拉起支付 |
| 回调验签：把共享密钥头替换为平台签名验签 + 报文解密 | `OrderPaymentPublicController.java:34-56`（`String.equals` 比较密钥） | 伪造回调被拒；重复回调幂等 |
| 下线客户端确认入口：网关上线后删除 `payment-confirmation` 与阶段 1 的闸门 | 阶段 1 Task 4 | 客户端无法确认支付 |
| 原路退款：CASH/WECHAT/ALIPAY 退款分支真正退款或留痕 | `OrderPaymentService.java:332-336`（只有 TODO 注释）；调用方 `AfterSalesApplicationService.java:180, :258` | 新增退款流水表（V1xx），每笔退款有流水和状态 |
| 现金退款留痕（不依赖网关，可提前做） | 同上（现金分支什么都不记） | 现金退款落流水，进入对账 |
| 补偿与对账：意图过期扫描、主动查单补单、日对账 | 仓库内没有这类任务 | 定时任务 + 差异报表 |
| 待支付订单超时关闭 / 客户取消 | `Order.markAsCancelled()` 只有售后在调用 | 超时自动关单，客户可取消待支付订单 |

## 阶段 3（P0-C）：小程序端接口补齐

证据：`api-client` 现在只有登录、下单/支付、订单详情/确认收货、售后申请/取消、积分商城、钱包验证码、qrBind 这些接口。`createOrder` 需要 productId 和 storeId，客户端却没有任何地方能查到它们。仓库里也没有小程序前端工程。

范围（每一项都要有：接口 + 服务方法 + 测试；客户身份一律取自 Sa-Token，不信任请求参数）：
1. 商品/分类浏览、商品详情（只返回上架商品，价格走 `ProductPricingService` 的客户渠道报价）。
2. 门店列表（支持自提的门店）。
3. 我的订单列表（分页 + 状态筛选）、取消待支付订单（和阶段 2 的关单逻辑共用领域方法）。
4. 个人资料、钱包余额与流水、我的优惠券、积分余额与流水。
5. 售后列表与详情。
6. api-client 模块的首批测试（目前是 0）。

验收：用上面这些接口能走完一次完整购物（浏览 → 下单 → 支付预览 → 意图 →〔dev 闸门下〕确认 → 我的订单 → 确认收货 → 售后）。

## 阶段 4（P1）：功能缺口

| # | 条目 | 证据 | 修法要点 |
|---|---|---|---|
| 4.1 | 充值事件从未发布，充值提成和充值通知都是死代码 | 找不到 `new CustomerRechargeEvent(`；监听方在 `PerformanceEventListener.java:204-235`、`CustomerNotificationEventListener.java:64-78` | 在 `WalletRechargeService.recharge` 成功后发布事件；`RechargeCommand` 增加 `guideStaffId` |
| 4.2 | 自动收货不发 `OrderFinishedEvent`、不回写配送单 | `OrderAutoFinishJob.java:43-44`；对比手动路径 `OrderApplicationService.java:1582-1587` | 抽出共用的完成方法，逐单独立事务 |
| 4.3 | VIP 月度权益一人失败全员回滚 | `VipMonthlyBenefitJob.java:32-33, :59-62` | 去掉外层事务，或每位会员用 `REQUIRES_NEW` |
| 4.4 | 二维码图片是假的，员工-客户绑定和拉新提成端到端不通 | `OneTimeQrService.java:42-44`；前端没有入口（menu 900 没有页面） | 接入 wxacode（需要 appId），管理端二维码页面 |
| 4.5 | 二维码 create 信任参数里的 staffId；qrBind 信任请求体里的 customerId | `QrAdminController.java:23-28`、`QrClientController.java:25-28` | 身份一律从登录会话取（不依赖外部条件，可优先做） |
| 4.6 | 系统通知中心没有写入方 | `SystemNotification.java:59/73` 的工厂方法没人调用 | 库存预警、提成到账时落库通知 |
| 4.7 | 开发登录只靠 `wechat.app-id` 为空来放行 | `AuthService.java:194-197` | 追加 profile 闸门（仅 dev 生效） |
| 4.8 | 赊销页关键字搜索无效，汇总卡片只统计当前页 | `views/credit/index.vue:180-192, 125-144`；`/credit/statistics` 没被用上 | 后端加 keyword；汇总改调 statistics |
| 4.9 | 字典类型写死在前端，缺 `customer_type` | `views/system/dict/index.vue:167-175` | 后端提供字典类型列表接口 |
| 4.10 | 客户专用金/余额发放没有 UI | `CustomerAdminController.java:266, :311` | 客户列表加发放对话框 |
| 4.11 | 提成冻结态在前端不可见 | `StaffPerformanceLogVO` 没有 status/freezeUntil | VO 补字段，绩效页显示「冻结中/可发放」 |
| 4.12 | 菜单管理 `/system/menu` 有菜单没页面 | menu 4；`MenuController` 只有读接口 | 要么补页面，要么隐藏该菜单 |
| 4.13 | 品牌推广类别只是预留 | `PerformanceApplicationService.java:209-221` | 需要业务定义结算事件，先挂起 |

## 阶段 5（P2 与文档）

- 调拨审批/在途/签收（`InventoryApplicationService.java:324-357` 一步完成）。
- 定时任务分布式锁（`SchedulingConfig.java:20-21` 只按应用区分开关）。
- 绩效记录 `storeId=0`（`PerformanceEventListener.java:227, :285`）。
- 积分清零单个大事务（`PointsClearJob.java:41, :55`）。
- 孤儿表：`customer_follow_up`、`inventory_adjustment`、`inventory_warning`、`staff_performance`（决定补功能还是弃用）。
- 死代码：`PrintAdminController`、`WalletApplicationService.validateAndConsumeWalletAuthCode`、废弃端点 `/vip-monthly-records`、重复看板 `StaffDashboardController`。
- 页面可见但接口 403 的权限错配（store-analytics、`getCreditRisk`、`getStaffList`）。
- 分层清理：`core-application` 直接依赖 infrastructure 的 15 处 import、`ProductApplicationService.java:271-272` 强转实现类。
- 文档：支付编码口径（`ddl/schema.sql:434`、`ddl/DATABASE_DESIGN.md:349, :798-803`、`ddl/er-diagram.md:433-438`）；`ddl/README.md` 的 Flyway 清单（停在 V10）；`README.md` / `docs/README.md` 的文档入口补上 runbooks、superpowers。
- 旧路由 alias `admin-web/src/store/permission.ts:7-11`。
