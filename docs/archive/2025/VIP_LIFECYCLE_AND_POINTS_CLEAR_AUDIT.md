> 文档状态：归档历史资料，不是现行开发/部署/建库依据。现行入口见[统一文档中心](../../current/README.md)。

# VIP生命周期服务与积分年度清零任务审计报告

## 一、功能实现检查

### 1. VIP生命周期服务 (VipLifecycleService)

#### ✅ 已实现功能

1. **VIP月度权益发放 (`grantMonthlyBenefit`)**
   - ✅ 幂等性检查（通过 `vip_monthly_record` 表）
   - ✅ VIP状态验证（检查是否为VIP、是否过期）
   - ✅ 支持多种权益类型（送券、送专用金）
   - ✅ 优惠券发放校验（状态、总量限制）
   - ✅ 异常容错处理（单个权益失败不影响其他权益）
   - ✅ 双写机制（`vip_monthly_record` + `customer_benefit_log`）
   - ✅ 专用金钱包自动创建（如果不存在）
   - ✅ 流水记录完整

2. **VIP降级任务 (`downgradeExpiredVips`)**
   - ✅ 查询所有VIP客户
   - ✅ 检查VIP过期状态
   - ✅ 降级为普通客户
   - ✅ 日志记录

#### ❌ 缺失功能

1. **VIP月度权益发放定时任务**
   - ❌ **严重问题**：`grantMonthlyBenefit` 方法已实现，但**没有定时任务调用它**
   - 需要创建 `VipMonthlyBenefitJob` 定时任务

2. **VIP降级定时任务**
   - ❌ **严重问题**：`downgradeExpiredVips` 方法已实现，但**没有定时任务调用它**
   - 需要创建 `VipDowngradeJob` 定时任务

### 2. 积分年度清零任务 (PointsClearJob)

#### ✅ 已实现功能

1. **定时任务配置**
   - ✅ Cron表达式：`0 0 0 1 1 ?`（每年1月1日00:00:00）
   - ✅ 事务管理：`@Transactional`
   - ✅ 日志记录

2. **业务逻辑**
   - ✅ 查询所有积分钱包（Type=3）
   - ✅ 跳过余额为0的账户
   - ✅ 重置余额为0
   - ✅ 记录流水（扣除类型）

#### ⚠️ 潜在问题

1. **性能问题**
   - ⚠️ 代码注释提到"应该使用分页查询，避免一次性加载过多数据"
   - ⚠️ 当前实现使用 `findAllByWalletType(3)` 一次性加载所有积分钱包
   - ⚠️ 如果客户数量很大（如10万+），可能导致内存溢出或数据库连接超时

2. **并发安全**
   - ⚠️ 没有使用乐观锁或悲观锁
   - ⚠️ 如果任务执行时间较长，可能存在并发问题

3. **错误处理**
   - ⚠️ 如果某个客户清零失败，会回滚整个事务
   - ⚠️ 建议改为批量处理，单个失败不影响其他客户

## 二、安全性检查

### 1. 数据一致性

#### ✅ 优点
- VIP月度权益发放使用事务管理
- 积分清零使用事务管理
- VIP降级使用事务管理

#### ⚠️ 风险点
- 积分清零任务如果失败，会回滚所有操作，可能导致部分客户已清零但事务回滚的情况

### 2. 幂等性

#### ✅ VIP月度权益发放
- 通过 `vip_monthly_record` 表的唯一索引 `uk_customer_month` 保证幂等性
- 如果已发放，直接跳过

#### ✅ 积分清零
- 每年只执行一次（1月1日）
- 但缺少幂等性检查，如果任务执行失败重试，可能重复清零

### 3. 异常处理

#### ✅ VIP月度权益发放
- 单个权益失败不影响其他权益
- 记录失败日志到 `customer_benefit_log`
- 优惠券发放失败有专门的异常处理

#### ⚠️ 积分清零
- 如果某个客户处理失败，整个事务回滚
- 建议改为批量处理，单个失败不影响其他客户

## 三、需要修复的问题

