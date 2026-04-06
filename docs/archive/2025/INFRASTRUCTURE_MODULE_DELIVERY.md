## Infrastructure：系统全局能力补全模块交付说明

### 1. 交付清单

| 子系统 | 文件 / 组件 | 说明 |
| ------ | ----------- | ---- |
| `core-infrastructure` | `common/response/R.java` | 统一响应体，包含 code、message、data、timestamp、traceId |
| `core-infrastructure` | `common/error/IErrorCode.java` | 错误码接口定义 |
| `core-infrastructure` | `common/error/SystemErrorCode.java` | 系统级错误码枚举（10000-19999） |
| `core-infrastructure` | `common/exception/BizException.java` | 业务异常类，携带错误码 |
| `core-infrastructure` | `common/exception/GlobalExceptionHandler.java` | 全局异常处理器，统一异常响应格式 |
| `core-infrastructure` | `common/config/ValidationConfig.java` | 参数校验配置（Fail-Fast 模式） |
| `core-infrastructure` | `common/log/LogMaskingUtils.java` | 敏感字段脱敏工具类 |
| `core-infrastructure` | `common/log/WebLogAspect.java` | 请求日志切面（TraceId 注入、脱敏、慢接口告警） |
| `core-infrastructure` | `common/config/OpenApiConfig.java` | OpenAPI/Swagger 配置 |
| `core-infrastructure` | `common/config/JacksonConfig.java` | Jackson 序列化配置（统一时间格式） |
| `core-infrastructure` | `test/common/ExceptionTest.java` | 异常处理测试用例 |
| `core-infrastructure` | `test/common/LogMaskingTest.java` | 日志脱敏测试用例 |
| `core-infrastructure` | `pom.xml` | 新增依赖：`spring-boot-starter-aop`、`springdoc-openapi-starter-webmvc-ui` |
| `docs` | `INFRASTRUCTURE_MODULE_DELIVERY.md` | 本交付文档 |

### 2. 核心功能覆盖

#### 2.1 统一响应结构 (`R<T>`)
- **字段：** `code`（状态码）、`message`（提示信息）、`data`（业务数据）、`timestamp`（响应时间戳）、`traceId`（链路追踪ID）
- **静态方法：** `success(data)`、`success()`、`fail(code, message)`、`fail(errorCode)`
- **TraceId 来源：** 自动从 MDC 获取，由 `WebLogAspect` 注入

#### 2.2 统一错误码体系
- **接口定义：** `IErrorCode`（`getCode()`、`getMessage()`）
- **系统级错误码（10000-19999）：**
  - `0`: 成功
  - `10001`: 参数错误
  - `10002`: 系统繁忙
  - `10003`: 未登录或登录已失效
  - `10004`: 无权限访问
  - `10005`: 重复请求
  - `10006`: 数据不存在
  - `10007`: 数据冲突或状态异常
- **业务模块规范：** 商品（20000-20999）、库存（21000-21999）、订单（30000-30999）、赊销（40000-40999）

#### 2.3 全局异常处理
- **`BizException`：** 返回对应错误码和消息
- **`NotLoginException`（Sa-Token）：** 返回 10003（未登录）
- **`MethodArgumentNotValidException` / `BindException`：** 拼接所有 FieldError，返回 10001（参数错误）
- **`ConstraintViolationException`：** 拼接所有约束违反信息，返回 10001
- **`HttpMessageNotReadableException`：** 返回 10001（请求参数格式错误）
- **`Exception`（兜底）：** 返回 10002（系统繁忙），记录 ERROR 日志

#### 2.4 智能请求日志切面
- **切点：** 所有 `@RestController` 的方法
- **TraceId 注入：** 生成 UUID 放入 MDC，确保日志链路可追溯
- **敏感字段脱敏：** 自动将以下字段替换为 `******`：
  - `password`、`payPassword`、`oldPassword`、`newPassword`
  - `token`、`Authorization`
  - `idCard`、`mobile`（保留首尾，如 `138****5678`）
