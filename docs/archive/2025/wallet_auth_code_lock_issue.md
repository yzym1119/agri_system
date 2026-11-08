> 文档状态：归档历史资料，不是现行开发/部署/建库依据。现行入口见[统一文档中心](../../current/README.md)。

# 钱包验证码锁等待超时问题分析

## 问题描述

在收银结算中使用钱包验证码进行余额支付时，出现 `Lock wait timeout exceeded; try restarting transaction` 错误，导致订单创建失败。

## 错误日志

```
2025-12-28 21:01:47.096 [http-nio-8080-exec-3] INFO c.a.a.o.s.OrderApplicationService - Queried customer balance for customerId=3 balance=1037555.00
2025-12-28 21:01:47.097 [http-nio-8080-exec-3] INFO c.a.a.w.s.WalletApplicationService - Validating wallet auth code for customerId=3 authCode=722055
2025-12-28 21:01:47.112 [http-nio-8080-exec-3] INFO c.a.a.w.s.WalletApplicationService - wallet auth code lookup returned row for customerId=3 authCode=722055 elapsed=13ms row={id=20, amount=0.00}
2025-12-28 21:01:47.112 [http-nio-8080-exec-3] INFO c.a.a.w.s.WalletApplicationService - About to update wallet_auth_code id=20 customerId=3 orderId=104
2025-12-28 21:02:37.610 [http-nio-8080-exec-3] ERROR c.a.a.w.s.WalletApplicationService - Exception while updating wallet_auth_code id=20 customerId=3 orderId=104
org.springframework.dao.CannotAcquireLockException: PreparedStatementCallback; SQL [UPDATE wallet_auth_code SET status = 2, used_at = ?, order_id = ? WHERE id = ? AND status = 1]; Lock wait timeout exceeded; try restarting transaction
```

## 核心代码

### WalletApplicationService.validateAndConsumeWalletAuthCode()

```java
@Transactional(rollbackFor = Exception.class)
public boolean validateAndConsumeWalletAuthCode(Long customerId, String authCode, Long orderId, java.math.BigDecimal amount) {
    // ... 参数验证 ...
    java.time.LocalDateTime now = java.time.LocalDateTime.now();
    String selectSql = "SELECT id, amount FROM wallet_auth_code WHERE customer_id = ? AND auth_code = ? AND status = 1 AND expire_at >= ? LIMIT 1";

    try {
        // 查询验证码
        java.util.Map<String, Object> row = jdbcTemplate.queryForMap(selectSql, customerId, authCode, java.sql.Timestamp.valueOf(now));
        Long id = ((Number)row.get("id")).longValue();

        // 更新验证码状态
        String updateSql = "UPDATE wallet_auth_code SET status = 2, used_at = ?, order_id = ? WHERE id = ? AND status = 1";
        int updated = jdbcTemplate.update(updateSql, java.sql.Timestamp.valueOf(now), orderId, id);

        return updated > 0;
    } catch (Exception e) {
        // 错误处理
    }
}
```

### OrderApplicationService.createPOSOrder()

```java
@Transactional(rollbackFor = Exception.class)
public Long createPOSOrder(POSOrderCreateCommand command) {
    // ... 订单创建逻辑 ...

    // 保存订单
    order = orderRepository.save(order);

    // 处理钱包验证码验证和消费
    if (balanceAmount.compareTo(BigDecimal.ZERO) > 0) {
        boolean authOk = walletService.validateAndConsumeWalletAuthCode(order.getCustomerId(), command.getWalletAuthCode(), order.getId(), balanceAmount);
        if (!authOk) {
            throw new IllegalArgumentException(OrderErrorCode.WALLET_AUTH_CODE_INVALID + ": 钱包验证码校验失败或已过期");
        }
        // 扣减余额
        walletService.deductBalance(order.getCustomerId(), balanceAmount, "ORDER", order.getId(), createdBy, "POS订单支付");
    }

    // ... 其他逻辑 ...
}
```

## 数据库表结构

```sql
CREATE TABLE wallet_auth_code (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    customer_id BIGINT NOT NULL,
    auth_code VARCHAR(6) NOT NULL,
    amount DECIMAL(10,2) DEFAULT 0.00,
    status TINYINT DEFAULT 1 COMMENT '1=未使用, 2=已使用',
    expire_at DATETIME NOT NULL,
    used_at DATETIME NULL,
    order_id BIGINT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_customer_auth (customer_id, auth_code, status),
    INDEX idx_expire (expire_at)
);
```