### 1. 创建VIP月度权益发放定时任务

**文件：** `core-application/src/main/java/com/agri_system/application/customer/job/VipMonthlyBenefitJob.java`

**功能：** 每月1日00:00:00执行，为所有有效VIP客户发放月度权益

**Cron表达式：** `0 0 0 1 * ?`（每月1日00:00:00）

### 2. 创建VIP降级定时任务

**文件：** `core-application/src/main/java/com/agri_system/application/customer/job/VipDowngradeJob.java`

**功能：** 每天凌晨执行，检查并降级过期的VIP客户

**Cron表达式：** `0 0 2 * * ?`（每天凌晨2点）

### 3. 优化积分清零任务

**改进点：**
- 使用分页查询，批量处理
- 添加幂等性检查
- 单个客户失败不影响其他客户
- 添加乐观锁或悲观锁

## 四、测试方法

### 1. VIP月度权益发放测试

#### 测试用例1：正常发放
```sql
-- 准备数据
INSERT INTO customer (id, name, phone, type, vip_level, vip_end_date) 
VALUES (1, '测试VIP1', '13800138001', 2, 1, '2025-12-31');

INSERT INTO marketing_vip_rights (vip_level, benefit_type, template_id, template_count, status) 
VALUES (1, 1, 1, 2, 1); -- VIP1送2张券

-- 执行测试
@Test
public void testGrantMonthlyBenefit_Success() {
    String currentMonth = "2025-01";
    vipLifecycleService.grantMonthlyBenefit(1L, currentMonth);
    
    // 验证：检查 vip_monthly_record 表
    // 验证：检查 customer_coupon 表（应该有2张券）
    // 验证：检查 customer_benefit_log 表
}
```

#### 测试用例2：幂等性测试
```java
@Test
public void testGrantMonthlyBenefit_Idempotent() {
    String currentMonth = "2025-01";
    // 第一次执行
    vipLifecycleService.grantMonthlyBenefit(1L, currentMonth);
    // 第二次执行（应该跳过）
    vipLifecycleService.grantMonthlyBenefit(1L, currentMonth);
    
    // 验证：vip_monthly_record 表只有一条记录
    // 验证：customer_coupon 表只有2张券（不是4张）
}
```

#### 测试用例3：VIP过期跳过
```java
@Test
public void testGrantMonthlyBenefit_ExpiredVip() {
    // 准备过期VIP客户
    Customer customer = customerRepository.findById(1L).get();
    customer.setVipEndDate(LocalDate.now().minusDays(1));
    customerRepository.save(customer);
    
    String currentMonth = "2025-01";
    vipLifecycleService.grantMonthlyBenefit(1L, currentMonth);
    
    // 验证：vip_monthly_record 表没有记录
    // 验证：customer_coupon 表没有新增券
}
```

#### 测试用例4：优惠券模板停用容错
```java
@Test
public void testGrantMonthlyBenefit_CouponDisabled() {
    // 准备停用的优惠券模板
    CouponTemplate template = couponTemplateRepository.findById(1L).get();
    template.setStatus(0);
    couponTemplateRepository.save(template);
    
    String currentMonth = "2025-01";
    vipLifecycleService.grantMonthlyBenefit(1L, currentMonth);
    
    // 验证：customer_benefit_log 表有失败日志
    // 验证：其他权益（如专用金）正常发放
}
```

### 2. VIP降级测试

#### 测试用例1：正常降级
```java
@Test
public void testDowngradeExpiredVips_Success() {
    // 准备过期VIP客户
    Customer customer = customerRepository.findById(1L).get();
    customer.setType(2); // VIP
    customer.setVipLevel(1);
    customer.setVipEndDate(LocalDate.now().minusDays(1));
    customerRepository.save(customer);
    
    vipLifecycleService.downgradeExpiredVips();
    
    // 验证：客户类型变为1（普通）
    // 验证：vip_level 为 null
    // 验证：vip_end_date 为 null
}
```