- **耗时告警：**
  - 执行时间 > 1000ms → WARN 级别日志
  - 执行时间 > 3000ms → ERROR 级别日志
- **输出内容：** URL、IP、Method、Args（脱敏后）、CostTime、Status

#### 2.5 参数校验配置
- **Fail-Fast 模式：** 遇到第一个校验错误即停止，提升响应速度
- **支持注解：** `@NotNull`、`@NotBlank`、`@Min`、`@Max`、`@Pattern` 等 Jakarta Bean Validation 注解

#### 2.6 OpenAPI 配置
- **集成：** SpringDoc OpenAPI 3（Swagger UI）
- **访问地址：** `/swagger-ui.html` 或 `/swagger-ui/index.html`
- **API 文档：** `/v3/api-docs`
- **配置项：** 标题、版本、联系人、外部文档链接

#### 2.7 Jackson 序列化配置
- **时间格式：** `yyyy-MM-dd HH:mm:ss`（日期时间）、`yyyy-MM-dd`（日期）
- **禁用时间戳：** 统一使用字符串格式，避免前后端时间格式不一致
- **模块：** 自动注册 `JavaTimeModule`

### 3. 使用规范

#### 3.1 Controller 返回值
所有 Controller 方法应返回 `R<T>` 类型：

```java
@PostMapping("/create")
public R<OrderVO> create(@RequestBody @Valid OrderCreateCommand cmd) {
    OrderVO vo = orderService.create(cmd);
    return R.success(vo);
}
```

#### 3.2 业务异常抛出
使用 `BizException` 抛出业务异常：

```java
if (order == null) {
    throw new BizException(SystemErrorCode.DATA_NOT_FOUND);
}
// 或自定义消息
throw new BizException(SystemErrorCode.DATA_CONFLICT, "订单状态不允许此操作");
```

#### 3.3 DTO 参数校验
使用 Jakarta Bean Validation 注解：

```java
public class OrderCreateCommand {
    @NotBlank(message = "订单号不能为空")
    private String orderNo;
    
    @NotNull(message = "客户ID不能为空")
    @Min(value = 1, message = "客户ID必须大于0")
    private Long customerId;
    
    @DecimalMin(value = "0.01", message = "金额必须大于0")
    private BigDecimal amount;
}
```

#### 3.4 API 文档注解
所有 DTO 字段和方法必须添加 OpenAPI 注解：

```java
@Schema(description = "订单创建命令")
public class OrderCreateCommand {
    @Schema(description = "订单号", example = "ORD20240101001")
    private String orderNo;
}

@Operation(summary = "创建订单", description = "创建新订单")
@PostMapping("/create")
public R<OrderVO> create(@RequestBody @Valid OrderCreateCommand cmd) {
    // ...
}
```

### 4. 响应格式示例