## 问题分析

### 1. 锁等待超时原因

- **事务隔离级别**：MySQL默认使用REPEATABLE READ，可能导致长时间的行锁
- **并发访问**：多个请求同时尝试消费同一个验证码
- **锁升级**：从行锁升级为表锁
- **死锁检测**：MySQL的死锁检测可能导致其中一个事务被回滚

### 2. 当前解决方案尝试

1. **移除SELECT FOR UPDATE**：已尝试，但UPDATE语句仍然遇到锁等待
2. **添加诊断日志**：已添加，但问题依然存在

### 3. 可能的根本原因

1. **数据库配置问题**：
   - `innodb_lock_wait_timeout` 设置过短（默认50秒）
   - `innodb_rollback_on_timeout` 设置

2. **应用层问题**：
   - 同一个验证码被多个线程同时消费
   - 事务嵌套导致锁竞争

3. **架构问题**：
   - 验证码消费逻辑在订单事务中，增加了锁的范围

## 可能的解决方案

### 方案1：调整数据库配置

```sql
-- 增加锁等待超时时间
SET GLOBAL innodb_lock_wait_timeout = 120;

-- 或者在应用层面设置
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/agri_system?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
    hikari:
      connection-init-sql: SET innodb_lock_wait_timeout=120
```

### 方案2：使用乐观锁

```java
// 在wallet_auth_code表中添加version字段
ALTER TABLE wallet_auth_code ADD COLUMN version INT DEFAULT 0;

// 修改更新逻辑
String updateSql = "UPDATE wallet_auth_code SET status = 2, used_at = ?, order_id = ?, version = version + 1 WHERE id = ? AND status = 1 AND version = ?";
```

### 方案3：分离验证码消费事务

```java
// 将验证码消费分离到独立的事务中
public boolean validateAndConsumeWalletAuthCode(Long customerId, String authCode, Long orderId, BigDecimal amount) {
    // 先在独立事务中消费验证码
    boolean consumed = walletService.consumeAuthCodeIndependently(customerId, authCode, orderId, amount);

    if (consumed) {
        // 如果消费成功，在订单事务中关联
        walletService.associateAuthCodeWithOrder(authCode, orderId);
        return true;
    }
    return false;
}

@Transactional(propagation = Propagation.REQUIRES_NEW)
public boolean consumeAuthCodeIndependently(Long customerId, String authCode, Long orderId, BigDecimal amount) {
    // 消费逻辑
}
```

### 方案4：使用Redis缓存验证码状态

```java
// 使用Redis SET NX 来实现分布式锁
String lockKey = "wallet_auth_code:" + authCode;
boolean locked = redisTemplate.opsForValue().setIfAbsent(lockKey, "locked", Duration.ofSeconds(30));

if (!locked) {
    throw new IllegalArgumentException("验证码正在被验证，请稍后重试");
}

try {
    // 消费逻辑
} finally {
    redisTemplate.delete(lockKey);
}
```

### 方案5：简化消费逻辑

```java
// 使用更简单的UPDATE语句，避免复杂的WHERE条件
@Transactional(propagation = Propagation.REQUIRES_NEW)
public boolean validateAndConsumeWalletAuthCode(Long customerId, String authCode, Long orderId, BigDecimal amount) {
    String sql = "UPDATE wallet_auth_code SET status = 2, used_at = NOW(), order_id = ? WHERE customer_id = ? AND auth_code = ? AND status = 1 AND expire_at >= NOW()";
    int updated = jdbcTemplate.update(sql, orderId, customerId, authCode);
    return updated > 0;
}
```

## 测试建议

1. **单线程测试**：确保单个请求能正常工作
2. **并发测试**：使用JMeter等工具模拟并发消费同一个验证码
3. **数据库监控**：观察InnoDB锁状态和等待情况

## 紧急修复建议

如果需要快速修复，建议采用方案5：简化消费逻辑并使用独立事务。

## 相关文件

- `core-application/src/main/java/com/agri_system/application/wallet/service/WalletApplicationService.java`
- `core-application/src/main/java/com/agri_system/application/order/service/OrderApplicationService.java`
- `api-client/src/main/java/com/agri_system/api/client/controller/WalletClientController.java`
- `admin-web/src/views/pos/components/CheckoutDialog.vue`