#### 测试用例2：未过期VIP不降级
```java
@Test
public void testDowngradeExpiredVips_NotExpired() {
    // 准备未过期VIP客户
    Customer customer = customerRepository.findById(1L).get();
    customer.setType(2); // VIP
    customer.setVipLevel(1);
    customer.setVipEndDate(LocalDate.now().plusDays(30));
    customerRepository.save(customer);
    
    vipLifecycleService.downgradeExpiredVips();
    
    // 验证：客户类型仍为2（VIP）
    // 验证：vip_level 仍为1
}
```

### 3. 积分年度清零测试

#### 测试用例1：正常清零
```java
@Test
public void testClearPointsYearly_Success() {
    // 准备有积分的客户
    CustomerWallet wallet = walletRepository.findByCustomerIdAndWalletType(1L, 3).get();
    wallet.setBalance(new BigDecimal("1000"));
    walletRepository.save(wallet);
    
    pointsClearJob.clearPointsYearly();
    
    // 验证：余额为0
    // 验证：wallet_transaction 表有扣除记录
}
```

#### 测试用例2：余额为0跳过
```java
@Test
public void testClearPointsYearly_ZeroBalance() {
    // 准备余额为0的客户
    CustomerWallet wallet = walletRepository.findByCustomerIdAndWalletType(1L, 3).get();
    wallet.setBalance(BigDecimal.ZERO);
    walletRepository.save(wallet);
    
    int transactionCountBefore = transactionRepository.count();
    pointsClearJob.clearPointsYearly();
    int transactionCountAfter = transactionRepository.count();
    
    // 验证：没有新增流水记录
    Assert.assertEquals(transactionCountBefore, transactionCountAfter);
}
```

#### 测试用例3：幂等性测试（需要添加幂等性检查后）
```java
@Test
public void testClearPointsYearly_Idempotent() {
    // 准备有积分的客户
    CustomerWallet wallet = walletRepository.findByCustomerIdAndWalletType(1L, 3).get();
    wallet.setBalance(new BigDecimal("1000"));
    walletRepository.save(wallet);
    
    // 第一次执行
    pointsClearJob.clearPointsYearly();
    // 第二次执行（应该跳过，因为余额已为0）
    pointsClearJob.clearPointsYearly();
    
    // 验证：只有一条扣除流水记录
}
```

### 4. 定时任务集成测试

#### 测试用例1：VIP月度权益发放定时任务
```java
@SpringBootTest
public class VipMonthlyBenefitJobTest {
    
    @Autowired
    private VipMonthlyBenefitJob job;
    
    @Test
    public void testVipMonthlyBenefitJob() {
        // 手动触发定时任务
        job.executeMonthlyBenefit();
        
        // 验证：所有有效VIP客户都收到了权益
    }
}
```

#### 测试用例2：VIP降级定时任务
```java
@SpringBootTest
public class VipDowngradeJobTest {
    
    @Autowired
    private VipDowngradeJob job;
    
    @Test
    public void testVipDowngradeJob() {
        // 手动触发定时任务
        job.executeDowngrade();
        
        // 验证：所有过期VIP客户都已降级
    }
}
```

## 五、性能测试建议

### 1. VIP月度权益发放性能测试

- **测试场景**：1000个VIP客户，每个客户配置2条权益（送券+送专用金）
- **预期结果**：任务在5分钟内完成
- **监控指标**：数据库连接数、内存使用、执行时间

### 2. 积分清零性能测试

- **测试场景**：10万个客户，其中5万个有积分
- **预期结果**：任务在30分钟内完成（使用批量处理）
- **监控指标**：数据库连接数、内存使用、执行时间

## 六、监控建议

1. **日志监控**
   - 记录任务开始时间、结束时间、处理数量
   - 记录异常和错误

2. **告警设置**
   - 任务执行失败告警
   - 任务执行时间过长告警（超过预期时间2倍）
   - 处理数量异常告警（如VIP权益发放数量为0）

3. **数据监控**
   - 监控 `vip_monthly_record` 表每月新增记录数
   - 监控 `customer_benefit_log` 表失败记录数
   - 监控积分清零后的余额分布