#### 4.1 成功响应
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "id": 1,
    "orderNo": "ORD20240101001"
  },
  "timestamp": 1704067200000,
  "traceId": "abc123def456"
}
```

#### 4.2 业务异常响应
```json
{
  "code": 10006,
  "message": "数据不存在",
  "data": null,
  "timestamp": 1704067200000,
  "traceId": "abc123def456"
}
```

#### 4.3 参数校验失败响应
```json
{
  "code": 10001,
  "message": "订单号不能为空; 客户ID必须大于0",
  "data": null,
  "timestamp": 1704067200000,
  "traceId": "abc123def456"
}
```

### 5. 日志输出示例

#### 5.1 正常请求日志
```
INFO  [traceId=abc123def456] API Success: [POST /api/admin/orders] traceId=abc123def456 ip=192.168.1.100 args={orderNo=ORD001, customerId=1} cost=45ms
```

#### 5.2 敏感字段脱敏日志
```
INFO  [traceId=abc123def456] API Success: [POST /api/auth/login] traceId=abc123def456 ip=192.168.1.100 args={username=admin, password=******} cost=120ms
```

#### 5.3 慢接口告警日志
```
WARN  [traceId=abc123def456] Slow API - API Success: [POST /api/admin/reports] traceId=abc123def456 ip=192.168.1.100 args={} cost=1523ms
ERROR [traceId=abc123def456] Slow API - API Success: [GET /api/admin/statistics] traceId=abc123def456 ip=192.168.1.100 args={} cost=3500ms
```

### 6. 测试

#### 6.1 异常处理测试 (`ExceptionTest`)
- 测试 `BizException` 返回格式
- 测试参数校验异常返回格式
- 测试未登录异常返回格式

#### 6.2 日志脱敏测试 (`LogMaskingTest`)
- 测试密码字段脱敏（`password` → `******`）
- 测试 Token 字段脱敏（`token` → `******`）
- 测试手机号脱敏（`13812345678` → `138****5678`）

执行命令：
```bash
mvn -pl core-infrastructure test "-Dtest=ExceptionTest,LogMaskingTest"
```

### 7. 依赖说明

#### 7.1 新增依赖
- `spring-boot-starter-aop`：AOP 支持（用于日志切面）
- `springdoc-openapi-starter-webmvc-ui`：OpenAPI/Swagger UI 支持

#### 7.2 现有依赖（已使用）
- `spring-boot-starter-validation`：参数校验支持
- `spring-boot-starter-web`：Web 相关功能
- `slf4j-api`：日志接口
- `lombok`：简化代码

### 8. 配置说明

#### 8.1 日志配置（建议在 `application.yml` 中配置）
```yaml
logging:
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] [%X{traceId}] %-5level %logger{36} - %msg%n"
  level:
    com.agri_system: INFO
```

#### 8.2 OpenAPI 配置（可选）
```yaml
springdoc:
  api-docs:
    path: /v3/api-docs
  swagger-ui:
    path: /swagger-ui.html
    enabled: true
```

### 9. 迁移指南

#### 9.1 现有 Controller 迁移
1. 将所有返回值改为 `R<T>` 类型
2. 使用 `R.success(data)` 返回成功结果
3. 使用 `throw new BizException(...)` 抛出业务异常
4. 在 DTO 上添加 `@Schema` 注解
5. 在 Controller 方法上添加 `@Operation` 注解

#### 9.2 现有异常处理迁移
1. 将自定义异常改为继承 `BizException` 或直接使用 `BizException`
2. 移除原有的 `@ExceptionHandler`（统一由 `GlobalExceptionHandler` 处理）
3. 确保错误码符合规范（系统级 10000-19999，业务模块按约定范围）

### 10. 注意事项

1. **TraceId 依赖 MDC：** 确保日志配置中包含 `%X{traceId}` 才能正确输出
2. **脱敏字段扩展：** 如需新增脱敏字段，修改 `LogMaskingUtils.mask()` 方法
3. **慢接口阈值：** 可在 `WebLogAspect` 中调整 `WARN_THRESHOLD` 和 `ERROR_THRESHOLD`
4. **错误码规范：** 新增业务模块错误码时，请遵循约定的范围，避免冲突
5. **Fail-Fast 影响：** 参数校验失败时只返回第一个错误，如需返回所有错误，需调整 `ValidationConfig`

### 11. 后续优化建议

1. **分布式追踪：** 可集成 SkyWalking、Zipkin 等分布式追踪系统，替换简单的 UUID TraceId
2. **日志持久化：** 建议集成 ELK 或 Loki 进行日志收集和分析
3. **监控告警：** 可基于慢接口日志集成 Prometheus + Grafana 进行监控告警
4. **错误码管理：** 可考虑将错误码配置化，支持动态更新和多语言支持
5. **API 版本管理：** 可扩展 OpenAPI 配置支持多版本 API 文档

---

**交付日期：** 2025-01-XX  
**版本：** 1.0.0  
**状态：** ✅ 已完成





