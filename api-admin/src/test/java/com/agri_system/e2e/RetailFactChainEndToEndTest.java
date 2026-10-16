package com.agri_system.e2e;

import cn.dev33.satoken.stp.SaTokenInfo;
import cn.dev33.satoken.stp.StpInterface;
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.interceptor.SaInterceptor;
import com.agri_system.api.admin.controller.InventoryController;
import com.agri_system.api.admin.controller.InventoryWarningController;
import com.agri_system.api.admin.controller.OrderAdminController;
import com.agri_system.api.admin.controller.ProductController;
import com.agri_system.api.admin.controller.ProductPackageController;
import com.agri_system.api.admin.controller.AfterSalesAdminController;
import com.agri_system.api.admin.controller.CreditAdminController;
import com.agri_system.api.admin.controller.ReportDashboardController;
import com.agri_system.application.after_sales.dto.command.AdminReturnCommand;
import com.agri_system.application.performance.listener.PerformanceEventListener;
import com.agri_system.application.performance.event.OrderRefundedEvent;
import com.agri_system.application.config.DomainServiceConfig;
import com.agri_system.application.inventory.dto.command.InventoryOutCommand;
import com.agri_system.application.inventory.dto.vo.InventoryRiskVO;
import com.agri_system.application.inventory.service.InventoryApplicationService;
import com.agri_system.application.inventory.service.InventoryRiskService;
import com.agri_system.application.order.dto.command.OrderItemDTO;
import com.agri_system.application.order.dto.command.POSOrderCreateCommand;
import com.agri_system.application.order.dto.command.PickupFulfillmentCommand;
import com.agri_system.application.order.service.OrderApplicationService;
import com.agri_system.application.order.service.PickupFulfillmentService;
import com.agri_system.application.purchase.dto.command.ReceiptBatchCommand;
import com.agri_system.application.purchase.dto.command.ReceivePurchaseCommand;
import com.agri_system.application.purchase.service.PurchaseApplicationService;
import com.agri_system.domain.inventory.model.InventoryBalanceFeatureStatus;
import com.agri_system.domain.inventory.model.InventoryMovement;
import com.agri_system.domain.inventory.repository.InventoryBalanceFeatureStatusRepository;
import com.agri_system.domain.inventory.service.BatchBalanceService;
import com.agri_system.infrastructure.auth.config.SaTokenConfigure;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 统一库存真实 MySQL 验收：全量 Flyway 后，通过业务入口收货、销售、原单退货、调拨和盘点。
 * 不依赖商品开关；库存快照使用主单位，余额与流水使用基准单位。
 * 每次创建唯一临时数据库，绝不预先删除已有库，仅清理本次成功创建的库。
 */
@EnabledIfSystemProperty(named = "mysql.it", matches = "true")
@SpringBootTest(classes = RetailFactChainEndToEndTest.E2EConfig.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class RetailFactChainEndToEndTest {

    private static final String DB_NAME = "agri_unified_inventory_it_"
            + java.util.UUID.randomUUID().toString().replace("-", "");

    private static boolean databaseCreated = false;
    private static String serverUrl;
    private static String jdbcUrl;
    private static String dbUser;
    private static String dbPassword;

    @Autowired
    private DataSource dataSource;
    @Autowired
    private OrderApplicationService orderApplicationService;
    @Autowired
    private PurchaseApplicationService purchaseApplicationService;
    @Autowired
    private InventoryApplicationService inventoryApplicationService;
    @Autowired
    private InventoryRiskService inventoryRiskService;
    @Autowired
    private PickupFulfillmentService pickupFulfillmentService;
    @Autowired
    private BatchBalanceService batchBalanceService;
    @Autowired
    private InventoryBalanceFeatureStatusRepository featureStatusRepository;
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired private PerformanceEventListener performanceEventListener;
    @Autowired private org.apache.ibatis.session.SqlSessionFactory sqlSessionFactory;
    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
    @Autowired private org.springframework.transaction.PlatformTransactionManager transactionManager;
    @Autowired private com.agri_system.application.order.service.OrderPaymentService orderPaymentService;
    @Autowired private com.agri_system.domain.order.repository.OrderRepository orderRepository;
    @Autowired private com.agri_system.domain.order.repository.OrderPaymentRepository paymentRepository;
    @Autowired private com.agri_system.domain.credit.repository.CreditRepaymentLogRepository repaymentRepository;
    @Autowired private com.agri_system.domain.after_sales.repository.AfterSalesRepository afterSalesRepository;
    @Autowired private com.agri_system.application.after_sales.service.AfterSalesApplicationService afterSalesApplicationService;

    // ------------------------------------------------------------------
    // 数据源：建一次性库 + Flyway 编程式迁移 + 自证（行数==迁移文件数、failed==0）
    // ------------------------------------------------------------------
    @DynamicPropertySource
    static void e2eDatasource(DynamicPropertyRegistry registry) {
        String host = System.getProperty("mysql.it.host", "127.0.0.1");
        String port = System.getProperty("mysql.it.port", "3306");
        dbUser = System.getProperty("mysql.it.user", "root");
        dbPassword = System.getProperty("mysql.it.password", "");
        serverUrl = "jdbc:mysql://" + host + ":" + port
                + "/?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&characterEncoding=utf8";
        jdbcUrl = "jdbc:mysql://" + host + ":" + port + "/" + DB_NAME
                + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&characterEncoding=utf8";

        try (Connection conn = DriverManager.getConnection(serverUrl, dbUser, dbPassword);
             Statement st = conn.createStatement()) {
            st.execute("CREATE DATABASE " + DB_NAME + " CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
            databaseCreated = true;
            log("[db] 已创建唯一一次性测试库 " + DB_NAME + "（未触碰 agri_system / tlcloud_* / agri_retail_it）");
        } catch (SQLException e) {
            throw new IllegalStateException("E2E 验收需要可达的真实 MySQL（-Dmysql.it=true，"
                    + "mysql.it.host/port/user/password 可覆盖；当前 " + serverUrl + "）。"
                    + "本测试禁止静默跳过：MySQL 不可达即失败", e);
        }

        try {
            MigrateResult migrateResult = Flyway.configure()
                    .dataSource(jdbcUrl, dbUser, dbPassword)
                    .locations("classpath:db/migration")
                    .load()
                    .migrate();
            log("[flyway] migrationsExecuted=" + migrateResult.migrationsExecuted);
            selfCheckFlywayHistory();
        } catch (RuntimeException | AssertionError e) {
            // 上下文初始化失败时 JUnit 不一定执行 AfterAll，仍只清理本次已创建库。
            dropOnetimeDatabase();
            throw e;
        }

        registry.add("spring.datasource.url", () -> jdbcUrl);
        registry.add("spring.datasource.username", () -> dbUser);
        registry.add("spring.datasource.password", () -> dbPassword);
        registry.add("spring.flyway.enabled", () -> "false");
    }

    private static void selfCheckFlywayHistory() {
        StringBuilder sb = new StringBuilder();
        int rows = 0;
        int failed = 0;
        try (Connection conn = DriverManager.getConnection(jdbcUrl, dbUser, dbPassword);
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT installed_rank, version, description, type, script, success "
                     + "FROM flyway_schema_history ORDER BY installed_rank")) {
            while (rs.next()) {
                rows++;
                boolean success = rs.getBoolean("success");
                if (!success) {
                    failed++;
                }
                sb.append("[flyway-history] installed_rank=").append(rs.getInt("installed_rank"))
                        .append(" version=").append(rs.getString("version"))
                        .append(" description=").append(rs.getString("description"))
                        .append(" type=").append(rs.getString("type"))
                        .append(" script=").append(rs.getString("script"))
                        .append(" success=").append(success)
                        .append('\n');
            }
        } catch (SQLException e) {
            throw new IllegalStateException("读取 flyway_schema_history 失败（自证无法完成即失败）", e);
        }

        int classpathMigrations;
        try {
            Resource[] resources = new PathMatchingResourcePatternResolver()
                    .getResources("classpath*:db/migration/*.sql");
            Map<String, String> distinct = new LinkedHashMap<>();
            for (Resource r : resources) {
                distinct.put(r.getFilename(), r.getDescription());
            }
            Resource[] javaMigrations = new PathMatchingResourcePatternResolver()
                    .getResources("classpath*:db/migration/V*__*.class");
            for (Resource r : javaMigrations) {
                if (!r.getFilename().contains("$")) distinct.put(r.getFilename(), r.getDescription());
            }
            classpathMigrations = distinct.size();
            sb.append("[flyway-selfcheck] classpath SQL/Java 迁移(按文件名去重)=").append(classpathMigrations).append('\n');
        } catch (Exception e) {
            throw new IllegalStateException("枚举 classpath:db/migration 失败（自证无法完成即失败）", e);
        }

        String databaseName;
        try (Connection conn = DriverManager.getConnection(jdbcUrl, dbUser, dbPassword);
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT DATABASE()")) {
            rs.next();
            databaseName = rs.getString(1);
        } catch (SQLException e) {
            throw new IllegalStateException("校验当前库名失败", e);
        }

        sb.append("[flyway-selfcheck] 迁移记录行数=").append(rows)
                .append(" classpath 迁移文件数=").append(classpathMigrations)
                .append(" failed=").append(failed)
                .append(" SELECT DATABASE()=").append(databaseName).append('\n');
        System.out.print(sb);
        try {
            Files.writeString(Paths.get("target", "e2e-flyway-selfcheck.txt"), sb.toString(),
                    StandardCharsets.UTF_8);
        } catch (Exception e) {
            log("[warn] 写 target/e2e-flyway-selfcheck.txt 失败: " + e.getMessage());
        }

        if (rows != classpathMigrations) {
            fail("Flyway 自证失败：history 行数=" + rows + " 与 classpath 迁移文件数="
                    + classpathMigrations + " 不一致");
        }
        if (failed != 0) {
            throw new IllegalStateException("Flyway 自证失败：存在 " + failed + " 条 success=0 的迁移记录");
        }
        if (!DB_NAME.equals(databaseName)) {
            throw new IllegalStateException("Flyway 自证失败：当前 schema 应为 " + DB_NAME + "，实际 " + databaseName);
        }
    }

    @AfterAll
    static void dropOnetimeDatabase() {
        if (!databaseCreated) {
            log("[cleanup] 数据库未建立，无需 drop");
            return;
        }
        try (Connection conn = DriverManager.getConnection(serverUrl, dbUser, dbPassword);
             Statement st = conn.createStatement()) {
            st.execute("DROP DATABASE IF EXISTS " + DB_NAME);
            log("[cleanup] 已 DROP 一次性测试库 " + DB_NAME);
        } catch (SQLException e) {
            log("[cleanup][WARN] DROP " + DB_NAME + " 失败: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // 十步弧线（一个有序 @Test，顺序即 brief §4，断言内联）
    // ------------------------------------------------------------------
    @Test
    void declaredTransactionIsolationAppliesToSharedConnection() {
        var committed = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        committed.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_READ_COMMITTED);
        committed.execute(status -> {
            org.apache.ibatis.session.SqlSession session = org.mybatis.spring.SqlSessionUtils.getSqlSession(sqlSessionFactory);
            try {
                assertEquals(Connection.TRANSACTION_READ_COMMITTED, session.getConnection().getTransactionIsolation(),
                        "事务注解指定的隔离级别必须作用于实际Flex连接");
                assertEquals("READ-COMMITTED", jdbcTemplate.queryForObject("SELECT @@transaction_isolation", String.class));
                try (Statement st = session.getConnection().createStatement(); ResultSet rs = st.executeQuery("SELECT CONNECTION_ID()")) {
                    rs.next();
                    assertEquals(rs.getLong(1), jdbcTemplate.queryForObject("SELECT CONNECTION_ID()", Long.class));
                }
                status.setRollbackOnly();
                return null;
            } catch (SQLException e) { throw new IllegalStateException(e); }
            finally { org.mybatis.spring.SqlSessionUtils.closeSqlSession(session, sqlSessionFactory); }
        });
        new org.springframework.transaction.support.TransactionTemplate(transactionManager).execute(status -> {
            assertEquals("REPEATABLE-READ", jdbcTemplate.queryForObject("SELECT @@transaction_isolation", String.class),
                    "事务结束后连接池必须恢复原默认隔离级别，不污染下一笔操作");
            return null;
        });
    }

    @Test
    void unifiedInventoryBusinessChain() throws Exception {
        assertTrue(sqlSessionFactory.getConfiguration().getEnvironment().getDataSource() == jdbcTemplate.getDataSource(),
                "JdbcTemplate 必须使用 MyBatis 同一个事务数据源");
        new org.springframework.transaction.support.TransactionTemplate(transactionManager).execute(status -> {
            org.apache.ibatis.session.SqlSession session = org.mybatis.spring.SqlSessionUtils.getSqlSession(sqlSessionFactory);
            try (Statement st = session.getConnection().createStatement(); ResultSet rs = st.executeQuery("SELECT CONNECTION_ID()")) {
                rs.next();
                assertEquals(rs.getLong(1), jdbcTemplate.queryForObject("SELECT CONNECTION_ID()", Long.class),
                        "MyBatis 与 JDBC 必须在同一事务使用同一物理连接");
            } catch (SQLException e) { throw new IllegalStateException(e); }
            finally { org.mybatis.spring.SqlSessionUtils.closeSqlSession(session, sqlSessionFactory); }
            return null;
        });
        seedFixture();
        assertEquals(0L, jdbcLong("SELECT COUNT(*) FROM inventory_balance_feature_status"),
                "所有业务入口必须在没有开关记录时直接使用同一账本");
        ReceivePurchaseCommand receipt = receiveCmd(1L, 1L, "SUP-BATCH-1", "箱", "1", "100.00",
                LocalDate.now().minusDays(10), LocalDate.now().plusYears(1));
        receipt.setRequestKey("E2E-RECEIPT-HALF-1");
        Long firstReceipt = purchaseApplicationService.receive(receipt, 1L);
        long batch = jdbcLong("SELECT id FROM inventory_receipt_batch WHERE product_id=101 ORDER BY id LIMIT 1");
        assertStock(1, "1", "20");
        assertEquals(1L, jdbcLong("SELECT COUNT(*) FROM inventory_movement WHERE movement_type='RECEIPT'"));
        assertDecimal("箱成本转换成每瓶成本", "5", jdbcDecimal("SELECT base_unit_cost FROM inventory_receipt_batch WHERE id=" + batch));
        long movements = movementCount();
        assertThrows(RuntimeException.class, () -> purchaseApplicationService.receive(receipt, 1L));
        assertEquals(movements, movementCount(), "重复半收货不能多记账");
        assertStock(1, "1", "20");
        ReceivePurchaseCommand secondReceipt = receiveCmd(1L, 1L, "SUP-BATCH-2", "箱", "1", "100.00",
                LocalDate.now().minusDays(5), LocalDate.now().plusYears(2));
        secondReceipt.setRequestKey("E2E-RECEIPT-HALF-2");
        purchaseApplicationService.receive(secondReceipt, 1L);
        assertStock(1, "2", "40");

        // 销售在下单时真实扣账，包括多单位和库房提货订单。
        long order = orderApplicationService.createPOSOrder(posSale(101L, "1", "箱", "100.00", true, null, "100.00"), 1L);
        long item = jdbcLong("SELECT id FROM order_item WHERE order_id=" + order);
        assertStock(1, "1", "20");
        assertDecimal("原单记录实际销售基准量", "20", jdbcDecimal("SELECT allocated_base_quantity FROM order_item_batch_allocation WHERE order_item_id=" + item));
        assertEquals(batch, jdbcLong("SELECT batch_id FROM order_item_batch_allocation WHERE order_item_id=" + item));
        long sourceMovement = jdbcLong("SELECT source_movement_id FROM order_item_batch_allocation WHERE order_item_id=" + item);
        assertEquals("SALE", jdbcString("SELECT movement_type FROM inventory_movement WHERE id=" + sourceMovement));
        assertEquals(0L, jdbcLong("SELECT COUNT(*) FROM inventory_movement WHERE id=" + sourceMovement + " AND to_location_id IS NOT NULL"));
        movements = movementCount();
        InventoryOutCommand duplicateSale = outCmd(101L, "1", "箱");
        duplicateSale.setReferenceType("ORDER"); duplicateSale.setReferenceId(order); duplicateSale.setReferenceLineId(item);
        assertThrows(RuntimeException.class, () -> inventoryApplicationService.outbound(duplicateSale, 1L));
        assertEquals(movements, movementCount());

        // 取货仅核对已出库的批次，不再次移动或扣库存。
        long pickup = jdbcLong("SELECT id FROM order_pickup_request WHERE order_item_id=" + item);
        PickupFulfillmentCommand scan = new PickupFulfillmentCommand();
        scan.setScannedCode(jdbcString("SELECT internal_receipt_batch_no FROM inventory_receipt_batch WHERE id=" + batch));
        pickupFulfillmentService.fulfill(pickup, scan, 1L);
        assertEquals("FULFILLED", jdbcString("SELECT status FROM order_pickup_request WHERE id=" + pickup));
        assertEquals("SCANNED", jdbcString("SELECT evidence_level FROM order_item_batch_allocation WHERE order_item_id=" + item));
        assertEquals(movements, movementCount(), "提货核对不能再次扣账");
        pickupFulfillmentService.fulfill(pickup, scan, 1L);
        assertEquals(movements, movementCount());
        assertStock(1, "1", "20");

        // 半箱退回原批次，日期与成本谱系不变；重复、超量退货全部回滚。
        var returned = new com.agri_system.application.inventory.dto.command.InventoryInCommand();
        returned.setStoreId(1L); returned.setProductId(101L); returned.setUnit("箱");
        returned.setQuantity(new BigDecimal("0.5")); returned.setSourceType("SALES_RETURN");
        returned.setSourceId(9001L); returned.setSourceLineNo(Math.toIntExact(item));
        assertEquals(batch, inventoryApplicationService.inbound(returned, 1L));
        assertStock(1, "1.5", "30");
        assertEquals(2L, jdbcLong("SELECT COUNT(*) FROM inventory_receipt_batch WHERE product_id=101"), "退货沿用原批次");
        assertDecimal("退货沿用原成本", "5", jdbcDecimal("SELECT unit_cost FROM inventory_movement WHERE movement_type='RETURN'"));
        movements = movementCount();
        assertThrows(RuntimeException.class, () -> inventoryApplicationService.inbound(returned, 1L));
        returned.setSourceId(9002L); returned.setQuantity(new BigDecimal("1"));
        assertThrows(RuntimeException.class, () -> inventoryApplicationService.inbound(returned, 1L));
        assertEquals(movements, movementCount(), "超量退货在部分回库后必须整体回滚");
        assertStock(1, "1.5", "30");

        orderApplicationService.createPOSOrder(posSale(101L, "2", "瓶", "5.00", false, null, "10.00"), 1L);
        assertStock(1, "1.4", "28");
        long orders = jdbcLong("SELECT COUNT(*) FROM `order`");
        long allocations = jdbcLong("SELECT COUNT(*) FROM order_item_batch_allocation");
        movements = movementCount();
        assertThrows(RuntimeException.class, () -> orderApplicationService.createPOSOrder(
                posSale(101L, "2", "箱", "100.00", false, null, "200.00"), 1L));
        assertEquals(orders, jdbcLong("SELECT COUNT(*) FROM `order`"), "缺货必须回滚整张订单");
        assertEquals(allocations, jdbcLong("SELECT COUNT(*) FROM order_item_batch_allocation"));
        assertEquals(movements, movementCount());
        assertStock(1, "1.4", "28");

        var transfer = new com.agri_system.application.inventory.dto.command.InventoryTransferCommand();
        transfer.setFromStoreId(1L); transfer.setToStoreId(2L); transfer.setProductId(101L);
        transfer.setRequestKey("E2E-TRANSFER-1");
        transfer.setQuantity(new BigDecimal("0.5")); transfer.setRemark("端到端调拨");
        inventoryApplicationService.transfer(transfer, 1L);
        assertStock(1, "0.9", "18"); assertStock(2, "0.5", "10");
        movements = movementCount(); assertThrows(RuntimeException.class, () -> inventoryApplicationService.transfer(transfer, 1L));
        assertEquals(movements, movementCount(), "重复调拨请求不能重复扣账");
        assertStock(1, "0.9", "18"); assertStock(2, "0.5", "10");
        assertEquals(4L, jdbcLong("SELECT COUNT(*) FROM inventory_movement WHERE movement_type='TRANSFER'"));
        assertEquals(2L, jdbcLong("SELECT COUNT(DISTINCT transfer_id) FROM inventory_movement WHERE movement_type='TRANSFER'"));
        assertEquals(2L, jdbcLong("SELECT COUNT(*) FROM inventory_receipt_batch a JOIN inventory_receipt_batch b "
                + "ON a.store_id=1 AND b.store_id=2 AND a.supplier_batch_no=b.supplier_batch_no "
                + "AND a.expiry_date=b.expiry_date AND a.production_date=b.production_date AND a.base_unit_cost=b.base_unit_cost"));
        long transfers = jdbcLong("SELECT COUNT(*) FROM inventory_transfer");
        movements = movementCount(); transfer.setRequestKey("E2E-TRANSFER-SHORTAGE"); transfer.setQuantity(new BigDecimal("2"));
        assertThrows(RuntimeException.class, () -> inventoryApplicationService.transfer(transfer, 1L));
        assertEquals(transfers, jdbcLong("SELECT COUNT(*) FROM inventory_transfer"));
        assertEquals(movements, movementCount());

        var count = new com.agri_system.application.inventory.dto.command.InventoryAdjustCommand();
        count.setStoreId(2L); count.setProductId(101L); count.setRemark("端到端盘点差异");
        count.setRequestKey("E2E-COUNT-LOSS"); count.setExpectedQuantity(new BigDecimal("0.5"));
        count.setQuantityDiff(new BigDecimal("-0.1")); count.setType(2);
        inventoryApplicationService.adjust(count, 1L); assertStock(2, "0.4", "8");
        movements = movementCount(); assertThrows(RuntimeException.class, () -> inventoryApplicationService.adjust(count, 1L));
        assertEquals(movements, movementCount(), "重复盘点请求不能重复记账");
        assertStock(2, "0.4", "8");
        count.setRequestKey("E2E-COUNT-GAIN"); count.setExpectedQuantity(new BigDecimal("0.4"));
        count.setQuantityDiff(new BigDecimal("0.2")); count.setType(1);
        inventoryApplicationService.adjust(count, 1L); assertStock(2, "0.6", "12");
        movements = movementCount(); count.setRequestKey("E2E-COUNT-SHORTAGE"); count.setExpectedQuantity(new BigDecimal("0.6"));
        count.setQuantityDiff(new BigDecimal("-1")); count.setType(2);
        assertThrows(RuntimeException.class, () -> inventoryApplicationService.adjust(count, 1L));
        assertEquals(movements, movementCount()); assertStock(2, "0.6", "12");
        assertEquals(2L, jdbcLong("SELECT COUNT(*) FROM inventory_movement WHERE movement_type='COUNT_ADJUSTMENT'"));
        assertConcurrentReturnCannotExceedOriginalSale();
        assertConcurrentSalesCannotOversell();
        assertExactAuxiliaryOperations();
        assertMultiBatchPickupCompletesWithoutSecondDeduction();
        assertUnifiedPricingBusinessChain();
        assertPackageBusinessChain();
        assertPosCouponPaymentChain();
        assertCreditSettlementBusinessChain();
        assertManualDeliveryBusinessChain();
        assertOnlineDeliveryPaymentAndFirstSettlement();
        assertPartialReturnPickupBusinessChain();
        assertOnlinePickupRespectsReturnFacts();
        assertConcurrentMultiPackageSale();
        assertLedgerReconciles();
        assertEquals(0L, jdbcLong("SELECT COUNT(*) FROM inventory_balance_feature_status"));
        log("收货、销售、原单退货、提货、调拨、盘点及失败回滚使用同一账本，验收通过");
    }

    /** POS整单人工配送复用现有单据，真实HTTP派单/退货/签收不增加第二份钱货绩效。 */
    private void assertManualDeliveryBusinessChain() throws Exception {
        E2EToken manager=loginToken(1L), secondStore=loginToken(3L);
        for(int id=630;id<=635;id++) exec("INSERT INTO customer(id,code,name,phone,type,status,credit_limit) VALUES("+id+",'DELIVERY-C"+id+"','配送验收客户"+id+"','13800000"+id+"',1,1,1000)");
        exec("INSERT INTO commission_rule(id,legacy_rule_type,rule_category,target_id,calc_mode,calc_value,enabled,status,priority) VALUES(630,1,1,130,'FIXED',1,1,1,1)");
        var invalid=deliverySale(630,"5");
        for(String missing:List.of("name","phone","address")) {
            invalid.setReceiverName(missing.equals("name")?"  ":"配送农户");
            invalid.setReceiverPhone(missing.equals("phone")?"  ":"13800000630");
            invalid.setReceiverAddress(missing.equals("address")?"  ":"测试村3号");
            DeliveryFacts before=deliveryFacts();
            assertPOSRejected(manager,invalid,10001,"收货");
            assertEquals(before,deliveryFacts(),"缺收货信息必须在任何订单/资金/库存/绩效事实前拒绝");
        }
        long initialPending=deliveryDashboard(manager,1).path("pendingDeliveryCount").asLong();
        long secondPending=deliveryDashboard(manager,2).path("pendingDeliveryCount").asLong();
        long order=createPOSViaHttp(manager,deliverySale(630,"5"));
        assertEquals(1L,jdbcLong("SELECT order_type FROM `order` WHERE id="+order));
        assertEquals(4L,jdbcLong("SELECT order_status FROM `order` WHERE id="+order));
        assertEquals(2L,jdbcLong("SELECT delivery_status FROM order_delivery WHERE order_id="+order));
        assertEquals(0L,jdbcLong("SELECT COUNT(*) FROM order_pickup_request p JOIN order_item i ON i.id=p.order_item_id WHERE i.order_id="+order),"人工配送即使勾过库房取货也不产生自提任务");
        assertDecimal("POS待配送即记成交50", "50",jdbcDecimal("SELECT total_order_amount FROM customer WHERE id=630"));
        assertDecimal("POS待配送只计5件提成", "5",commissionSum(order,1));
        assertEquals(initialPending+1,deliveryDashboard(manager,1).path("pendingDeliveryCount").asLong());
        assertEquals(secondPending,deliveryDashboard(manager,2).path("pendingDeliveryCount").asLong(),"第二店待配送不包含第一店POS单");
        JsonNode pending=deliveryRow(manager,order,2);
        assertEquals(1,pending.path("orderType").asInt());assertEquals(2,pending.path("deliveryType").asInt());
        assertTrue(pending.path("canShip").asBoolean(),pending.toString());
        assertTrue(!pending.path("canConfirmReceipt").asBoolean(),pending.toString());
        DeliveryFacts sold=deliveryFacts();
        requireCreditSuccess(postCreditJson(manager,"/api/admin/orders/"+order+"/ship",Map.of("deliveryStaffId",2)));
        Timestamp shipped=jdbcTimestamp("SELECT delivery_at FROM order_delivery WHERE order_id="+order);
        assertNotNull(shipped);assertEquals(sold,deliveryFacts(),"派单不能再扣库、收款、累计客户或提成");
        requireCreditSuccess(postCreditJson(manager,"/api/admin/orders/"+order+"/ship",Map.of("deliveryStaffId",2)));
        assertEquals(shipped,jdbcTimestamp("SELECT delivery_at FROM order_delivery WHERE order_id="+order));
        assertEquals(sold,deliveryFacts(),"重复派单保持原事实");
        creditReturn(manager,order,creditPreview(manager,order,"2"));
        assertEquals(9L,jdbcLong("SELECT order_status FROM `order` WHERE id="+order));assertOrderReturned(order,"2");
        assertCreditState(order,"50","0","0","0","20","0");
        assertDecimal("退2件后客户净成交30", "30",jdbcDecimal("SELECT total_order_amount FROM customer WHERE id=630"));
        assertDecimal("退2件后只回冲2元", "-2",commissionSum(order,4));
        JsonNode moving=deliveryRow(manager,order,3);
        assertTrue(moving.path("canConfirmReceipt").asBoolean(),moving.toString());
        DeliveryFacts returned=deliveryFacts();
        for(int repeat=0;repeat<2;repeat++) requireCreditSuccess(postCreditJson(manager,"/api/admin/orders/"+order+"/confirm-receipt",Map.of()));
        assertEquals(returned,deliveryFacts(),"部分退货后的首次签收及重放均不重复结算");
        assertEquals(9L,jdbcLong("SELECT order_status FROM `order` WHERE id="+order),"签收不能覆盖部分退款状态9");
        assertEquals(4L,jdbcLong("SELECT delivery_status FROM order_delivery WHERE order_id="+order));
        JsonNode detail=getDeliveryJson(manager,"/api/admin/orders/"+order,Map.of());
        assertNotNull(jdbcTimestamp("SELECT received_at FROM order_delivery WHERE order_id="+order));
        assertTrue(detail.path("delivery").path("receivedAt").isTextual(),detail.toString());
        assertTrue(!detail.path("canConfirmReceipt").asBoolean(),detail.toString());
        assertEquals(initialPending,deliveryDashboard(manager,1).path("pendingDeliveryCount").asLong());

        // 部分退货发生在派单前时，待派送筛选仍必须保留状态9的真实剩余物料。
        long beforeShip=createPOSViaHttp(manager,deliverySale(631,"5"));
        directDeliveryReturn(manager,beforeShip,creditPreview(manager,beforeShip,"2"));
        assertTrue(deliveryRow(manager,beforeShip,2).path("canShip").asBoolean());
        DeliveryFacts beforeDispatch=deliveryFacts();
        requireCreditSuccess(postCreditJson(manager,"/api/admin/orders/"+beforeShip+"/ship",Map.of("deliveryStaffId",2)));
        assertEquals(beforeDispatch,deliveryFacts());assertEquals(9L,jdbcLong("SELECT order_status FROM `order` WHERE id="+beforeShip));
        // 处理中售后暂停签收；改为拒绝后才能继续交付。
        exec("INSERT INTO after_sales(after_sales_no,order_id,customer_id,store_id,type,status,refund_amount) VALUES('DELIVERY-PENDING',"+beforeShip+",631,1,2,10,0)");
        DeliveryFacts paused=deliveryFacts();
        assertCreditRejected(postCreditJson(manager,"/api/admin/orders/"+beforeShip+"/confirm-receipt",Map.of()));
        assertEquals(paused,deliveryFacts());assertTrue(!deliveryRow(manager,beforeShip,3).path("canConfirmReceipt").asBoolean());
        exec("UPDATE after_sales SET status=90 WHERE after_sales_no='DELIVERY-PENDING'");
        requireCreditSuccess(postCreditJson(manager,"/api/admin/orders/"+beforeShip+"/confirm-receipt",Map.of()));

        long fullyReturned=createPOSViaHttp(manager,deliverySale(632,"2"));
        directDeliveryReturn(manager,fullyReturned,creditPreview(manager,fullyReturned,"2"));
        DeliveryFacts full=deliveryFacts();
        assertCreditRejected(postCreditJson(manager,"/api/admin/orders/"+fullyReturned+"/ship",Map.of("deliveryStaffId",2)));
        assertEquals(full,deliveryFacts());assertEquals(8L,jdbcLong("SELECT order_status FROM `order` WHERE id="+fullyReturned));
        assertEquals(initialPending,deliveryDashboard(manager,1).path("pendingDeliveryCount").asLong(),"全退停止配送并移出待派送统计");
        // 显式门店过滤必须使用真实关系，同时保留POS来源。
        var secondCommand=deliverySale(633,"2");secondCommand.setStaffId(3L);secondCommand.setStoreId(2L);
        long second=createPOSViaHttp(secondStore,secondCommand);
        assertEquals(initialPending,deliveryDashboard(manager,1).path("pendingDeliveryCount").asLong());
        assertEquals(secondPending+1,deliveryDashboard(manager,2).path("pendingDeliveryCount").asLong());
        assertEquals(2,deliveryRow(manager,second,2).path("storeId").asInt());
        assertDeliveryReturnReceiptRace(manager);
        log("[配送] POS待派送→人工派单→部分退货→签收/重放，原资金/库存/客户/绩效不重复；全退、处理中售后、门店统计及并发状态保留通过");
    }
    private POSOrderCreateCommand deliverySale(long customer,String quantity) {
        var command=posSale(130L,quantity,"袋","10",true,null,new BigDecimal(quantity).multiply(BigDecimal.TEN).toPlainString());
        command.setCustomerId(customer);command.setDeliveryType(2);command.setReceiverName("配送农户");
        command.setReceiverPhone("13800000630");command.setReceiverAddress("测试村3号");return command;
    }
    private void directDeliveryReturn(E2EToken token,long order,JsonNode quote) throws Exception {
        var body=new LinkedHashMap<>(creditReturnBody(order,quote));
        body.remove("reason");body.put("refundReason","派单前原单退货");
        requireCreditSuccess(postCreditJson(token,"/api/admin/orders/"+order+"/refund",body));
    }
    private JsonNode getDeliveryJson(E2EToken token,String url,Map<String,String> params) throws Exception {
        var request=get(url).header(token.name(),token.value());params.forEach(request::param);
        String response=mockMvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode result=objectMapper.readTree(response);requireCreditSuccess(result);return result;
    }
    private JsonNode deliveryDashboard(E2EToken token,long store) throws Exception {
        return getDeliveryJson(token,"/api/admin/report/dashboard/today",Map.of("storeId",Long.toString(store)));
    }
    private JsonNode deliveryRow(E2EToken token,long order,int status) throws Exception {
        JsonNode result=getDeliveryJson(token,"/api/admin/orders",Map.of("deliveryType","2","deliveryStatus",Integer.toString(status),"pageNum","1","pageSize","200"));
        JsonNode matched=null;
        for(JsonNode row:result.path("records")) {
            assertEquals(status,row.path("deliveryStatus").asInt(),"后端必须真实过滤配送状态："+row);
            assertEquals(2,row.path("deliveryType").asInt(),"配送方式必须来自真实配送关系："+row);
            if(row.path("id").asLong()==order) matched=row;
        }
        if(matched!=null) return matched;
        fail("真实配送状态筛选遗漏POS或部分退货订单 "+order+": "+result);return null;
    }
    private DeliveryFacts deliveryFacts() {
        return new DeliveryFacts(creditFacts(),jdbcLong("SELECT COALESCE(SUM(total_order_count),0) FROM customer"),
                jdbcDecimal("SELECT COALESCE(SUM(total_order_amount),0) FROM customer"),jdbcLong("SELECT COUNT(*) FROM order_pickup_request"));
    }
    private record DeliveryFacts(CreditFacts moneyAndStock,long customerOrders,BigDecimal customerAmount,long pickupRequests) {}
    private void assertDeliveryReturnReceiptRace(E2EToken manager) throws Exception {
        long order=createPOSViaHttp(manager,deliverySale(634,"5"));
        requireCreditSuccess(postCreditJson(manager,"/api/admin/orders/"+order+"/ship",Map.of("deliveryStaffId",2)));
        JsonNode quote=creditPreview(manager,order,"2");Map<String,Object> body=creditReturnBody(order,quote);
        long payments=jdbcLong("SELECT COUNT(*) FROM order_payment WHERE order_id="+order);
        List<JsonNode> responses=raceCreditHttp(index->index==0
                ?postCreditJson(manager,"/api/admin/after-sales",body)
                :postCreditJson(manager,"/api/admin/orders/"+order+"/confirm-receipt",Map.of()));
        for(JsonNode response:responses) requireCreditSuccess(response);
        assertEquals(9L,jdbcLong("SELECT order_status FROM `order` WHERE id="+order),"退货与签收竞争不能丢掉退款状态");
        assertEquals(4L,jdbcLong("SELECT delivery_status FROM order_delivery WHERE order_id="+order));
        assertEquals(payments,jdbcLong("SELECT COUNT(*) FROM order_payment WHERE order_id="+order));
        assertOrderReturned(order,"2");assertCreditState(order,"50","0","0","0","20","0");
        assertDecimal("竞争后客户净成交只30", "30",jdbcDecimal("SELECT total_order_amount FROM customer WHERE id=634"));
        DeliveryFacts completed=deliveryFacts();
        requireCreditSuccess(postCreditJson(manager,"/api/admin/orders/"+order+"/confirm-receipt",Map.of()));
        assertEquals(completed,deliveryFacts(),"竞争后的签收重放仍不重复原资金、库存、绩效和客户事实");
    }

    /** 从真实客户创建/钱包付款开始，验证状态生产方和首次签收前退货的净额结算。 */
    private void assertOnlineDeliveryPaymentAndFirstSettlement() throws Exception {
        E2EToken manager=loginToken(1L);
        exec("INSERT INTO customer_wallet(customer_id,wallet_type,balance) VALUES(635,1,100)");
        var command=new com.agri_system.application.order.dto.command.OrderCreateCommand();
        command.setStoreId(1L);command.setOrderType(3);command.setDeliveryType(2);command.setReceiverName("线上配送农户");
        command.setDeliveryAddress("测试村8号");command.setDeliveryPhone("13800000635");
        var item=new OrderItemDTO();item.setProductId(130L);item.setQuantity(new BigDecimal("5"));item.setUnit("袋");command.setItems(List.of(item));
        long order=orderApplicationService.createOrder(command,635L);
        assertEquals(1L,jdbcLong("SELECT delivery_status FROM order_delivery WHERE order_id="+order));
        var payment=new com.agri_system.application.order.dto.command.OrderPayCommand();payment.setOrderId(order);payment.setUseBalance(true);payment.setUseSpecialFund(false);
        orderApplicationService.previewOrderPayment(payment,635L);
        assertEquals(1L,jdbcLong("SELECT delivery_status FROM order_delivery WHERE order_id="+order),"预览不提前派送");
        assertNull(orderApplicationService.payOrder(payment,635L));
        assertEquals(4L,jdbcLong("SELECT order_status FROM `order` WHERE id="+order));
        assertEquals(2L,jdbcLong("SELECT delivery_status FROM order_delivery WHERE order_id="+order));
        assertTrue(deliveryRow(manager,order,2).path("canShip").asBoolean());
        assertDecimal("线上未签收不提前累计客户消费", "0",jdbcDecimal("SELECT total_order_amount FROM customer WHERE id=635"));
        requireCreditSuccess(postCreditJson(manager,"/api/admin/orders/"+order+"/ship",Map.of("deliveryStaffId",2)));
        creditReturn(manager,order,creditPreview(manager,order,"2"));
        assertDecimal("首次签收前退货不扣客户其它消费", "0",jdbcDecimal("SELECT total_order_amount FROM customer WHERE id=635"));
        requireCreditSuccess(postCreditJson(manager,"/api/admin/orders/"+order+"/confirm-receipt",Map.of()));
        assertDecimal("线上首次签收按剩余成交30累计", "30",jdbcDecimal("SELECT total_order_amount FROM customer WHERE id=635"));
        assertDecimal("首次签收积分只留30", "30",jdbcDecimal("SELECT balance FROM customer_wallet WHERE customer_id=635 AND wallet_type=3"));
        assertEquals(9L,jdbcLong("SELECT order_status FROM `order` WHERE id="+order));
        assertEquals(4L,jdbcLong("SELECT delivery_status FROM order_delivery WHERE order_id="+order));
        assertCreditState(order,"50","0","0","0","20","0");assertOrderReturned(order,"2");
        DeliveryFacts completed=deliveryFacts();
        requireCreditSuccess(postCreditJson(manager,"/api/admin/orders/"+order+"/confirm-receipt",Map.of()));
        assertEquals(completed,deliveryFacts(),"线上首次签收后重放不再记钱、货、积分或提成");
        log("[配送] 真实客户下单→钱包付款推进待派2→派单→首次签收前部分退货→签收保留9，客户/积分仅结算剩余30");
    }

    /** 真实状态3自提核销须先串行原订单，售后处理中暂停；退款后不能覆盖状态。 */
    private void assertOnlinePickupRespectsReturnFacts() throws Exception {
        E2EToken manager=loginToken(1L);
        exec("INSERT INTO customer(id,code,name,phone,type,status) VALUES(636,'PICKUP-C636','线上自提验收','13800000636',1,1)");
        exec("INSERT INTO customer_wallet(customer_id,wallet_type,balance) VALUES(636,1,200)");
        long order=createPaidOnlinePickup();
        String code=jdbcString("SELECT pickup_code FROM `order` WHERE id="+order);
        long item=creditItem(order);
        var apply=new com.agri_system.application.after_sales.dto.command.ClientApplyCommand();
        apply.setOrderId(order);apply.setReason("自提核销前处理中售后暂停");
        long afterSales=afterSalesApplicationService.applyFromClient(apply,636L);
        DeliveryFacts waiting=deliveryFacts();
        assertCreditRejected(postCreditJson(manager,"/api/admin/orders/pickup/verify",Map.of("pickupCode",code)));
        assertEquals(waiting,deliveryFacts());assertEquals(3L,jdbcLong("SELECT order_status FROM `order` WHERE id="+order));
        afterSalesApplicationService.cancel(afterSales,636L);
        requireCreditSuccess(postCreditJson(manager,"/api/admin/orders/pickup/verify",Map.of("pickupCode",code)));
        assertEquals(6L,jdbcLong("SELECT order_status FROM `order` WHERE id="+order));
        assertDecimal("正常自提首次结算客户20","20",jdbcDecimal("SELECT total_order_amount FROM customer WHERE id=636"));
        DeliveryFacts done=deliveryFacts();
        requireCreditSuccess(postCreditJson(manager,"/api/admin/orders/pickup/verify",Map.of("pickupCode",code)));
        assertEquals(done,deliveryFacts(),"核销重放不二次记资金库存客户或积分");
        creditReturn(manager,order,pickupReturnPreview(manager,order,item,"1"));
        DeliveryFacts refunded=deliveryFacts();
        assertCreditRejected(postCreditJson(manager,"/api/admin/orders/pickup/verify",Map.of("pickupCode",code)));
        assertEquals(refunded,deliveryFacts());assertEquals(9L,jdbcLong("SELECT order_status FROM `order` WHERE id="+order));
        long racing=createPaidOnlinePickup();
        String racingCode=jdbcString("SELECT pickup_code FROM `order` WHERE id="+racing);
        JsonNode quote=pickupReturnPreview(manager,racing,creditItem(racing),"1");
        Map<String,Object> body=new LinkedHashMap<>(creditReturnBody(racing,quote));
        body.remove("reason");body.put("refundReason","自提与原单退款竞争");
        List<JsonNode> results=raceCreditHttp(index->index==0
            ?postCreditJson(manager,"/api/admin/orders/"+racing+"/refund",body)
            :postCreditJson(manager,"/api/admin/orders/pickup/verify",Map.of("pickupCode",racingCode)));
        // 核销先获锁则退款入口看到已完成状态而拒绝；退款先获锁则核销看到9而拒绝。
        // 当前各入口的原状态契约不扩张，至少一笔完成；绝不能把退款覆盖成6。
        assertTrue(results.stream().anyMatch(RetailFactChainEndToEndTest::creditSuccess));
        for(JsonNode result:results) if(!creditSuccess(result)) assertCreditRejected(result);
        long finalStatus=jdbcLong("SELECT order_status FROM `order` WHERE id="+racing);
        assertTrue(finalStatus==6 || finalStatus==9);
        if(creditSuccess(results.get(0))) {
            assertEquals(9L,finalStatus);assertOrderReturned(racing,"1");
            assertDecimal("先退款则核销不能错误首次结算","10",jdbcDecimal("SELECT total_order_amount FROM customer WHERE id=636"));
        } else assertEquals(6L,finalStatus);
        log("[自提] 真实钱包付款→处理中售后暂停→核销/重放→退货保留9，以及核销退款竞争无旧状态覆盖");
    }
    private void assertConcurrentMultiPackageSale() throws Exception {
        E2EToken manager=loginToken(1L);
        JsonNode first=packageQuote(manager,201L,"1",null),second=packageQuote(manager,203L,"1",null);
        var a=packageSale(201L,"1",first,1L);var b=packageSale(203L,"1",second,1L);
        var reverse=packageSale(203L,"1",second,1L);
        a.setItems(List.of(a.getItems().get(0),b.getItems().get(0)));
        reverse.setItems(List.of(reverse.getItems().get(0),packageSale(201L,"1",first,1L).getItems().get(0)));
        BigDecimal total=first.path("totalAmount").decimalValue().add(second.path("totalAmount").decimalValue());
        a.setReceiveAmount(total);reverse.setReceiveAmount(total);
        long beforeOrders=jdbcLong("SELECT COUNT(*) FROM `order`");BigDecimal crop=packageStock(110L),seed=packageStock(111L);
        List<JsonNode> results=raceCreditHttp(index->postCreditJson(manager,"/api/admin/orders/pos",index==0?a:reverse));
        for(JsonNode result:results) assertTrue(result.isIntegralNumber() && result.asLong()>0,"反向套餐购物车均应开单成功："+result);
        assertEquals(beforeOrders+2,jdbcLong("SELECT COUNT(*) FROM `order`"));
        assertDecimal("两笔双套餐只扣四套物料",crop.subtract(new BigDecimal("48")).toPlainString(),packageStock(110L));
        assertDecimal("两笔双套餐只扣四套种子",seed.subtract(new BigDecimal("4")).toPlainString(),packageStock(111L));
        for(int index=0;index<2;index++) {
            long order=results.get(index).asLong();
            assertDecimal("各笔实际收款与成交一致",total.toPlainString(),jdbcDecimal("SELECT SUM(amount) FROM order_payment WHERE order_id="+order));
            List<Object[]> groups=rows("SELECT package_id FROM order_item WHERE order_id="+order+" GROUP BY package_group_no,package_id ORDER BY package_group_no");
            assertEquals(index==0?201L:203L,((Number)groups.get(0)[0]).longValue(),"加锁排序不改变购物车套餐组序");
        }
        log("[套餐锁] 相反购物车顺序双套餐两笔实库并发都成交且保持原组序与精确钱货");
    }

    private long createPaidOnlinePickup() {
        var command=new com.agri_system.application.order.dto.command.OrderCreateCommand();command.setStoreId(1L);command.setOrderType(2);
        var line=new OrderItemDTO();line.setProductId(130L);line.setQuantity(new BigDecimal("2"));line.setUnit("袋");command.setItems(List.of(line));
        long order=orderApplicationService.createOrder(command,636L);
        var pay=new com.agri_system.application.order.dto.command.OrderPayCommand();pay.setOrderId(order);pay.setUseBalance(true);pay.setUseSpecialFund(false);
        assertNull(orderApplicationService.payOrder(pay,636L));assertEquals(3L,jdbcLong("SELECT order_status FROM `order` WHERE id="+order));return order;
    }

    /** 原销售单的应收、实还、冲欠款、已退来源和实物回库均走真实HTTP/事务。 */
    private void assertCreditSettlementBusinessChain() throws Exception {
        seedCreditFixture();
        E2EToken manager = loginToken(1L), cashier = loginToken(2L), otherStore = loginToken(3L);
        long unpaid = creditSale(manager,600,1,"10","0","100");
        assertCreditState(unpaid,"0","100","0","0","0","100");
        assertDecimal("新支付来源0必须由MyBatis回读", "0", paymentRepository.findByOrderId(unpaid).get(0).getRefundedAmount());
        JsonNode unpaidQuote = creditPreview(manager,unpaid,"3");
        assertPreview(unpaidQuote,"30","30","0");
        assertEquals(0,unpaidQuote.path("sources").size(),"全冲欠款不得虚构现金退款来源");
        creditReturn(manager,unpaid,unpaidQuote);
        assertCreditState(unpaid,"0","100","0","30","0","70");
        assertOrderReturned(unpaid,"3");

        long partial = creditSale(manager,601,1,"10","0","100");
        JsonNode bankBatch = requireCreditSuccess(repay(manager,601,"40",4,"CREDIT-BANK-40",null,null));
        assertDecimal("还款批次保存原单40元","40",bankBatch.path("amount").decimalValue());
        assertEquals(1,bankBatch.path("allocations").size());
        assertDecimal("新还款来源0由MyBatis回读","0",repaymentRepository.findByRequestKey(1L,"CREDIT-BANK-40").get(0).getRefundedAmount());
        JsonNode partialQuote = creditPreview(manager,partial,"8");
        assertPreview(partialQuote,"80","60","20");
        assertEquals("CREDIT_REPAYMENT",partialQuote.path("sources").get(0).path("sourceType").asText());
        assertTrue(partialQuote.path("sources").get(0).path("paymentMethodName").asText().contains("银行"),partialQuote.toString());
        long partialAfterSales = creditReturn(manager,partial,partialQuote);
        assertCreditState(partial,"0","100","40","60","20","0");
        assertOrderReturned(partial,"8");
        assertDecimal("售后实际冲欠款60","60",afterSalesRepository.findById(partialAfterSales).orElseThrow().getCreditReductionAmount());
        assertDecimal("售后实际退还款20","20",afterSalesRepository.findById(partialAfterSales).orElseThrow().getCashRefundAmount());
        assertEquals(0L,jdbcLong("SELECT COUNT(*) FROM wallet_transaction WHERE customer_id=601 AND wallet_type IN(1,2)"),
                "银行还款编码4不能当作本金钱包退款");
        assertEquals(0L,jdbcLong("SELECT COUNT(*) FROM customer_wallet WHERE customer_id=601 AND wallet_type IN(1,2)"));

        long mixed = creditSale(manager,602,1,"10","40","60");
        requireCreditSuccess(repay(manager,602,"20",1,"CREDIT-MIXED-20",null,null));
        JsonNode mixedQuote = creditPreview(manager,mixed,"10");
        assertPreview(mixedQuote,"100","40","60");
        assertEquals(2,mixedQuote.path("sources").size(),"先退原单20元还款，再退原现金40元");
        creditReturn(manager,mixed,mixedQuote);
        assertCreditState(mixed,"40","60","20","40","60","0");
        assertOrderReturned(mixed,"10");
        assertDecimal("原现金来源只累计退40","40",jdbcDecimal("SELECT refunded_amount FROM order_payment WHERE order_id="+mixed+" AND payment_type=1"));
        assertDecimal("原还款来源只累计退20","20",jdbcDecimal("SELECT refunded_amount FROM credit_repayment_log WHERE request_key='CREDIT-MIXED-20'"));

        long cash = creditSale(manager,603,1,"10","100","0");
        for (String quantity : List.of("3","3","4")) creditReturn(manager,cash,creditPreview(manager,cash,quantity));
        assertCreditState(cash,"100","0","0","0","100","0");
        assertOrderReturned(cash,"10");
        assertDecimal("分次现金退款原来源封顶100","100",jdbcDecimal("SELECT refunded_amount FROM order_payment WHERE order_id="+cash+" AND payment_type=1"));
        CreditFacts cashFacts=creditFacts();
        assertCreditRejected(postCreditJson(manager,"/api/admin/after-sales",Map.of("orderId",cash,"refundAmount",10,
                "reason","重复已全退单","items",List.of(Map.of("orderItemId",creditItem(cash),"quantity",1,"refundAmount",10)))));
        assertEquals(cashFacts,creditFacts(),"全退后重复退货不得改资金、数量或库存");

        long stale = creditSale(manager,604,1,"10","0","100");
        JsonNode staleQuote = creditPreview(manager,stale,"3");
        requireCreditSuccess(repay(manager,604,"90",2,"CREDIT-STALE-90",null,null));
        CreditFacts staleFacts=creditFacts();
        assertCreditRejected(postCreditJson(manager,"/api/admin/after-sales",creditReturnBody(stale,staleQuote)));
        assertEquals(staleFacts,creditFacts(),"还款改变资金拆分后旧预览整单拒绝，无钱货数量副作用");
        JsonNode freshQuote = creditPreview(manager,stale,"3");
        assertTrue(!staleQuote.path("quoteId").asText().equals(freshQuote.path("quoteId").asText()),"还款改变资金指纹");
        assertPreview(freshQuote,"30","10","20");
        creditReturn(manager,stale,freshQuote);
        assertCreditState(stale,"0","100","90","10","20","0");
        assertOrderReturned(stale,"3");

        long sameKey = creditSale(manager,605,1,"10","0","100");
        List<JsonNode> same = raceCreditHttp(index -> repay(manager,605,"40",1,"CREDIT-SAME-KEY",null,null));
        assertTrue(same.stream().allMatch(RetailFactChainEndToEndTest::creditSuccess),same.toString());
        assertEquals(same.get(0).path("repaymentNo").asText(),same.get(1).path("repaymentNo").asText());
        assertEquals(1L,jdbcLong("SELECT COUNT(*) FROM credit_repayment_log WHERE request_key='CREDIT-SAME-KEY'"));
        assertCreditState(sameKey,"0","100","40","0","0","60");
        CreditFacts retryFacts=creditFacts();
        requireCreditSuccess(repay(manager,605,"40",1,"CREDIT-SAME-KEY",null,null));
        assertEquals(retryFacts,creditFacts(),"相同还款重试只返回原批次");
        long crossCustomer = creditSale(manager,606,1,"10","0","100");
        CreditFacts crossFacts=creditFacts();
        assertCreditRejected(repay(manager,606,"40",1,"CREDIT-SAME-KEY",null,null));
        assertEquals(crossFacts,creditFacts(),"请求标识不能绑另一客户");
        assertCreditState(crossCustomer,"0","100","0","0","0","100");

        long competing = creditSale(manager,607,1,"10","0","100");
        List<JsonNode> different = raceCreditHttp(index -> repay(manager,607,"60",1,"CREDIT-DIFFERENT-"+index,null,null));
        assertEquals(1L,different.stream().filter(RetailFactChainEndToEndTest::creditSuccess).count(),different.toString());
        different.stream().filter(result->!creditSuccess(result)).forEach(result->{
            assertCreditRejected(result);assertTrue(result.path("message").asText().contains("剩余欠款"),result.toString());
        });
        assertCreditState(competing,"0","100","60","0","0","40");
        requireCreditSuccess(repay(manager,607,"40",1,"CREDIT-CLEAR-40",null,null));
        assertCreditState(competing,"0","100","100","0","0","0");

        long racing = creditSale(manager,608,1,"10","0","100");
        JsonNode beforeRace = creditPreview(manager,racing,"10");
        List<JsonNode> repaymentVsReturn = raceCreditHttp(index -> index==0
                ? repay(manager,608,"100",1,"CREDIT-VS-RETURN",null,null)
                : postCreditJson(manager,"/api/admin/after-sales",creditReturnBody(racing,beforeRace)));
        assertEquals(1L,repaymentVsReturn.stream().filter(RetailFactChainEndToEndTest::creditSuccess).count(),
                "先还款则旧退款预览拒绝；先全退则无欠款可还："+repaymentVsReturn);
        repaymentVsReturn.stream().filter(result->!creditSuccess(result)).forEach(result->{
            assertCreditRejected(result);String message=result.path("message").asText();
            assertTrue(message.contains("无欠款") || message.contains("预览已变化"),result.toString());
        });
        if (creditSuccess(repaymentVsReturn.get(0))) {
            assertCreditState(racing,"0","100","100","0","0","0");
            JsonNode afterRace=creditPreview(manager,racing,"10");
            assertPreview(afterRace,"100","0","100");
            creditReturn(manager,racing,afterRace);
            assertCreditState(racing,"0","100","100","0","100","0");
        } else assertCreditState(racing,"0","100","0","100","0","0");
        assertOrderReturned(racing,"10");

        long fifoFirst=creditSale(manager,609,1,"6","0","60");
        long fifoSecond=creditSale(manager,609,1,"4","0","40");
        JsonNode fifo=requireCreditSuccess(repay(manager,609,"70",3,"CREDIT-FIFO-70",null,null));
        assertEquals(2,fifo.path("allocations").size(),"一次收款保存两笔原单分配");
        assertEquals(1L,jdbcLong("SELECT COUNT(*) FROM credit_repayment_log WHERE request_key='CREDIT-FIFO-70'"),"只有批次首分配持有唯一请求标识");
        assertEquals(2L,jdbcLong("SELECT COUNT(*) FROM credit_repayment_log WHERE customer_id=609 AND repayment_no='"+fifo.path("repaymentNo").asText()+"'"),"批次查询必须包含request_key为NULL的第二分配");
        CreditFacts fifoFacts=creditFacts();
        JsonNode fifoReplay=requireCreditSuccess(repay(manager,609,"70",3,"CREDIT-FIFO-70",null,null));
        assertEquals(2,fifoReplay.path("allocations").size(),"首分配防重重放仍返回完整原单分配");
        assertEquals(fifo.path("repaymentNo").asText(),fifoReplay.path("repaymentNo").asText());
        assertEquals(fifoFacts,creditFacts(),"多原单批次重放不能另记收款");
        assertCreditState(fifoFirst,"0","60","60","0","0","0");
        assertCreditState(fifoSecond,"0","40","10","0","0","30");
        JsonNode list=objectMapper.readTree(mockMvc.perform(get("/api/admin/credit/repayments").header(manager.name(),manager.value()).param("customerId","609"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertTrue(list.isArray()&&list.size()==1,list.toString());
        assertEquals(2,list.get(0).path("allocations").size());
        List<String> allocatedOrderNos=new ArrayList<>();
        for(JsonNode allocation:list.get(0).path("allocations")) allocatedOrderNos.add(allocation.path("orderNo").asText());
        assertTrue(allocatedOrderNos.contains(jdbcString("SELECT order_no FROM `order` WHERE id="+fifoFirst)),list.toString());
        assertTrue(allocatedOrderNos.contains(jdbcString("SELECT order_no FROM `order` WHERE id="+fifoSecond)),list.toString());

        long storeOne=creditSale(manager,610,1,"10","0","100");
        long storeTwo=creditSale(otherStore,610,3,"10","0","100");
        CreditFacts scopeFacts=creditFacts();
        assertCreditRejected(repay(cashier,610,"10",1,"CREDIT-WRONG-STORE",2L,null));
        assertCreditRejected(repay(cashier,610,"10",1,"CREDIT-WRONG-EMPLOYEE",null,1L));
        assertEquals(scopeFacts,creditFacts(),"普通员工不能借请求体伪造门店或操作员");
        JsonNode scopeSummary=objectMapper.readTree(mockMvc.perform(get("/api/admin/credit/statistics")
                        .header(cashier.name(),cashier.value()).param("customerId","610"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertDecimal("普通员工本店欠款不是客户两店总额","100",scopeSummary.path("totalDebt").decimalValue());
        assertEquals(1,scopeSummary.path("storeId").asInt());
        requireCreditSuccess(repay(manager,610,"20",1,"CREDIT-ADMIN-STORE-2",2L,null));
        assertCreditState(storeOne,"0","100","0","0","0","100");
        assertCreditState(storeTwo,"0","100","20","0","0","80");
        JsonNode accounts=objectMapper.readTree(mockMvc.perform(get("/api/admin/credit/accounts")
                        .header(cashier.name(),cashier.value()).param("customerId","610"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertEquals(1,accounts.path("records").size());
        assertEquals(storeOne,accounts.path("records").get(0).path("orderId").asLong());

        assertRefundQuoteReplaysOneFact(manager);
        assertZeroNetCouponReturnsAndClientCancellation(manager);
        assertNewCreditSaleAndReturnLockOrder(manager);
        assertCashReturnAndNewSaleLockOrder(manager);
        assertNewCreditSaleAndRepaymentLockOrder(manager);
        assertCrossCustomerSameKeyHasSingleWinner(manager);
        assertDifferentCustomerBatchReplaysStayIndependent(manager);
        assertUnknownHistoricalFundingRemainsUnknown(manager);
        log("[赊销] 纯赊/混付、银行还款、原单退款来源、FIFO批次、重复与并发还款、还款退货竞态及本店范围均按真实资金和库存事实验收");
    }

    /** 完整报价事实就是请求身份，双提交和丢响应重放只能取原完成结果。 */
    private void assertRefundQuoteReplaysOneFact(E2EToken manager) throws Exception {
        long order=creditSale(manager,619,1,"10","100","0");
        JsonNode quote=creditPreview(manager,order,"1");
        Map<String,Object> body=creditReturnBody(order,quote);
        Map<String,Object> noQuote=new LinkedHashMap<>(body);noQuote.remove("quoteId");
        CreditFacts before=creditFacts();
        assertCreditRejected(postCreditJson(manager,"/api/admin/after-sales",noQuote));
        assertEquals(before,creditFacts(),"无报价不能开第二条现金退款路径，钱货和事件均不改变");
        List<JsonNode> results=raceCreditHttp(index->postCreditJson(manager,"/api/admin/after-sales",body));
        assertTrue(results.stream().allMatch(JsonNode::isIntegralNumber),results.toString());
        assertEquals(results.get(0).asLong(),results.get(1).asLong(),"相同报价并发应返回同一完成售后ID");
        long afterSales=results.get(0).asLong();
        assertEquals(quote.path("quoteId").asText(),jdbcString("SELECT refund_quote_id FROM after_sales WHERE id="+afterSales));
        assertTrue(jdbcString("SELECT refund_request_hash FROM after_sales WHERE id="+afterSales).matches("[0-9a-f]{64}"),"新请求元数据保存完整SHA256，不能截断或塞可读备注");
        assertEquals(before.trading().afterSales()+1,jdbcLong("SELECT COUNT(*) FROM after_sales"));
        assertEquals(1L,jdbcLong("SELECT COUNT(*) FROM after_sales WHERE order_id="+order));
        assertEquals(1L,jdbcLong("SELECT COUNT(*) FROM performance_event_record WHERE event_type='ORDER_REFUNDED' AND biz_id="+order));
        assertCreditState(order,"100","0","0","0","10","0");assertOrderReturned(order,"1");
        CreditFacts completed=creditFacts();
        JsonNode replay=postCreditJson(manager,"/api/admin/after-sales",body);
        assertEquals(afterSales,replay.asLong(),"丢成功响应后相同请求返回原完成结果");
        assertEquals(completed,creditFacts(),"响应重放不得再退10元或回库第二件");
        for(Map<String,Object> changed:List.<Map<String,Object>>of(
                Map.of("refundAmount",20,"items",List.of(Map.of("orderItemId",creditItem(order),"quantity",2,"refundAmount",20))),
                Map.of("items",List.of(Map.of("orderItemId",creditItem(order),"quantity",2,"refundAmount",10))),
                Map.of("reason","不能改成另一退货原因"))) {
            Map<String,Object> edited=new LinkedHashMap<>(body);edited.putAll(changed);
            assertCreditRejected(postCreditJson(manager,"/api/admin/after-sales",edited));
            assertEquals(completed,creditFacts(),"同报价改变参数不能冒用原成功结果");
        }
        E2EToken anotherActor=loginToken(4L);
        assertCreditRejected(postCreditJson(anotherActor,"/api/admin/after-sales",body));
        assertEquals(completed,creditFacts(),"同报价不能复用到另一退款员工");
        log("[退货请求] 相同quote并发/丢响应重放仅一售后、一笔10元退款、一件原批次回库、一个退款事件；篡改参数/员工均拒绝");
    }

    /** 全券0元也按剩余实物判断券恢复；线上取消的售后子明细沿原净额。 */
    private void assertZeroNetCouponReturnsAndClientCancellation(E2EToken manager) throws Exception {
        exec("INSERT INTO customer_coupon(id,customer_id,name,amount,start_time,end_time,status) VALUES"
                +"(9501,615,'两件全额券',20,DATE_SUB(NOW(),INTERVAL 1 DAY),DATE_ADD(NOW(),INTERVAL 1 DAY),1),"
                +"(9502,616,'线上30元券',30,DATE_SUB(NOW(),INTERVAL 1 DAY),DATE_ADD(NOW(),INTERVAL 1 DAY),1)");
        var free=posSale(130L,"2","袋","10",false,null,"20");free.setCustomerId(615L);free.setCouponId(9501L);
        long freeOrder=createPOSViaHttp(manager,free);
        assertDecimal("两件券全抵净应结算为0","0",jdbcDecimal("SELECT pay_amount FROM `order` WHERE id="+freeOrder));
        assertEquals(2L,jdbcLong("SELECT status FROM customer_coupon WHERE id=9501"));
        JsonNode first=creditPreview(manager,freeOrder,"1");assertPreview(first,"0","0","0");creditReturn(manager,freeOrder,first);
        assertEquals(2L,jdbcLong("SELECT status FROM customer_coupon WHERE id=9501"),"只退一件，N=0不能提前恢复全额券");
        assertOrderReturned(freeOrder,"1");
        JsonNode second=creditPreview(manager,freeOrder,"1");assertPreview(second,"0","0","0");creditReturn(manager,freeOrder,second);
        assertEquals(1L,jdbcLong("SELECT status FROM customer_coupon WHERE id=9501"),"两件全部实退才恢复全额券");
        assertOrderReturned(freeOrder,"2");assertCreditState(freeOrder,"0","0","0","0","0","0");

        exec("UPDATE store SET support_pickup=1 WHERE id=1");
        exec("INSERT INTO customer_wallet(customer_id,wallet_type,balance) VALUES(616,1,100)");
        var online=new com.agri_system.application.order.dto.command.OrderCreateCommand();online.setStoreId(1L);online.setOrderType(2);online.setCustomerId(616L);
        var material=new OrderItemDTO();material.setProductId(130L);material.setQuantity(new BigDecimal("10"));material.setUnit("袋");online.setItems(List.of(material));
        long onlineOrder=orderApplicationService.createOrder(online,616L);
        var pay=new com.agri_system.application.order.dto.command.OrderPayCommand();pay.setOrderId(onlineOrder);pay.setUseBalance(true);pay.setCouponId(9502L);
        assertNull(orderApplicationService.payOrder(pay,616L),"线上本金足够，应通过真实内部支付完成，不制造外部支付成功");
        assertDecimal("线上商品原gross100","100",jdbcDecimal("SELECT total_amount FROM `order` WHERE id="+onlineOrder));
        assertDecimal("线上券后净应收70","70",jdbcDecimal("SELECT pay_amount FROM `order` WHERE id="+onlineOrder));
        assertDecimal("线上钱包只实扣70","30",walletBalance(616,1));
        var application=new com.agri_system.application.after_sales.dto.command.ClientApplyCommand();application.setOrderId(onlineOrder);application.setReason("客户端券后净额取消验收");
        long afterSales=afterSalesApplicationService.applyFromClient(application,616L);
        var pending=afterSalesRepository.findById(afterSales).orElseThrow();
        assertNull(pending.getCreditReductionAmount(),"待处理售后尚无实际冲欠款，保存NULL而非假0");
        assertNull(pending.getCashRefundAmount(),"待处理售后尚无实际退款，保存NULL而非假0");
        assertDecimal("客户端申请子明细保存净70不是gross100","70",jdbcDecimal("SELECT SUM(refund_amount) FROM after_sales_item WHERE after_sales_id="+afterSales));
        JsonNode detail=objectMapper.readTree(mockMvc.perform(get("/api/admin/after-sales/"+afterSales).header(manager.name(),manager.value()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertDecimal("真实API子明细净额70","70",detail.path("items").get(0).path("refundAmount").decimalValue());
        JsonNode audit=postCreditJson(manager,"/api/admin/after-sales/"+afterSales+"/audit",Map.of("approved",true,"remark","拦截取消通过"));
        assertTrue(audit==null||audit.isNull()||creditSuccess(audit));
        JsonNode quote=postCreditJson(manager,"/api/admin/orders/"+onlineOrder+"/refund-preview",
                Map.of("afterSalesId",afterSales,"items",List.of(Map.of("orderItemId",creditItem(onlineOrder),"quantity",10,"refundAmount",70))));
        requireCreditSuccess(quote);assertPreview(quote,"70","0","70");
        postCreditJson(manager,"/api/admin/after-sales/"+afterSales+"/refund",Map.of("refundAmount",70,"quoteId",quote.path("quoteId").asText()));
        assertEquals(50L,jdbcLong("SELECT status FROM after_sales WHERE id="+afterSales));
        assertDecimal("线上原本金退款恢复100","100",walletBalance(616,1));
        assertOrderReturned(onlineOrder,"10");assertCreditState(onlineOrder,"70","0","0","0","70","0");
        assertEquals(1L,jdbcLong("SELECT status FROM customer_coupon WHERE id=9502"));
    }

    /** 两业务先在同一客户锁排队，稳定检查customer→库存顺序，禁止把死锁当通过。 */
    private void assertNewCreditSaleAndReturnLockOrder(E2EToken manager) throws Exception {
        long original=creditSale(manager,614,1,"10","0","100");
        JsonNode quote=creditPreview(manager,original,"3");
        var newSale=posSale(130L,"2","袋","10",false,null,"0");newSale.setCustomerId(614L);newSale.setPaymentType(6);
        var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        try(Connection hold=dataSource.getConnection()) {
            hold.setAutoCommit(false);
            try(PreparedStatement lock=hold.prepareStatement("SELECT id FROM customer WHERE id=614 FOR UPDATE")) {
                try(ResultSet rs=lock.executeQuery()) { assertTrue(rs.next()); }
            }
            var returning=pool.submit(()->postCreditJson(manager,"/api/admin/after-sales",creditReturnBody(original,quote)));
            waitForCustomerWaiters(1);
            var selling=pool.submit(()->postCreditJson(manager,"/api/admin/orders/pos",newSale));
            waitForCustomerWaiters(2);
            hold.commit();
            JsonNode returned=returning.get(35,java.util.concurrent.TimeUnit.SECONDS);
            JsonNode sold=selling.get(35,java.util.concurrent.TimeUnit.SECONDS);
            requireCreditSuccess(returned);requireCreditSuccess(sold);
            assertTrue(sold.isIntegralNumber()&&sold.asLong()>0,sold.toString());
            assertCreditState(original,"0","100","0","30","0","70");
            assertCreditState(sold.asLong(),"0","20","0","0","0","20");
            assertOrderReturned(original,"3");
            log("[锁序] 已观测两请求同customer排队，原单退货与新赊销无死锁且双方钱货仅记一次");
        } finally { pool.shutdownNow(); }
    }
    /** 现金也只有报价后的正式入口，与新POS遵守相同customer→库存锁序。 */
    private void assertCashReturnAndNewSaleLockOrder(E2EToken manager) throws Exception {
        long original=creditSale(manager,618,1,"10","100","0");
        var newSale=posSale(130L,"2","袋","10",false,null,"20");newSale.setCustomerId(618L);
        JsonNode quote=creditPreview(manager,original,"3");
        Map<String,Object> returnBody=creditReturnBody(original,quote);
        var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        try(Connection hold=dataSource.getConnection()) {
            hold.setAutoCommit(false);
            try(PreparedStatement lock=hold.prepareStatement("SELECT id FROM customer WHERE id=618 FOR UPDATE")) {
                try(ResultSet rs=lock.executeQuery()) { assertTrue(rs.next()); }
            }
            // 新销售先排队，现金正式退货后排队；同一锁序下双方均能完成。
            var selling=pool.submit(()->postCreditJson(manager,"/api/admin/orders/pos",newSale));
            waitForCustomerWaiters(1);
            var returning=pool.submit(()->postCreditJson(manager,"/api/admin/after-sales",returnBody));
            waitForCustomerWaiters(2);
            hold.commit();
            JsonNode sold=selling.get(35,java.util.concurrent.TimeUnit.SECONDS);
            JsonNode returned=returning.get(35,java.util.concurrent.TimeUnit.SECONDS);
            requireCreditSuccess(sold);requireCreditSuccess(returned);
            assertTrue(sold.isIntegralNumber()&&sold.asLong()>0,sold.toString());
            assertCreditState(original,"100","0","0","0","30","0");
            assertCreditState(sold.asLong(),"20","0","0","0","0","0");
            assertOrderReturned(original,"3");
            log("[锁序] 强制quote现金退货与新销售已观测customer排队，双方成功、无死锁、钱货不重记");
        } finally { pool.shutdownNow(); }
    }

    /** 新POS先等待customer，随后还款排队；外键写store时不能形成反向门店锁环。 */
    private void assertNewCreditSaleAndRepaymentLockOrder(E2EToken manager) throws Exception {
        long original=creditSale(manager,621,1,"10","0","100");
        var newSale=posSale(130L,"2","袋","10",false,null,"0");
        newSale.setCustomerId(621L);newSale.setPaymentType(6);
        var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        CreditFacts before=creditFacts();
        try(Connection hold=dataSource.getConnection()) {
            hold.setAutoCommit(false);
            try(PreparedStatement lock=hold.prepareStatement("SELECT id FROM customer WHERE id=621 FOR UPDATE")) {
                try(ResultSet rs=lock.executeQuery()) { assertTrue(rs.next()); }
            }
            var selling=pool.submit(()->postCreditJson(manager,"/api/admin/orders/pos",newSale));
            waitForCustomerWaiters(1);
            var repaying=pool.submit(()->repay(manager,621,"40",1,"CREDIT-POS-VS-REPAY",null,null));
            waitForCustomerWaiters(2);
            hold.commit();
            JsonNode sold=requireCreditSuccess(selling.get(35,java.util.concurrent.TimeUnit.SECONDS));
            JsonNode batch=requireCreditSuccess(repaying.get(35,java.util.concurrent.TimeUnit.SECONDS));
            assertTrue(sold.isIntegralNumber()&&sold.asLong()>0,sold.toString());
            assertEquals(1,batch.path("allocations").size());
            assertEquals(original,batch.path("allocations").get(0).path("orderId").asLong(),"还款沿最早原单分配，不改新挂账20元");
            assertCreditState(original,"0","100","40","0","0","60");
            assertCreditState(sold.asLong(),"0","20","0","0","0","20");
            assertOrderReturned(original,"0");assertOrderReturned(sold.asLong(),"0");
            assertEquals(before.repayments()+1,jdbcLong("SELECT COUNT(*) FROM credit_repayment_log"));
            assertEquals(1L,jdbcLong("SELECT COUNT(*) FROM credit_repayment_log WHERE request_key='CREDIT-POS-VS-REPAY'"));
            assertDecimal("新赊销只实出库2袋","2",jdbcDecimal("SELECT SUM(a.allocated_base_quantity) FROM order_item_batch_allocation a JOIN order_item i ON i.id=a.order_item_id WHERE i.order_id="+sold.asLong()));
            log("[锁序] MySQL已观测POS与还款先后等待同customer，order及credit_account门店外键写入均成功，无store/customer死锁");
        } finally { pool.shutdownNow(); }
    }

    /** 不同customer无需串行锁门店，唯一请求身份必须使一个胜出且另一事务完整回滚。 */
    private void assertCrossCustomerSameKeyHasSingleWinner(E2EToken manager) throws Exception {
        long first=creditSale(manager,622,1,"10","0","100");
        long second=creditSale(manager,623,1,"10","0","100");
        CreditFacts before=creditFacts();
        List<JsonNode> responses=raceCreditHttp(index->repay(manager,index==0?622:623,"40",1,"CREDIT-CROSS-CUSTOMER-RACE",null,null));
        assertEquals(1L,responses.stream().filter(RetailFactChainEndToEndTest::creditSuccess).count(),responses.toString());
        responses.stream().filter(result->!creditSuccess(result)).forEach(this::assertCreditRejected);
        long winner=jdbcLong("SELECT customer_id FROM credit_repayment_log WHERE request_key='CREDIT-CROSS-CUSTOMER-RACE'");
        assertTrue(winner==622||winner==623);
        assertCreditState(first,"0","100",winner==622?"40":"0","0","0",winner==622?"60":"100");
        assertCreditState(second,"0","100",winner==623?"40":"0","0","0",winner==623?"60":"100");
        assertEquals(before.repayments()+1,jdbcLong("SELECT COUNT(*) FROM credit_repayment_log"),"跨客户争用不能保留失败批次");
        assertDecimal("仅胜者累计实还40",before.paid().add(new BigDecimal("40")).toPlainString(),jdbcDecimal("SELECT COALESCE(SUM(paid_amount),0) FROM credit_account"));
        assertDecimal("总欠款只减少一次40",before.debt().subtract(new BigDecimal("40")).toPlainString(),jdbcDecimal("SELECT COALESCE(SUM(remaining_amount),0) FROM credit_account"));
        CreditFacts completed=creditFacts();
        JsonNode replay=requireCreditSuccess(repay(manager,winner,"40",1,"CREDIT-CROSS-CUSTOMER-RACE",null,null));
        assertEquals(winner,replay.path("customerId").asLong());assertEquals(completed,creditFacts());
        assertCreditRejected(repay(manager,winner==622?623:622,"40",1,"CREDIT-CROSS-CUSTOMER-RACE",null,null));
        assertEquals(completed,creditFacts(),"败者不能改客户复用已完成请求");
        log("[还款请求] 跨客户同店同key并发唯一胜者、失败完整回滚；胜者重放/败者复用均无新事实");
    }

    private void assertDifferentCustomerBatchReplaysStayIndependent(E2EToken manager) throws Exception {
        creditSale(manager,624,1,"10","0","100");
        creditSale(manager,625,1,"10","0","100");
        JsonNode first=requireCreditSuccess(repay(manager,624,"40",1,"CREDIT-BATCH-A",null,null));
        JsonNode second=requireCreditSuccess(repay(manager,625,"40",1,"CREDIT-BATCH-B",null,null));
        var plan=jdbcTemplate.queryForList("EXPLAIN FORMAT=TRADITIONAL SELECT * FROM credit_repayment_log WHERE store_id=? AND repayment_no=? ORDER BY id FOR UPDATE",
                1,first.path("repaymentNo").asText());
        assertEquals("idx_credit_repayment_batch",plan.get(0).get("key"),"完整批次当前读不能扫描整个门店并锁到别的客户");
        CreditFacts before=creditFacts();
        for(int attempt=0;attempt<10;attempt++) {
            List<JsonNode> replay=raceCreditHttp(index-> index==0
                    ? repay(manager,624,"40",1,"CREDIT-BATCH-A",null,null)
                    : repay(manager,625,"40",1,"CREDIT-BATCH-B",null,null));
            assertEquals(first.path("repaymentNo").asText(),requireCreditSuccess(replay.get(0)).path("repaymentNo").asText());
            assertEquals(second.path("repaymentNo").asText(),requireCreditSuccess(replay.get(1)).path("repaymentNo").asText());
            assertEquals(before,creditFacts(),"不同客户批次并发重放不能多收款、互相核销或扩大锁范围");
        }
        log("[批次] 实际MySQL使用门店+还款号索引，不同客户已完成批次十轮并发重放均成功且事实不变");
    }

    private void waitForCustomerWaiters(int expected) throws Exception {
        long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
        while(System.nanoTime()<deadline) {
            long waiting=jdbcLong("SELECT COUNT(*) FROM performance_schema.data_lock_waits w JOIN performance_schema.data_locks l"
                    +" ON w.REQUESTING_ENGINE_LOCK_ID=l.ENGINE_LOCK_ID WHERE l.OBJECT_SCHEMA='"+DB_NAME+"' AND l.OBJECT_NAME='customer'");
            if(waiting>=expected) return;
            Thread.sleep(25);
        }
        fail("未观测到本次测试库"+expected+"个customer锁等待，不能假称已覆盖并发锁序");
    }

    private void seedCreditFixture() {
        exec("INSERT INTO staff(id,code,name,store_id,role,status) VALUES(3,'CREDIT-STAFF-STORE-2','第二店员工',2,'STAFF',1),(4,'CREDIT-REFUND-ACTOR','另一退款员工',1,'STAFF',1)");
        exec("INSERT INTO sys_staff_role(staff_id,role_id) SELECT 1,id FROM sys_role WHERE code='SUPER_ADMIN' ON DUPLICATE KEY UPDATE staff_id=VALUES(staff_id)");
        exec("INSERT INTO sys_role(id,name,code,status) VALUES(902,'账款普通员工','E2E_CREDIT_CLERK',1)");
        exec("INSERT INTO sys_staff_role(staff_id,role_id) VALUES(2,902),(3,902),(4,902)");
        assertEquals(1L,jdbcLong("SELECT COUNT(*) FROM sys_staff_role sr JOIN sys_role r ON r.id=sr.role_id WHERE sr.staff_id=1 AND r.code='SUPER_ADMIN'"));
        assertEquals(0L,jdbcLong("SELECT COUNT(*) FROM sys_staff_role sr JOIN sys_role r ON r.id=sr.role_id WHERE sr.staff_id=2 AND r.code IN('ADMIN','SUPER_ADMIN')"));
        exec("INSERT INTO product(id,code,name,main_unit,suggested_retail_price,avg_cost_price,status) VALUES(130,'CREDIT-P130','原单账款验收农资','袋',10,1,1)");
        exec("INSERT INTO product_unit(id,product_id,unit_name,base_quantity_per_unit,is_base_unit,allow_purchase,allow_sale,allow_inventory,allow_transfer,status) VALUES(1301,130,'袋',1,1,1,1,1,1,1)");
        for(long storeId:List.of(1L,2L)) {
            var inbound=new com.agri_system.application.inventory.dto.command.InventoryInCommand();
            inbound.setStoreId(storeId);inbound.setProductId(130L);inbound.setUnit("袋");inbound.setBatchNo("CREDIT-STOCK-"+storeId);
            inbound.setQuantity(new BigDecimal("500"));inbound.setCostPrice(BigDecimal.ONE);inventoryApplicationService.inbound(inbound,1L);
        }
        for(int customerId=600;customerId<=625;customerId++) exec("INSERT INTO customer(id,code,name,phone,type,status,credit_limit) VALUES("+customerId+",'CREDIT-C"+customerId+"','原单账款客户"+customerId+"','13800000"+customerId+"',1,1,1000)");
    }

    private long creditSale(E2EToken token,long customerId,long staffId,String quantity,String cash,String debt) throws Exception {
        POSOrderCreateCommand command=posSale(130L,quantity,"袋","10",false,null,cash);
        command.setCustomerId(customerId);command.setStaffId(staffId);
        if(new BigDecimal(debt).signum()>0) {
            if(new BigDecimal(cash).signum()==0) command.setPaymentType(6);
            else { command.setPaymentType(10);command.setPayments(List.of(paymentTender(1,cash),paymentTender(6,debt))); }
        }
        return createPOSViaHttp(token,command);
    }
    private long creditItem(long orderId) { return jdbcLong("SELECT id FROM order_item WHERE order_id="+orderId+" AND product_id=130"); }
    private JsonNode creditPreview(E2EToken token,long orderId,String quantity) throws Exception {
        JsonNode result=postCreditJson(token,"/api/admin/orders/"+orderId+"/refund-preview",
                Map.of("items",List.of(Map.of("orderItemId",creditItem(orderId),"quantity",new BigDecimal(quantity)))));
        requireCreditSuccess(result);assertTrue(!result.path("quoteId").asText().isBlank(),result.toString());return result;
    }
    private void assertPreview(JsonNode quote,String amount,String reduction,String cash) {
        assertDecimal("预览成交冲销",amount,quote.path("refundAmount").decimalValue());
        assertDecimal("预览冲欠款",reduction,quote.path("creditReductionAmount").decimalValue());
        assertDecimal("预览退已收款",cash,quote.path("cashRefundAmount").decimalValue());
    }
    private Map<String,Object> creditReturnBody(long orderId,JsonNode quote) {
        List<Map<String,Object>> items=new ArrayList<>();
        for(JsonNode line:quote.path("items")) items.add(Map.of("orderItemId",line.path("orderItemId").asLong(),
                "quantity",line.path("quantity").decimalValue(),"refundAmount",line.path("refundAmount").decimalValue()));
        return Map.of("orderId",orderId,"quoteId",quote.path("quoteId").asText(),"refundAmount",quote.path("refundAmount").decimalValue(),
                "reason","原单账款闭环退货","items",items);
    }
    private long creditReturn(E2EToken token,long orderId,JsonNode quote) throws Exception {
        JsonNode result=postCreditJson(token,"/api/admin/after-sales",creditReturnBody(orderId,quote));
        assertTrue(result.isIntegralNumber()&&result.asLong()>0,result.toString());return result.asLong();
    }
    private JsonNode repay(E2EToken token,long customerId,String amount,int method,String requestKey,Long storeId,Long createdBy) throws Exception {
        Map<String,Object> body=new LinkedHashMap<>();body.put("customerId",customerId);body.put("amount",new BigDecimal(amount));
        body.put("paymentMethod",method);body.put("requestKey",requestKey);body.put("transactionId","EXT-"+requestKey);
        if(storeId!=null) body.put("storeId",storeId);if(createdBy!=null) body.put("createdBy",createdBy);
        return postCreditJson(token,"/api/admin/credit/repay",body);
    }
    private JsonNode postCreditJson(E2EToken token,String url,Object body) throws Exception {
        String response=mockMvc.perform(post(url).header(token.name(),token.value()).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return response.isBlank() ? objectMapper.getNodeFactory().nullNode() : objectMapper.readTree(response);
    }
    private static boolean creditSuccess(JsonNode body) { return !body.has("code") || body.path("code").asInt()==200; }
    private JsonNode requireCreditSuccess(JsonNode body) { assertTrue(creditSuccess(body),body.toString());return body; }
    private void assertCreditRejected(JsonNode body) {
        assertEquals(10001,body.path("code").asInt(),"应由业务守卫拒绝，不得将死锁或500当验收通过："+body);
    }
    @FunctionalInterface private interface CreditHttpAction { JsonNode execute(int index) throws Exception; }
    private List<JsonNode> raceCreditHttp(CreditHttpAction action) throws Exception {
        var barrier=new java.util.concurrent.CyclicBarrier(2);var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        List<java.util.concurrent.Future<JsonNode>> futures=new ArrayList<>();
        try {
            for(int i=0;i<2;i++) { final int index=i;futures.add(pool.submit(()->{barrier.await(10,java.util.concurrent.TimeUnit.SECONDS);return action.execute(index);})); }
            List<JsonNode> responses=new ArrayList<>();for(var future:futures) responses.add(future.get(35,java.util.concurrent.TimeUnit.SECONDS));return responses;
        } finally { pool.shutdownNow(); }
    }
    private void assertCreditState(long orderId,String cash,String debt,String paid,String reduction,String refunded,String remaining) {
        BigDecimal n=jdbcDecimal("SELECT pay_amount FROM `order` WHERE id="+orderId);
        BigDecimal c=jdbcDecimal("SELECT COALESCE(SUM(amount),0) FROM order_payment WHERE order_id="+orderId+" AND payment_type IN(1,2,3,4,8)");
        BigDecimal b=jdbcDecimal("SELECT COALESCE(SUM(amount),0) FROM order_payment WHERE order_id="+orderId+" AND payment_type=6");
        BigDecimal p=jdbcDecimal("SELECT COALESCE(SUM(paid_amount),0) FROM credit_account WHERE order_id="+orderId);
        BigDecimal d=jdbcDecimal("SELECT COALESCE(SUM(remaining_amount),0) FROM credit_account WHERE order_id="+orderId);
        BigDecimal w=jdbcDecimal("SELECT COALESCE(SUM(credit_reduction_amount),0) FROM after_sales WHERE order_id="+orderId+" AND status=50");
        BigDecimal f=jdbcDecimal("SELECT COALESCE(SUM(cash_refund_amount),0) FROM after_sales WHERE order_id="+orderId+" AND status=50");
        BigDecimal r=jdbcDecimal("SELECT COALESCE(SUM(refund_amount),0) FROM after_sales WHERE order_id="+orderId+" AND status=50");
        assertDecimal("原非赊销收款C",cash,c);assertDecimal("原赊销B",debt,b);assertDecimal("累计实际还款P不可因退还款而减少",paid,p);
        assertDecimal("累计冲欠款W",reduction,w);assertDecimal("累计退已收款F",refunded,f);assertDecimal("原单剩余欠款D",remaining,d);
        assertDecimal("N=C+B",n.toPlainString(),c.add(b));
        assertDecimal("D=B-P-W",d.toPlainString(),b.subtract(p).subtract(w));
        assertDecimal("R=W+F",r.toPlainString(),w.add(f));
        assertDecimal("N-R=(C+P-F)+D",n.subtract(r).toPlainString(),c.add(p).subtract(f).add(d));
        BigDecimal paidSources=jdbcDecimal("SELECT COALESCE(SUM(l.amount),0) FROM credit_repayment_log l JOIN credit_account a ON a.id=l.credit_account_id WHERE a.order_id="+orderId);
        assertDecimal("实际还款来源等于账户paid_amount",p.toPlainString(),paidSources);
        BigDecimal sourceRefund=jdbcDecimal("SELECT (SELECT COALESCE(SUM(refunded_amount),0) FROM order_payment WHERE order_id="+orderId+")"
                +"+(SELECT COALESCE(SUM(l.refunded_amount),0) FROM credit_repayment_log l JOIN credit_account a ON a.id=l.credit_account_id WHERE a.order_id="+orderId+")");
        assertDecimal("支付和还款来源已退counter等于实际退已收F",f.toPlainString(),sourceRefund);
        assertEquals(0L,jdbcLong("SELECT COUNT(*) FROM order_payment WHERE order_id="+orderId+" AND (refunded_amount<0 OR refunded_amount>amount)"));
        assertEquals(0L,jdbcLong("SELECT COUNT(*) FROM credit_repayment_log l JOIN credit_account a ON a.id=l.credit_account_id WHERE a.order_id="+orderId+" AND (l.refunded_amount<0 OR l.refunded_amount>l.amount)"));
        log("[原单资金] order="+orderId+" N="+n+" C="+c+" B="+b+" P="+p+" W="+w+" F="+f+" R="+r+" D="+d+" 四式及来源counter守恒");
    }
    private void assertOrderReturned(long orderId,String quantity) {
        BigDecimal actual=jdbcDecimal("SELECT COALESCE(SUM(m.base_quantity),0) FROM inventory_movement m JOIN order_item_batch_allocation a ON a.id=m.reference_id JOIN order_item oi ON oi.id=a.order_item_id"
                +" WHERE m.movement_type='RETURN' AND m.reference_type='SALE_ALLOCATION' AND oi.order_id="+orderId);
        assertDecimal("原单实际回库数量",quantity,actual);
        assertDecimal("退货记录数量等于原批次回库",quantity,jdbcDecimal("SELECT COALESCE(SUM(i.quantity),0) FROM after_sales_item i JOIN after_sales a ON a.id=i.after_sales_id WHERE a.order_id="+orderId+" AND a.status=50"));
        assertEquals(0L,jdbcLong("SELECT COUNT(*) FROM inventory_movement m JOIN order_item_batch_allocation a ON a.id=m.reference_id JOIN order_item oi ON oi.id=a.order_item_id"
                +" WHERE m.movement_type='RETURN' AND m.reference_type='SALE_ALLOCATION' AND oi.order_id="+orderId+" AND (m.batch_id<>a.batch_id OR m.unit_cost<>a.batch_unit_cost)"),"所有退货沿原销售批次及原成本");
    }
    private CreditFacts creditFacts() {
        return new CreditFacts(pricingFacts(),jdbcLong("SELECT COUNT(*) FROM credit_account"),jdbcLong("SELECT COUNT(*) FROM credit_repayment_log"),
                jdbcDecimal("SELECT COALESCE(SUM(paid_amount),0) FROM credit_account"),jdbcDecimal("SELECT COALESCE(SUM(remaining_amount),0) FROM credit_account"),
                jdbcDecimal("SELECT COALESCE(SUM(refunded_amount),0) FROM order_payment"),jdbcDecimal("SELECT COALESCE(SUM(refunded_amount),0) FROM credit_repayment_log"),
                jdbcDecimal("SELECT COALESCE(SUM(credit_reduction_amount),0) FROM after_sales"),jdbcDecimal("SELECT COALESCE(SUM(cash_refund_amount),0) FROM after_sales"),
                jdbcDecimal("SELECT COALESCE(SUM(quantity),0) FROM after_sales_item"),jdbcLong("SELECT COUNT(*) FROM wallet_transaction"),jdbcDecimal("SELECT COALESCE(SUM(balance),0) FROM customer_wallet"));
    }
    private record CreditFacts(PricingFacts trading,long accounts,long repayments,BigDecimal paid,BigDecimal debt,
            BigDecimal paymentRefunded,BigDecimal repaymentRefunded,BigDecimal reduction,BigDecimal cashRefund,
            BigDecimal returnedQuantity,long walletTransactions,BigDecimal walletBalance) {}
    private void assertUnknownHistoricalFundingRemainsUnknown(E2EToken manager) throws Exception {
        // 审核是第一次回库入口，必须在任何状态/库存写之前拒绝未核实的原资金。
        exec("INSERT INTO customer_wallet(customer_id,wallet_type,balance) VALUES(620,1,100)");
        var pendingOrder=new com.agri_system.application.order.dto.command.OrderCreateCommand();pendingOrder.setStoreId(1L);pendingOrder.setOrderType(2);pendingOrder.setCustomerId(620L);
        var pendingMaterial=new OrderItemDTO();pendingMaterial.setProductId(130L);pendingMaterial.setQuantity(new BigDecimal("10"));pendingMaterial.setUnit("袋");pendingOrder.setItems(List.of(pendingMaterial));
        long approvalOrder=orderApplicationService.createOrder(pendingOrder,620L);
        var pendingPay=new com.agri_system.application.order.dto.command.OrderPayCommand();pendingPay.setOrderId(approvalOrder);pendingPay.setUseBalance(true);
        assertNull(orderApplicationService.payOrder(pendingPay,620L));
        var pendingApply=new com.agri_system.application.after_sales.dto.command.ClientApplyCommand();pendingApply.setOrderId(approvalOrder);pendingApply.setReason("原资金未知不可审核回库");
        long approval=afterSalesApplicationService.applyFromClient(pendingApply,620L);
        exec("UPDATE order_payment SET refunded_amount=NULL WHERE order_id="+approvalOrder);
        CreditFacts beforeApproval=creditFacts();
        assertCreditRejected(postCreditJson(manager,"/api/admin/after-sales/"+approval+"/audit",Map.of("approved",true,"remark","未知资金应拒绝")));
        assertEquals(beforeApproval,creditFacts(),"审核失败不得改售后状态、提前回库或取消原订单");
        assertEquals(10L,jdbcLong("SELECT status FROM after_sales WHERE id="+approval));
        assertEquals(3L,jdbcLong("SELECT order_status FROM `order` WHERE id="+approvalOrder));
        assertEquals(0L,jdbcLong("SELECT COUNT(*) FROM inventory_movement m JOIN order_item_batch_allocation a ON a.id=m.reference_id JOIN order_item oi ON oi.id=a.order_item_id WHERE m.movement_type='RETURN' AND m.reference_type='SALE_ALLOCATION' AND oi.order_id="+approvalOrder));

        long oldPayment=creditSale(manager,611,1,"10","100","0");
        exec("UPDATE order_payment SET refunded_amount=NULL WHERE order_id="+oldPayment);
        assertNull(paymentRepository.findByOrderId(oldPayment).get(0).getRefundedAmount(),"历史支付NULL不能被MyBatis变成0");
        CreditFacts oldFacts=creditFacts();
        assertCreditRejected(postCreditJson(manager,"/api/admin/orders/"+oldPayment+"/refund-preview",Map.of("items",List.of(Map.of("orderItemId",creditItem(oldPayment),"quantity",1)))));
        assertEquals(oldFacts,creditFacts(),"未知历史支付金额不能重新开放自动退款");

        long oldRepayment=creditSale(manager,612,1,"10","0","100");
        long account=jdbcLong("SELECT id FROM credit_account WHERE order_id="+oldRepayment);
        exec("UPDATE credit_account SET paid_amount=40,remaining_amount=60 WHERE id="+account);
        exec("INSERT INTO credit_repayment_log(repayment_no,customer_id,store_id,credit_account_id,amount,payment_method,refunded_amount) VALUES('LEGACY-UNKNOWN-40',612,1,"+account+",40,4,NULL)");
        assertNull(repaymentRepository.findByCreditAccountIdForUpdate(account).get(0).getRefundedAmount(),"历史还款NULL不能猜为0");
        oldFacts=creditFacts();
        assertCreditRejected(postCreditJson(manager,"/api/admin/orders/"+oldRepayment+"/refund-preview",Map.of("items",List.of(Map.of("orderItemId",creditItem(oldRepayment),"quantity",1)))));
        assertEquals(oldFacts,creditFacts());

        long unallocatedOrder=creditSale(manager,617,1,"10","0","100");
        exec("UPDATE credit_account SET paid_amount=40,remaining_amount=60 WHERE order_id="+unallocatedOrder);
        exec("INSERT INTO credit_repayment_log(repayment_no,customer_id,store_id,amount,payment_method) VALUES('LEGACY-UNALLOCATED-40',617,1,40,1)");
        var unallocated=repaymentRepository.listByCustomer(617L).get(0);
        assertNull(unallocated.getCreditAccountId());assertNull(unallocated.getRequestKey());assertNull(unallocated.getRefundedAmount());
        oldFacts=creditFacts();
        assertCreditRejected(postCreditJson(manager,"/api/admin/orders/"+unallocatedOrder+"/refund-preview",Map.of("items",List.of(Map.of("orderItemId",creditItem(unallocatedOrder),"quantity",1)))));
        assertCreditRejected(repay(manager,617,"10",1,"CREDIT-UNKNOWN-REPAY",null,null));
        assertEquals(oldFacts,creditFacts(),"缺原单分配的历史还款不能猜归属后继续退款或收款");

        long oldReturn=creditSale(manager,613,1,"10","100","0");
        exec("INSERT INTO after_sales(after_sales_no,order_id,customer_id,store_id,type,status,refund_amount,credit_reduction_amount,cash_refund_amount) VALUES('LEGACY-UNKNOWN-RETURN',"+oldReturn+",613,1,2,50,10,NULL,NULL)");
        var unknown=afterSalesRepository.findByOrderId(oldReturn).get(0);
        assertNull(unknown.getCreditReductionAmount());assertNull(unknown.getCashRefundAmount(),"历史售后资金拆分必须保留未知");
        assertNull(unknown.getRefundQuoteId(),"旧售后不能猜测补造退款请求身份");
        assertNull(unknown.getRefundRequestHash(),"旧售后未知请求摘要由真实MyBatis保留NULL");
        oldFacts=creditFacts();
        assertCreditRejected(postCreditJson(manager,"/api/admin/orders/"+oldReturn+"/refund-preview",Map.of("items",List.of(Map.of("orderItemId",creditItem(oldReturn),"quantity",1)))));
        assertEquals(oldFacts,creditFacts());
    }

    /** POS券核销与收款使用真实服务/事务，钱包、券、授权及库存均直接核账。 */
    private void assertPosCouponPaymentChain() throws Exception {
        exec("INSERT INTO product(id,code,name,main_unit,suggested_retail_price,avg_cost_price,status) VALUES(120,'PAY-P120','收款回归商品','袋',100,10,1)");
        exec("INSERT INTO product_unit(id,product_id,unit_name,base_quantity_per_unit,is_base_unit,allow_purchase,allow_sale,allow_inventory,allow_transfer,status) VALUES(1201,120,'袋',1,1,1,1,1,1,1)");
        var inbound = new com.agri_system.application.inventory.dto.command.InventoryInCommand();
        inbound.setStoreId(1L); inbound.setProductId(120L); inbound.setUnit("袋");
        inbound.setBatchNo("POS-PAYMENT-STOCK"); inbound.setQuantity(new BigDecimal("20")); inbound.setCostPrice(new BigDecimal("10"));
        inventoryApplicationService.inbound(inbound,1L);
        exec("INSERT INTO customer(id,code,name,phone,type,status) VALUES(550,'PAY-C550','支付回归客户','13800000550',1,1),(551,'PAY-C551','专用金回归客户','13800000551',1,1)");
        exec("INSERT INTO customer_wallet(customer_id,wallet_type,balance) VALUES(550,1,200),(550,2,0),(551,1,200),(551,2,5)");
        for (int id = 9001; id <= 9012; id++) {
            exec("INSERT INTO customer_coupon(id,customer_id,name,amount,start_time,end_time,status) VALUES(" + id + ","
                    + (id == 9005 ? 551 : 550) + ",'POS收款测试券'," + (id == 9006 ? 100 : 10)
                    + ",DATE_SUB(NOW(),INTERVAL 1 DAY),DATE_ADD(NOW(),INTERVAL 1 DAY),1)");
        }
        exec("INSERT INTO wallet_auth_code(customer_id,auth_code,amount,status,expire_at) VALUES(550,'PAY090',90,1,DATE_ADD(NOW(),INTERVAL 1 DAY)),"
                + "(551,'PAY085',85,1,DATE_ADD(NOW(),INTERVAL 1 DAY)),(550,'PAY050',50,1,DATE_ADD(NOW(),INTERVAL 1 DAY))");
        E2EToken manager = loginToken(1L);

        var cash = couponSale(550L,9001L,1); long cashOrder = createPOSViaHttp(manager,cash);
        assertDecimal("现金减券只收90元","90",paymentAmount(cashOrder,1));
        assertDecimal("券只记录10元","10",paymentAmount(cashOrder,5));
        assertDecimal("订单实付90元","90",jdbcDecimal("SELECT pay_amount FROM `order` WHERE id="+cashOrder));

        var wallet = couponSale(550L,9002L,4); wallet.setWalletAuthCode("PAY090");
        long walletOrder = orderApplicationService.createPOSOrder(wallet,1L);
        assertDecimal("钱包200支付净额90后剩110","110",walletBalance(550L,1));
        assertDecimal("钱包流水恰好90","90",paymentAmount(walletOrder,4));
        assertEquals(1L,jdbcLong("SELECT COUNT(*) FROM wallet_transaction WHERE wallet_type=1 AND reference_id="+walletOrder+" AND reference_type='ORDER'"));
        assertEquals(2L,jdbcLong("SELECT status FROM wallet_auth_code WHERE auth_code='PAY090'"));

        var wechat = couponSale(550L,9003L,2); long wechatOrder = createPOSViaHttp(manager,wechat);
        assertDecimal("微信加券仍收净额90","90",paymentAmount(wechatOrder,2));
        var mixed = couponSale(550L,9004L,10);
        mixed.setPayments(List.of(paymentTender(1,"40"),paymentTender(2,"50")));
        long mixedOrder = createPOSViaHttp(manager,mixed);
        assertDecimal("混合现金40","40",paymentAmount(mixedOrder,1));
        assertDecimal("混合微信50","50",paymentAmount(mixedOrder,2));
        assertEquals(3L,jdbcLong("SELECT COUNT(*) FROM order_payment WHERE order_id="+mixedOrder));

        // 原商品允许专用金，实际5元不能记成商品上限100元。
        exec("UPDATE product SET allow_special_fund=1 WHERE id=120");
        var special = couponSale(551L,9005L,4); special.setUseSpecialFund(true); special.setWalletAuthCode("PAY085");
        long specialOrder = orderApplicationService.createPOSOrder(special,1L);
        assertDecimal("专用金实际只扣5元","0",walletBalance(551L,2));
        assertDecimal("本金实际只扣85元","115",walletBalance(551L,1));
        assertDecimal("专用金流水等于实扣","5",paymentAmount(specialOrder,8));
        assertDecimal("本金流水等于实扣","85",paymentAmount(specialOrder,4));

        var free = couponSale(550L,9006L,4); long freeOrder = orderApplicationService.createPOSOrder(free,1L);
        assertDecimal("券全额抵扣无钱包扣减","110",walletBalance(550L,1));
        assertEquals(1L,jdbcLong("SELECT COUNT(*) FROM order_payment WHERE order_id="+freeOrder));
        assertDecimal("券全额抵扣实付为0","0",jdbcDecimal("SELECT pay_amount FROM `order` WHERE id="+freeOrder));

        var mixedWallet = couponSale(550L,9007L,10); mixedWallet.setWalletAuthCode("PAY050");
        mixedWallet.setPayments(List.of(paymentTender(1,"40"),paymentTender(4,"50")));
        long mixedWalletOrder = orderApplicationService.createPOSOrder(mixedWallet,1L);
        assertDecimal("混合钱包只扣50元","60",walletBalance(550L,1));
        assertDecimal("混合钱包流水50元","50",paymentAmount(mixedWalletOrder,4));
        assertEquals(2L,jdbcLong("SELECT status FROM wallet_auth_code WHERE auth_code='PAY050'"));
        var before = pricingFacts(); long walletTransactions = jdbcLong("SELECT COUNT(*) FROM wallet_transaction");
        var reused = couponSale(550L,9008L,10); reused.setWalletAuthCode("PAY050");
        reused.setPayments(List.of(paymentTender(1,"40"),paymentTender(4,"50")));
        assertThrows(RuntimeException.class,()->orderApplicationService.createPOSOrder(reused,1L));
        assertEquals(before,pricingFacts(),"已用验证码必须回滚订单/券流水/库存");
        assertDecimal("已用码不能再次扣钱包","60",walletBalance(550L,1));
        assertEquals(walletTransactions,jdbcLong("SELECT COUNT(*) FROM wallet_transaction"));
        assertEquals(1L,jdbcLong("SELECT status FROM customer_coupon WHERE id=9008"));

        for (var tenders : List.of(List.of(paymentTender(1,"150"),paymentTender(6,"-50")),
                List.of(paymentTender(5,"100")),List.of(paymentTender(8,"100")),List.of(paymentTender(999,"100")),
                List.of(paymentTender(1,"100"),paymentTender(2,"0")),List.of(paymentTender(1,"99.999"),paymentTender(2,"0.001")))) {
            var invalid = posSale(120L,"1","袋","100",false,null,"100"); invalid.setPaymentType(10); invalid.setPayments(tenders);
            assertThrows(IllegalArgumentException.class,()->orderApplicationService.createPOSOrder(invalid,1L));
            assertEquals(before,pricingFacts(),"非法支付不得写入任何交易事实");
        }
        var insufficient = couponSale(550L,9009L,1); insufficient.getItems().get(0).setQuantity(new BigDecimal("100"));
        assertThrows(IllegalArgumentException.class,()->orderApplicationService.createPOSOrder(insufficient,1L));
        assertEquals(before,pricingFacts(),"缺货不能保留券核销或收款");
        assertEquals(1L,jdbcLong("SELECT status FROM customer_coupon WHERE id=9009"));

        assertSameCouponCannotBeUsedConcurrently();
        log("POS券加现金/微信/余额/混合、实际钱包拆分、授权防重、缺货及非法支付回滚验收通过");
    }

    /** 持有券锁让两个请求都停在券写入/当前读处，再释放，稳定复现旧的双核销。 */
    private void assertSameCouponCannotBeUsedConcurrently() throws Exception {
        exec("INSERT INTO `order`(id,order_no,store_id,customer_id,order_type,order_status,total_amount,pay_amount) VALUES(9501,'COUPON-CONCURRENT-1',1,550,1,1,100,100),(9502,'COUPON-CONCURRENT-2',1,550,1,1,100,100)");
        var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        try (Connection blocker = DriverManager.getConnection(jdbcUrl,dbUser,dbPassword)) {
            blocker.setAutoCommit(false);
            try (Statement statement = blocker.createStatement()) { statement.executeQuery("SELECT id FROM customer_coupon WHERE id=9010 FOR UPDATE").close(); }
            var first = executor.submit(() -> tryApplyCoupon(9501L,9010L));
            var second = executor.submit(() -> tryApplyCoupon(9502L,9010L));
            try {
                long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
                long waiting = 0;
                while (System.nanoTime() < deadline) {
                    waiting = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM performance_schema.data_lock_waits w JOIN performance_schema.data_locks r ON r.ENGINE_LOCK_ID=w.REQUESTING_ENGINE_LOCK_ID WHERE r.OBJECT_SCHEMA=? AND r.OBJECT_NAME='customer_coupon'",Long.class,DB_NAME);
                    if (waiting >= 2) break;
                    Thread.sleep(50);
                }
                assertTrue(waiting >= 2,"并发复现需要两个请求实际等待同一优惠券锁");
            } finally { blocker.commit(); }
            int successes = (first.get(15,java.util.concurrent.TimeUnit.SECONDS) ? 1 : 0) + (second.get(15,java.util.concurrent.TimeUnit.SECONDS) ? 1 : 0);
            assertEquals(1,successes,"同一优惠券两个并发订单只能成交一个");
            assertEquals(1L,jdbcLong("SELECT COUNT(*) FROM order_payment WHERE coupon_id=9010"));
        } finally { executor.shutdownNow(); }
    }

    private boolean tryApplyCoupon(Long orderId,Long couponId) {
        try { orderPaymentService.applyCouponToOrder(orderRepository.findById(orderId).orElseThrow(),550L,couponId); return true; }
        catch (IllegalArgumentException invalid) { return false; }
    }
    private POSOrderCreateCommand couponSale(Long customerId,Long couponId,int method) {
        var command = posSale(120L,"1","袋","100",false,null,"90");
        command.setCustomerId(customerId); command.setCouponId(couponId); command.setPaymentType(method); return command;
    }
    private com.agri_system.application.order.dto.command.PaymentDetailDTO paymentTender(int type,String amount) {
        var tender = new com.agri_system.application.order.dto.command.PaymentDetailDTO();
        tender.setPaymentType(type); tender.setAmount(new BigDecimal(amount)); return tender;
    }
    private BigDecimal paymentAmount(long orderId,int type) { return jdbcDecimal("SELECT COALESCE(SUM(amount),0) FROM order_payment WHERE order_id="+orderId+" AND payment_type="+type); }
    private BigDecimal walletBalance(long customerId,int type) { return jdbcDecimal("SELECT balance FROM customer_wallet WHERE customer_id="+customerId+" AND wallet_type="+type); }

    /** 报价、权限与成交价走真实 HTTP/数据库，不用客户端价格或模拟权限替代验收。 */
    private void assertUnifiedPricingBusinessChain() throws Exception {
        exec("INSERT INTO staff(id,code,name,store_id,role,status) VALUES(2,'PRICE-CASHIER','普通收银员',1,'STAFF',1)");
        exec("INSERT INTO customer(id,code,name,phone,type,vip_level,vip_end_date,status) VALUES"
                + "(501,'PRICE-MEMBER','有效会员','13800000501',2,4,DATE_ADD(CURRENT_DATE,INTERVAL 30 DAY),1),"
                + "(502,'PRICE-EXPIRED','过期会员','13800000502',2,4,DATE_SUB(CURRENT_DATE,INTERVAL 1 DAY),1)");
        exec("INSERT INTO product(id,code,name,main_unit,aux_unit,main_to_aux_ratio,suggested_retail_price,vip_price,min_price,avg_cost_price,status) VALUES"
                + "(105,'PRICE-P105','基准单位定价验收','箱','瓶',3,10,9,8,2,1),"
                + "(106,'PRICE-P106','未配置零售价商品','瓶',NULL,NULL,NULL,5,6,2,1)");
        exec("INSERT INTO product_unit(id,product_id,unit_name,base_quantity_per_unit,is_base_unit,allow_purchase,allow_sale,allow_inventory,allow_transfer,status) VALUES"
                + "(1051,105,'瓶',1,1,1,1,1,1,1),(1052,105,'箱',3,0,1,1,1,1,1),"
                + "(1053,105,'件',12,0,1,1,1,1,1),(1061,106,'瓶',1,1,1,1,1,1,1)");
        for (long productId : List.of(105L, 106L)) {
            var inbound = new com.agri_system.application.inventory.dto.command.InventoryInCommand();
            inbound.setStoreId(1L); inbound.setProductId(productId); inbound.setUnit("瓶");
            inbound.setBatchNo("PRICE-" + productId); inbound.setQuantity(new BigDecimal("60"));
            inbound.setCostPrice(new BigDecimal("2"));
            inventoryApplicationService.inbound(inbound, 1L);
        }

        E2EToken manager = loginToken(1L);
        E2EToken cashier = loginToken(2L);
        JsonNode bottle = priceQuote(cashier, "瓶", null);
        assertDecimal("普通收银员可以获取基准单位零售价", "10", bottle.path("suggestedUnitPrice").decimalValue());
        assertDecimal("最低价按同一基准单位返回", "8", bottle.path("referenceFloor").decimalValue());
        assertEquals("RETAIL", bottle.path("ruleVersion").asText());
        assertDecimal("主单位按持久化系数换算", "30", priceQuote(cashier, "箱", null).path("suggestedUnitPrice").decimalValue());
        JsonNode retailCase = priceQuote(cashier, "件", null);
        assertDecimal("第三销售单位按12瓶换算，不能按主辅比例3换算", "120", retailCase.path("suggestedUnitPrice").decimalValue());
        assertDecimal("第三单位最低价必须同步换算", "96", retailCase.path("referenceFloor").decimalValue());
        JsonNode memberCase = priceQuote(cashier, "件", 501L);
        assertDecimal("有效会员按基准单位会员价换算", "108", memberCase.path("suggestedUnitPrice").decimalValue());
        assertDecimal("会员报价与最低价必须使用相同销售单位", "96", memberCase.path("referenceFloor").decimalValue());
        assertTrue(!memberCase.path("belowReference").asBoolean(), "正常会员价应高于同单位最低价");
        assertEquals("MEMBER", memberCase.path("ruleVersion").asText());
        assertDecimal("过期会员恢复零售价", "120", priceQuote(cashier, "件", 502L).path("suggestedUnitPrice").decimalValue());

        JsonNode forgedMember = objectMapper.readTree(mockMvc.perform(get("/api/admin/products/105/price-quote")
                        .header(cashier.name(), cashier.value()).param("saleUnit", "瓶")
                        .param("customerType", "2").param("vipLevel", "4"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data");
        assertDecimal("客户端伪造会员类型不能改变报价", "10", forgedMember.path("suggestedUnitPrice").decimalValue());

        PricingFacts before = pricingFacts();
        POSOrderCreateCommand faultyQuote = posSale(105L, "1", "瓶", "10.00", false, null, "16.00");
        OrderItemDTO missingRetail = posSale(106L, "1", "瓶", "6.00", false, "试图用底价替代报价", "6.00").getItems().get(0);
        faultyQuote.setItems(List.of(faultyQuote.getItems().get(0), missingRetail));
        assertPOSRejected(manager, faultyQuote, 10001, "未配置有效零售价");
        assertEquals(before, pricingFacts(), "报价失败不能采用前端价，不能留下订单、收款、审计或库存扣减");

        POSOrderCreateCommand normalSale = posSale(105L, "1", "瓶", "10.00", false, null, "10.00");
        normalSale.setStaffId(2L);
        long ordinaryOrder = createPOSViaHttp(cashier, normalSale);
        assertDecimal("没有议价权限仍能按正常报价开单", "10", jdbcDecimal("SELECT unit_price FROM order_item WHERE order_id=" + ordinaryOrder));
        assertDecimal("正常开单只扣一瓶", "59", jdbcDecimal("SELECT SUM(base_quantity) FROM inventory_batch_balance WHERE product_id=105"));
        assertEquals(0L, jdbcLong("SELECT COUNT(*) FROM order_item_price_audit WHERE product_id=105"));

        before = pricingFacts();
        POSOrderCreateCommand deniedNegotiation = posSale(105L, "1", "瓶", "9.50", false, "老客户议价", "9.50");
        deniedNegotiation.setStaffId(2L);
        assertPOSRejected(cashier, deniedNegotiation, 10004, "order:price:override");
        assertEquals(before, pricingFacts(), "没有议价权限时整单拒绝且不扣库存/收款");

        POSOrderCreateCommand deniedDiscount = posSale(105L, "1", "瓶", "10.00", false, null, "1.00");
        deniedDiscount.setStaffId(2L); deniedDiscount.setDiscountAmount(new BigDecimal("9.00"));
        deniedDiscount.setRemark("整单优惠");
        assertPOSRejected(cashier, deniedDiscount, 10004, "order:price:override");
        assertEquals(before, pricingFacts(), "整单优惠不能绕过议价权限");

        POSOrderCreateCommand staleQuote = posSale(105L, "1", "瓶", "10.00", false, null, "10.00");
        staleQuote.setStaffId(2L); staleQuote.getItems().get(0).setQuotedUnitPrice(new BigDecimal("9.00"));
        assertPOSRejected(cashier, staleQuote, 10001, "商品价格已变化");
        assertEquals(before, pricingFacts(), "结算报价变化时须重新确认金额");

        POSOrderCreateCommand missingReason = posSale(105L, "1", "件", "95.00", false, null, "95.00");
        assertPOSRejected(manager, missingReason, 10001, "议价原因");
        assertEquals(before, pricingFacts(), "即使有权限也必须填写议价原因");

        POSOrderCreateCommand negotiated = posSale(105L, "1", "件", "95.00", false, "老客户整件优惠，确认低于底价", "95.00");
        long negotiatedOrder = createPOSViaHttp(manager, negotiated);
        long negotiatedItem = jdbcLong("SELECT id FROM order_item WHERE order_id=" + negotiatedOrder);
        assertDecimal("订单保存实际成交单价", "95", jdbcDecimal("SELECT unit_price FROM order_item WHERE id=" + negotiatedItem));
        assertDecimal("订单保存实际成交总额", "95", jdbcDecimal("SELECT total_amount FROM `order` WHERE id=" + negotiatedOrder));
        assertDecimal("整件销售精确扣12瓶", "47", jdbcDecimal("SELECT SUM(base_quantity) FROM inventory_batch_balance WHERE product_id=105"));
        assertEquals(1L, jdbcLong("SELECT COUNT(*) FROM order_item_price_audit WHERE order_item_id=" + negotiatedItem));
        String auditWhere = " FROM order_item_price_audit WHERE order_item_id=" + negotiatedItem;
        assertDecimal("审计保存服务端整件报价", "120", jdbcDecimal("SELECT suggested_unit_price" + auditWhere));
        assertDecimal("审计保存人工成交价", "95", jdbcDecimal("SELECT final_unit_price" + auditWhere));
        assertDecimal("审计保存价差", "-25", jdbcDecimal("SELECT price_difference" + auditWhere));
        assertEquals(1053L, jdbcLong("SELECT sale_unit_id" + auditWhere));
        assertEquals(1L, jdbcLong("SELECT operator_id" + auditWhere));
        assertEquals(1L, jdbcLong("SELECT confirmer_id" + auditWhere));
        assertEquals("老客户整件优惠，确认低于底价", jdbcString("SELECT change_reason" + auditWhere));
        assertTrue(jdbcString("SELECT quote_id" + auditWhere) != null && !jdbcString("SELECT quote_id" + auditWhere).isBlank());

        exec("UPDATE product SET suggested_retail_price=20,vip_price=18,min_price=16 WHERE id=105");
        assertDecimal("商品调价后新报价使用新价", "240", priceQuote(cashier, "件", null).path("suggestedUnitPrice").decimalValue());
        assertDecimal("商品调价不得覆盖历史成交价快照", "95", jdbcDecimal("SELECT unit_price FROM order_item WHERE id=" + negotiatedItem));
        assertDecimal("商品调价不得覆盖历史报价审计", "120", jdbcDecimal("SELECT suggested_unit_price" + auditWhere));
        POSOrderCreateCommand authorizedDiscount = posSale(105L, "1", "瓶", "20.00", false, null, "19.00");
        authorizedDiscount.setDiscountAmount(new BigDecimal("1.00")); authorizedDiscount.setRemark("老客户整单优惠");
        long discountedOrder = createPOSViaHttp(manager, authorizedDiscount);
        assertDecimal("整单优惠单独保存金额", "1", jdbcDecimal("SELECT discount_amount FROM `order` WHERE id=" + discountedOrder));
        assertTrue(jdbcString("SELECT remark FROM `order` WHERE id=" + discountedOrder).contains("老客户整单优惠"));

        log("[价格] 零售/会员/第三单位、底价换算、报价失败回滚、普通收银及授权议价成交快照验收通过");
    }

    private E2EToken loginToken(long staffId) throws Exception {
        JsonNode body = objectMapper.readTree(mockMvc.perform(get("/__e2e__/login").param("staffId", Long.toString(staffId)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        return new E2EToken(body.path("tokenName").asText(), body.path("tokenValue").asText());
    }

    private JsonNode priceQuote(E2EToken token, String unit, Long customerId) throws Exception {
        var request = get("/api/admin/products/105/price-quote").header(token.name(), token.value())
                .param("storeId", "1").param("channel", "POS").param("saleUnit", unit);
        if (customerId != null) request.param("customerId", customerId.toString());
        JsonNode response = objectMapper.readTree(mockMvc.perform(request).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertEquals("200", response.path("code").asText(), response.toString());
        return response.path("data");
    }

    private long createPOSViaHttp(E2EToken token, POSOrderCreateCommand command) throws Exception {
        String response = mockMvc.perform(post("/api/admin/orders/pos").header(token.name(), token.value())
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(command)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode body = objectMapper.readTree(response);
        assertTrue(body.isIntegralNumber() && body.asLong() > 0, "正常开单应返回订单ID: " + response);
        return body.asLong();
    }

    private void assertPOSRejected(E2EToken token, POSOrderCreateCommand command, int code, String reason) throws Exception {
        JsonNode response = objectMapper.readTree(mockMvc.perform(post("/api/admin/orders/pos")
                        .header(token.name(), token.value()).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(command)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertEquals(code, response.path("code").asInt(), response.toString());
        assertTrue(response.path("message").asText().contains(reason), response.toString());
    }

    private PricingFacts pricingFacts() {
        return new PricingFacts(jdbcLong("SELECT COUNT(*) FROM `order`"), jdbcLong("SELECT COUNT(*) FROM order_item"),
                jdbcLong("SELECT COUNT(*) FROM order_payment"), jdbcLong("SELECT COUNT(*) FROM order_item_batch_allocation"),
                movementCount(), jdbcLong("SELECT COUNT(*) FROM order_item_price_audit"),
                jdbcLong("SELECT COUNT(*) FROM after_sales"), jdbcLong("SELECT COUNT(*) FROM after_sales_item"),
                jdbcLong("SELECT COUNT(*) FROM staff_performance_log"), jdbcLong("SELECT COUNT(*) FROM performance_event_record"),
                jdbcDecimal("SELECT SUM(base_quantity) FROM inventory_batch_balance"));
    }

    private record E2EToken(String name, String value) {}
    private record PricingFacts(long orders, long items, long payments, long allocations,
                                long movements, long audits, long afterSales, long afterSalesItems,
                                long performanceLogs, long performanceEvents, BigDecimal balance) {}

    /** 套餐整套开单→物料出库→真实售后退款→库存/提成回冲，所有操作只在本次临时库。 */
    private void assertPackageBusinessChain() throws Exception {
        exec("INSERT INTO product(id,code,name,main_unit,aux_unit,main_to_aux_ratio,suggested_retail_price,min_price,allow_special_fund,avg_cost_price,status) VALUES"
                + "(110,'PACK-P110','套餐第三单位农资','箱','瓶',3,5,1,1,1,1),"
                + "(111,'PACK-P111','套餐种子','袋',NULL,NULL,40,1,0,4,1),"
                + "(113,'PACK-P113','一分套餐主物料','瓶',NULL,NULL,1,NULL,0,0.0001,1),"
                + "(114,'PACK-P114','零元分摊赠品','瓶',NULL,NULL,0.01,NULL,0,0.0001,1)");
        exec("INSERT INTO product_unit(id,product_id,unit_name,base_quantity_per_unit,is_base_unit,allow_purchase,allow_sale,allow_inventory,allow_transfer,status) VALUES"
                + "(1101,110,'瓶',1,1,1,1,1,1,1),(1102,110,'箱',3,0,1,1,1,1,1),"
                + "(1103,110,'件',12,0,1,1,1,1,1),(1111,111,'袋',1,1,1,1,1,1,1),"
                + "(1131,113,'瓶',1,1,1,1,1,1,1),(1141,114,'瓶',1,1,1,1,1,1,1)");
        for (long productId : List.of(110L,111L,113L,114L)) {
            var inbound = new com.agri_system.application.inventory.dto.command.InventoryInCommand();
            inbound.setStoreId(1L); inbound.setProductId(productId); inbound.setUnit(productId == 111L ? "袋" : "瓶");
            inbound.setBatchNo("PACKAGE-STOCK-" + productId);
            inbound.setQuantity(new BigDecimal(productId == 110L ? "200" : "20"));
            inbound.setCostPrice(new BigDecimal(productId == 111L ? "4" : productId >= 113L ? "0.0001" : "1"));
            inventoryApplicationService.inbound(inbound,1L);
        }
        exec("INSERT INTO product_package(id,code,name,suggested_retail_price,vip_price,min_price,status) VALUES"
                + "(201,'PACK-201','单品一百套餐八十',80,72,60,1),(202,'PACK-202','整套缺货回滚',80,NULL,NULL,1),"
                + "(203,'PACK-203','分次退款提成尾分',80,NULL,NULL,1),(204,'PACK-204','零元物料退货',0.01,NULL,NULL,1),"
                + "(205,'PACK-205','三件一元分币退款',1,NULL,NULL,1)");
        exec("INSERT INTO product_package_item(id,package_id,product_id,quantity,unit,sort_order) VALUES"
                + "(2011,201,110,1,'件',1),(2012,201,111,1,'袋',2),"
                + "(2021,202,110,20,'件',1),(2022,202,111,1,'袋',2),"
                + "(2031,203,110,1,'件',1),(2032,203,111,1,'袋',2),"
                + "(2041,204,113,1,'瓶',1),(2042,204,114,1,'瓶',2),"
                + "(2051,205,113,3,'瓶',1),(2052,205,114,1,'瓶',2)");
        exec("INSERT INTO commission_rule(id,legacy_rule_type,rule_category,target_id,calc_mode,calc_value,enabled,status,priority) VALUES"
                + "(201,1,5,201,'FIXED',10,1,1,1),(203,1,5,203,'FIXED',0.01,1,1,1),"
                + "(205,1,5,205,'FIXED',0.02,1,1,1)");
        E2EToken cashier = loginToken(2L);
        E2EToken manager = loginToken(1L);
        JsonNode twoSets = packageQuote(cashier,201L,"2",null);
        assertDecimal("单品合计100不覆盖套餐单价80", "80", twoSets.path("suggestedUnitPrice").decimalValue());
        assertDecimal("两套按套餐价格成交160", "160", twoSets.path("totalAmount").decimalValue());
        assertDecimal("专用金额度来自实际套餐分摊额", "96", twoSets.path("specialFundEligibleAmount").decimalValue());
        assertDecimal("套餐会员价按套报价", "72", packageQuote(cashier,201L,"1",501L).path("suggestedUnitPrice").decimalValue());
        long order = createPOSViaHttp(cashier,packageSale(201L,"2",twoSets,2L));
        assertEquals(2L,jdbcLong("SELECT COUNT(*) FROM order_item WHERE order_id=" + order));
        assertEquals(2L,jdbcLong("SELECT COUNT(*) FROM order_item WHERE order_id=" + order + " AND item_type=1 AND package_group_no IS NOT NULL"));
        assertDecimal("套餐订单权威总额为160", "160", jdbcDecimal("SELECT total_amount FROM `order` WHERE id=" + order));
        assertDecimal("物料分摊额必须精确合计160", "160", jdbcDecimal("SELECT SUM(total_price) FROM order_item WHERE order_id=" + order));
        assertDecimal("两套物料按第三单位扣24瓶", "176", packageStock(110L));
        assertDecimal("两套扣2袋种子", "18", packageStock(111L));
        assertDecimal("固定套餐提成按两套计算一次", "20", commissionSum(order,1));
        assertEquals(2L,jdbcLong("SELECT COUNT(*) FROM order_item WHERE order_id=" + order + " AND package_quantity=2 AND package_unit_price=80 AND package_name='单品一百套餐八十'"));

        JsonNode oneSet = packageQuote(cashier,201L,"1",null);
        exec("UPDATE product_package_item SET quantity=0.5 WHERE id=2011");
        exec("UPDATE product_package_item SET quantity=1.75 WHERE id=2012");
        PricingFacts before = pricingFacts();
        assertPOSRejected(cashier,packageSale(201L,"1",oneSet,2L),10001,"组成已变化");
        assertEquals(before,pricingFacts(),"价格不变但组成变化时旧指纹拒绝整单");
        assertDecimal("旧指纹不能扣第三单位库存", "176", packageStock(110L));
        assertDecimal("旧指纹不能扣种子库存", "18", packageStock(111L));
        exec("UPDATE product_package_item SET quantity=1 WHERE id IN (2011,2012)");
        POSOrderCreateCommand differentGroups = packageSale(201L,"2",packageQuote(cashier,201L,"2",null),2L);
        differentGroups.setItems(List.of(differentGroups.getItems().get(0),packageSale(201L,"1",packageQuote(cashier,201L,"1",null),2L).getItems().get(0)));
        differentGroups.setReceiveAmount(new BigDecimal("240"));
        long groupedOrder = createPOSViaHttp(cashier,differentGroups);
        assertEquals(2L,jdbcLong("SELECT COUNT(DISTINCT package_group_no) FROM order_item WHERE order_id=" + groupedOrder));
        assertDecimal("同套餐不同销售组分别按2套和1套计提", "30", commissionSum(groupedOrder,1));
        assertDecimal("三套准确扣36瓶", "140", packageStock(110L));
        assertDecimal("三套准确扣3袋", "15", packageStock(111L));
        before = pricingFacts();
        assertPOSRejected(cashier,packageSale(202L,"1",packageQuote(cashier,202L,"1",null),2L),10001,"可用库存不足");
        assertEquals(before,pricingFacts(),"套餐任一物料缺货必须回滚订单、支付、审计、分配、库存流水");
        assertDecimal("缺货失败不留下部分出库", "140", packageStock(110L));
        assertDecimal("缺货失败不扣其它物料", "15", packageStock(111L));

        long tailOrder = createPOSViaHttp(cashier,packageSale(203L,"3",packageQuote(cashier,203L,"3",null),2L));
        long cropItem = jdbcLong("SELECT id FROM order_item WHERE order_id=" + tailOrder + " AND product_id=110");
        long seedItem = jdbcLong("SELECT id FROM order_item WHERE order_id=" + tailOrder + " AND product_id=111");
        assertDecimal("三套每套1分提成，总计3分", "0.03", commissionSum(tailOrder,1));
        for (long itemId : List.of(cropItem,seedItem)) {
            String amount = itemId == cropItem ? "48.00" : "32.00";
            for (int i=0;i<3;i++) {
                long returned = returnViaHttp(manager,tailOrder,itemId,"1",amount);
                BigDecimal reversal = commissionSum(tailOrder,4);
                assertTrue(reversal.negate().compareTo(new BigDecimal("0.03")) <= 0,"分次退款不能超扣原提成");
                long movementBefore = movementCount();
                performanceEventListener.onOrderRefunded(new OrderRefundedEvent(tailOrder,new BigDecimal(amount),
                        Map.of(itemId,BigDecimal.ONE),"AFTER_SALES:" + returned));
                assertDecimal("重复退款事件不重复回冲", reversal.toPlainString(), commissionSum(tailOrder,4));
                assertEquals(movementBefore,movementCount());
            }
        }
        assertDecimal("实退完所有物料后精确冲回全部3分提成", "-0.03", commissionSum(tailOrder,4));
        assertDecimal("逐次退款实际金额合计240", "240", jdbcDecimal("SELECT SUM(refund_amount) FROM after_sales WHERE order_id=" + tailOrder + " AND status=50"));
        assertDecimal("套餐退回原库存瓶数", "140", packageStock(110L));
        assertDecimal("套餐退回原库存袋数", "15", packageStock(111L));
        before = pricingFacts();
        assertReturnRejected(manager,tailOrder,cropItem,"1","48.00");
        assertEquals(before,pricingFacts(),"退完后重复实物退货不得再回库/退款");

        JsonNode giftQuote = packageQuote(cashier,204L,"1",null);
        assertDecimal("一分套餐的第二物料允许零元分摊", "0", giftQuote.path("items").get(1).path("totalPrice").decimalValue());
        long giftOrder = createPOSViaHttp(cashier,packageSale(204L,"1",giftQuote,2L));
        long paidItem = jdbcLong("SELECT id FROM order_item WHERE order_id=" + giftOrder + " AND product_id=113");
        long freeItem = jdbcLong("SELECT id FROM order_item WHERE order_id=" + giftOrder + " AND product_id=114");
        returnViaHttp(manager,giftOrder,paidItem,"1","0.01");
        assertEquals(8L,jdbcLong("SELECT order_status FROM `order` WHERE id=" + giftOrder));
        returnViaHttp(manager,giftOrder,freeItem,"1","0.00");
        assertDecimal("全额退款后零元赠品仍能退回库存", "20", packageStock(114L));
        assertDecimal("有金额物料回原库存", "20", packageStock(113L));
        assertEquals(2L,jdbcLong("SELECT COUNT(*) FROM after_sales WHERE order_id=" + giftOrder + " AND status=50"));

        long pennyOrder = createPOSViaHttp(cashier,packageSale(205L,"1",packageQuote(cashier,205L,"1",null),2L));
        long pennyItem = jdbcLong("SELECT id FROM order_item WHERE order_id=" + pennyOrder + " AND product_id=113");
        long pennyGift = jdbcLong("SELECT id FROM order_item WHERE order_id=" + pennyOrder + " AND product_id=114");
        assertDecimal("MySQL必须保存12位物料分摊单价", "0.333333333333", jdbcDecimal("SELECT unit_price FROM order_item WHERE id=" + pennyItem));
        assertDecimal("金额仍以分摊小计1元为准", "1.00", jdbcDecimal("SELECT total_price FROM order_item WHERE id=" + pennyItem));
        returnViaHttp(manager,pennyOrder,pennyItem,"1","0.33");
        returnViaHttp(manager,pennyOrder,pennyItem,"1","0.33");
        returnViaHttp(manager,pennyOrder,pennyItem,"1","0.34");
        assertDecimal("三次实退33/33/34分须精确退完1元", "1.00", jdbcDecimal("SELECT SUM(refund_amount) FROM after_sales WHERE order_id=" + pennyOrder));
        assertDecimal("每次各退一件不能累计多扣2分原提成", "-0.02", commissionSum(pennyOrder,4));
        returnViaHttp(manager,pennyOrder,pennyGift,"1","0.00");
        assertDecimal("分币退款后原实物全部回库", "20", packageStock(113L));
        assertDecimal("分币退款后0元物料也能回库", "20", packageStock(114L));
        JsonNode standardQuote = packageQuote(cashier,201L,"1",null);
        POSOrderCreateCommand deniedPackageDeal = packageSale(201L,"1",standardQuote,2L);
        deniedPackageDeal.getItems().get(0).setUnitPrice(new BigDecimal("78"));
        deniedPackageDeal.getItems().get(0).setPriceChangeReason("整套议价");
        deniedPackageDeal.setReceiveAmount(new BigDecimal("78"));
        before = pricingFacts();
        assertPOSRejected(cashier,deniedPackageDeal,10004,"order:price:override");
        assertEquals(before,pricingFacts(),"套餐议价无权限时不产生任何销售、收款或出库事实");
        POSOrderCreateCommand negotiatedPackage = packageSale(201L,"1",standardQuote,1L);
        negotiatedPackage.getItems().get(0).setUnitPrice(new BigDecimal("78"));
        negotiatedPackage.getItems().get(0).setPriceChangeReason("整套议价");
        negotiatedPackage.setDiscountAmount(new BigDecimal("8")); negotiatedPackage.setRemark("整单优惠");
        negotiatedPackage.setReceiveAmount(new BigDecimal("70"));
        long netOrder = createPOSViaHttp(manager,negotiatedPackage);
        assertDecimal("套餐议价成交快照保留78元", "78",jdbcDecimal("SELECT SUM(total_price) FROM order_item WHERE order_id="+netOrder));
        assertDecimal("整单优惠后实付70元", "70",jdbcDecimal("SELECT pay_amount FROM `order` WHERE id="+netOrder));
        assertEquals(2L,jdbcLong("SELECT COUNT(*) FROM order_item_price_audit WHERE order_item_id IN (SELECT id FROM order_item WHERE order_id="+netOrder+")"));
        long netCrop = jdbcLong("SELECT id FROM order_item WHERE order_id="+netOrder+" AND product_id=110");
        long netSeed = jdbcLong("SELECT id FROM order_item WHERE order_id="+netOrder+" AND product_id=111");
        before = pricingFacts();
        assertReturnRejected(manager,netOrder,netCrop,"1","46.80");
        assertEquals(before,pricingFacts(),"套餐退货不能把原整单优惠再次退成现金");
        returnViaHttp(manager,netOrder,netCrop,"1","42.00");
        returnViaHttp(manager,netOrder,netSeed,"1","28.00");
        assertDecimal("净额退款准确合计原实付70元", "70",jdbcDecimal("SELECT SUM(refund_amount) FROM after_sales WHERE order_id="+netOrder));
        assertDecimal("退货不改原成交物料金额78元", "78",jdbcDecimal("SELECT SUM(total_price) FROM order_item WHERE order_id="+netOrder));
        assertDecimal("净额退款后提成完整回冲10元", "-10",commissionSum(netOrder,4));
        assertDecimal("净额退款仍按实物回原批次", "140",packageStock(110L));
        assertDecimal("净额退款仍按实物回原种子库存", "15",packageStock(111L));
        log("[套餐] 整套报价、第三单位库存、组成指纹、分组提成、缺货回滚、分次退货尾分及零元物料验收通过");
    }

    private JsonNode packageQuote(E2EToken token,long packageId,String quantity,Long customerId) throws Exception {
        var request=get("/api/admin/product-packages/"+packageId+"/price-quote")
                .header(token.name(),token.value()).param("storeId","1").param("quantity",quantity);
        if(customerId!=null) request.param("customerId",customerId.toString());
        JsonNode response=objectMapper.readTree(mockMvc.perform(request).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertEquals("200",response.path("code").asText(),response.toString()); return response.path("data");
    }

    private POSOrderCreateCommand packageSale(long packageId,String quantity,JsonNode quote,long staffId) {
        OrderItemDTO item=new OrderItemDTO(); item.setItemType(2); item.setPackageId(packageId);
        item.setQuantity(new BigDecimal(quantity)); item.setUnit("套");
        item.setQuotedUnitPrice(quote.path("suggestedUnitPrice").decimalValue()); item.setPackageQuoteId(quote.path("quoteId").asText());
        POSOrderCreateCommand command=new POSOrderCreateCommand(); command.setStoreId(1L); command.setStaffId(staffId);
        command.setPaymentType(1); command.setDiscountAmount(BigDecimal.ZERO);
        command.setReceiveAmount(quote.path("totalAmount").decimalValue()); command.setItems(List.of(item)); return command;
    }

    private long returnViaHttp(E2EToken token,long orderId,long itemId,String quantity,String amount) throws Exception {
        JsonNode quote=postCreditJson(token,"/api/admin/orders/"+orderId+"/refund-preview",Map.of("items",List.of(
                Map.of("orderItemId",itemId,"quantity",new BigDecimal(quantity),"refundAmount",new BigDecimal(amount)))));
        requireCreditSuccess(quote);
        AdminReturnCommand command=returnCommand(orderId,itemId,quantity,amount);command.setQuoteId(quote.path("quoteId").asText());
        JsonNode response=postCreditJson(token,"/api/admin/after-sales",command);
        assertTrue(response.isIntegralNumber()&&response.asLong()>0,response.toString()); return response.asLong();
    }

    private void assertReturnRejected(E2EToken token,long orderId,long itemId,String quantity,String amount) throws Exception {
        JsonNode quote=postCreditJson(token,"/api/admin/orders/"+orderId+"/refund-preview",Map.of("items",List.of(
                Map.of("orderItemId",itemId,"quantity",new BigDecimal(quantity),"refundAmount",new BigDecimal(amount)))));
        if (!creditSuccess(quote)) {assertCreditRejected(quote);return;}
        AdminReturnCommand command=returnCommand(orderId,itemId,quantity,amount);command.setQuoteId(quote.path("quoteId").asText());
        assertCreditRejected(postCreditJson(token,"/api/admin/after-sales",command));
    }

    private AdminReturnCommand returnCommand(long orderId,long itemId,String quantity,String amount) {
        AdminReturnCommand command=new AdminReturnCommand(); command.setOrderId(orderId);
        command.setRefundAmount(new BigDecimal(amount)); command.setReason("套餐闭环验收退货");
        AdminReturnCommand.ReturnItem item=new AdminReturnCommand.ReturnItem(); item.setOrderItemId(itemId);
        item.setQuantity(new BigDecimal(quantity)); item.setRefundAmount(new BigDecimal(amount)); command.setItems(List.of(item)); return command;
    }

    private BigDecimal packageStock(long productId) {
        return jdbcDecimal("SELECT SUM(base_quantity) FROM inventory_batch_balance WHERE product_id="+productId);
    }

    private BigDecimal commissionSum(long orderId,int bizType) {
        return jdbcDecimal("SELECT COALESCE(SUM(amount),0) FROM staff_performance_log WHERE biz_id="+orderId+" AND biz_type="+bizType);
    }

    private void assertExactAuxiliaryOperations() {
        exec("INSERT INTO product(id,code,name,main_unit,aux_unit,main_to_aux_ratio,avg_cost_price,status) VALUES(103,'EXACT-P103','三瓶一箱验收','箱','瓶',3,30,1)");
        exec("INSERT INTO product_unit(id,product_id,unit_name,base_quantity_per_unit,is_base_unit,status) VALUES(4,103,'瓶',1,1,1),(5,103,'箱',3,0,1)");
        var inbound = new com.agri_system.application.inventory.dto.command.InventoryInCommand();
        inbound.setStoreId(1L); inbound.setProductId(103L); inbound.setBatchNo("EXACT-3"); inbound.setUnit("瓶");
        inbound.setQuantity(new BigDecimal("3")); inbound.setCostPrice(BigDecimal.TEN);
        inventoryApplicationService.inbound(inbound, 1L);
        var transfer = new com.agri_system.application.inventory.dto.command.InventoryTransferCommand();
        transfer.setFromStoreId(1L); transfer.setToStoreId(2L); transfer.setProductId(103L); transfer.setUnit("瓶");
        transfer.setQuantity(BigDecimal.ONE); transfer.setRequestKey("EXACT-ONE-BOTTLE");
        inventoryApplicationService.transfer(transfer, 1L);
        assertDecimal("调拨一瓶必须精确扣一瓶", "2", jdbcDecimal("SELECT SUM(base_quantity) FROM inventory_batch_balance WHERE product_id=103 AND store_id=1"));
        assertDecimal("调入一瓶不能因为小数箱数出现尾差", "1", jdbcDecimal("SELECT SUM(base_quantity) FROM inventory_batch_balance WHERE product_id=103 AND store_id=2"));
        var count = new com.agri_system.application.inventory.dto.command.InventoryAdjustCommand();
        count.setStoreId(2L); count.setProductId(103L); count.setRequestKey("EXACT-COUNT-TWO-BOTTLES");
        count.setExpectedQuantity(new BigDecimal("0.33333333")); count.setActualQuantity(new BigDecimal("2")); count.setUnit("瓶");
        count.setQuantityDiff(new BigDecimal("0.33333333")); count.setType(1); count.setRemark("按实物瓶数盘点");
        inventoryApplicationService.adjust(count, 1L);
        assertDecimal("按瓶盘点不能先转小数箱再舍入", "2", jdbcDecimal("SELECT SUM(base_quantity) FROM inventory_batch_balance WHERE product_id=103 AND store_id=2"));
        assertDecimal("主单位展示有明确8位精度", "0.66666667", jdbcDecimal("SELECT quantity FROM inventory WHERE product_id=103 AND store_id=2"));
    }

    private void assertMultiBatchPickupCompletesWithoutSecondDeduction() {
        exec("INSERT INTO product(id,code,name,main_unit,suggested_retail_price,min_price,avg_cost_price,status) VALUES(104,'MULTI-P104','多批提货验收','瓶',5,1,2,1)");
        exec("INSERT INTO product_unit(id,product_id,unit_name,base_quantity_per_unit,is_base_unit,status) VALUES(6,104,'瓶',1,1,1)");
        var inbound = new com.agri_system.application.inventory.dto.command.InventoryInCommand();
        inbound.setStoreId(1L); inbound.setProductId(104L); inbound.setUnit("瓶");
        inbound.setQuantity(new BigDecimal("2")); inbound.setCostPrice(new BigDecimal("2"));
        inbound.setBatchNo("MULTI-A"); inventoryApplicationService.inbound(inbound, 1L);
        inbound.setBatchNo("MULTI-B"); inventoryApplicationService.inbound(inbound, 1L);
        long order = orderApplicationService.createPOSOrder(posSale(104L,"3","瓶","5.00",true,null,"15.00"),1L);
        long item = jdbcLong("SELECT id FROM order_item WHERE order_id=" + order);
        long request = jdbcLong("SELECT id FROM order_pickup_request WHERE order_item_id=" + item);
        List<Long> ids = rows("SELECT id FROM order_item_batch_allocation WHERE order_item_id=" + item)
                .stream().map(row -> ((Number)row[0]).longValue()).toList();
        assertEquals(2, ids.size());
        long movements = movementCount();
        var command = new PickupFulfillmentCommand(); command.setAllocationIds(ids);
        pickupFulfillmentService.fulfill(request, command, 1L);
        assertEquals("FULFILLED", jdbcString("SELECT status FROM order_pickup_request WHERE id=" + request));
        assertEquals(movements, movementCount());
        assertDecimal("多批确认不第二次扣货", "1", jdbcDecimal("SELECT SUM(base_quantity) FROM inventory_batch_balance WHERE product_id=104"));
    }

    /** 原销售跨两次采购收货，先退一批后只交剩余批次，原数量和钱货事实不改写。 */
    private void assertPartialReturnPickupBusinessChain() throws Exception {
        E2EToken manager=loginToken(1L);
        exec("INSERT INTO product(id,code,name,main_unit,suggested_retail_price,min_price,avg_cost_price,status) VALUES(140,'PICKUP-P140','退货后剩余提货验收','袋',10,1,2,1)");
        exec("INSERT INTO product_unit(id,product_id,unit_name,base_quantity_per_unit,is_base_unit,allow_purchase,allow_sale,allow_inventory,allow_transfer,status) VALUES(1401,140,'袋',1,1,1,1,1,1,1)");
        var purchase=new com.agri_system.application.purchase.dto.command.PurchaseCreateCommand();
        purchase.setStoreId(1L);purchase.setSupplierId(1L);
        var material=new com.agri_system.application.purchase.dto.command.PurchaseItemDTO();
        material.setProductId(140L);material.setQty(new BigDecimal("20"));material.setUnit("袋");material.setPrice(new BigDecimal("2"));
        purchase.setItems(List.of(material));
        long purchaseId=purchaseApplicationService.create(purchase,1L);
        var submit=new com.agri_system.application.purchase.dto.command.PurchaseSubmitCommand();submit.setOrderId(purchaseId);
        purchaseApplicationService.submit(submit);
        long purchaseItem=jdbcLong("SELECT id FROM purchase_order_item WHERE purchase_order_id="+purchaseId);
        for(int i=0;i<2;i++) {
            var receipt=receiveCmd(purchaseId,purchaseItem,"PICKUP-RETURN-"+i,"袋",i==0?"1":"19","2",null,LocalDate.now().plusYears(i+1));
            receipt.getLines().get(0).setProductId(140L);receipt.setRequestKey("PICKUP-RETURN-RECEIPT-"+i);
            purchaseApplicationService.receive(receipt,1L);
        }
        assertEquals(2L,jdbcLong("SELECT COUNT(*) FROM inventory_receipt_batch WHERE product_id=140"));
        long order=createPOSViaHttp(manager,posSale(140L,"2","袋","10",true,null,"20"));
        long item=jdbcLong("SELECT id FROM order_item WHERE order_id="+order);
        long request=jdbcLong("SELECT id FROM order_pickup_request WHERE order_item_id="+item);
        List<Object[]> allocations=rows("SELECT a.id,a.batch_id FROM order_item_batch_allocation a JOIN inventory_receipt_batch b ON b.id=a.batch_id WHERE a.order_item_id="+item+" ORDER BY b.expiry_date,a.id");
        assertEquals(2,allocations.size());
        long first=((Number)allocations.get(0)[0]).longValue(),last=((Number)allocations.get(1)[0]).longValue();
        JsonNode quote=pickupReturnPreview(manager,order,item,"1");assertPreview(quote,"10","0","10");
        pickupReturn(manager,order,quote);
        assertEquals(9L,jdbcLong("SELECT order_status FROM `order` WHERE id="+order));
        assertDecimal("原申请量不被部分退货改写","2",jdbcDecimal("SELECT requested_base_quantity FROM order_pickup_request WHERE id="+request));
        assertDecimal("原销售分配保留2袋","2",jdbcDecimal("SELECT SUM(allocated_base_quantity) FROM order_item_batch_allocation WHERE order_item_id="+item));
        assertDecimal("先退第一批1袋","1",jdbcDecimal("SELECT SUM(base_quantity) FROM inventory_movement WHERE movement_type='RETURN' AND reference_type='SALE_ALLOCATION' AND reference_id="+first));
        var pending=pickupFulfillmentService.listPickupRequests(1L,"PENDING").stream().filter(r->r.getId().equals(request)).findFirst().orElseThrow();
        assertTrue(pending.isCanFulfill());assertDecimal("本次只剩1袋可交","1",pending.getRemainingBaseQuantity());
        assertDecimal("已退批次不要求再交","0",pending.getAllocations().stream().filter(a->a.getId().equals(first)).findFirst().orElseThrow().getRemainingBaseQuantity());
        DeliveryFacts returned=deliveryFacts();
        var remaining=Map.of("allocationIds",List.of(last));
        requireCreditSuccess(postCreditJson(manager,"/api/admin/orders/pickup/requests/"+request+"/fulfill",remaining));
        assertEquals("FULFILLED",jdbcString("SELECT status FROM order_pickup_request WHERE id="+request));
        assertEquals("FULFILLMENT_CONFIRMED",jdbcString("SELECT evidence_level FROM order_item_batch_allocation WHERE id="+last));
        assertTrue(!"FULFILLMENT_CONFIRMED".equals(jdbcString("SELECT evidence_level FROM order_item_batch_allocation WHERE id="+first)),"已退物料不可冒记本次交货证据");
        assertEquals(returned,deliveryFacts(),"提货不能再次扣库、收款或结算客户和提成");
        requireCreditSuccess(postCreditJson(manager,"/api/admin/orders/pickup/requests/"+request+"/fulfill",remaining));
        assertEquals(returned,deliveryFacts(),"完成请求原样重放没有副作用");
        // 完成后的退货不能倒改过去交货事实，已完成查询不伪造历史交货量。
        pickupReturn(manager,order,pickupReturnPreview(manager,order,item,"1"));
        var fulfilled=pickupFulfillmentService.listPickupRequests(1L,"FULFILLED").stream().filter(r->r.getId().equals(request)).findFirst().orElseThrow();
        assertNull(fulfilled.getRemainingBaseQuantity());
        assertDecimal("已交历史仍保留原申请","2",fulfilled.getRequestedBaseQuantity());
        DeliveryFacts fullyReturned=deliveryFacts();
        requireCreditSuccess(postCreditJson(manager,"/api/admin/orders/pickup/requests/"+request+"/fulfill",remaining));
        assertEquals(fullyReturned,deliveryFacts(),"完成后的退款也不能让重放再交货或记账");

        // 单批次退半袋与提货争用同一父订单，均可完成，只扣原销售1袋。
        long racing=createPOSViaHttp(manager,posSale(140L,"1","袋","10",true,null,"10"));
        long racingItem=jdbcLong("SELECT id FROM order_item WHERE order_id="+racing);
        long racingRequest=jdbcLong("SELECT id FROM order_pickup_request WHERE order_item_id="+racingItem);
        Map<String,Object> returnBody=new LinkedHashMap<>(creditReturnBody(racing,pickupReturnPreview(manager,racing,racingItem,"0.5")));
        String batchCode=jdbcString("SELECT b.internal_receipt_batch_no FROM order_item_batch_allocation a JOIN inventory_receipt_batch b ON b.id=a.batch_id WHERE a.order_item_id="+racingItem+" LIMIT 1");
        long payments=jdbcLong("SELECT COUNT(*) FROM order_payment WHERE order_id="+racing);
        List<JsonNode> responses=raceCreditHttp(index->index==0
                ?postCreditJson(manager,"/api/admin/after-sales",returnBody)
                :postCreditJson(manager,"/api/admin/orders/pickup/requests/"+racingRequest+"/fulfill",Map.of("scannedCode",batchCode)));
        for(JsonNode response:responses) requireCreditSuccess(response);
        assertEquals("FULFILLED",jdbcString("SELECT status FROM order_pickup_request WHERE id="+racingRequest));
        assertEquals(9L,jdbcLong("SELECT order_status FROM `order` WHERE id="+racing));
        assertEquals(payments,jdbcLong("SELECT COUNT(*) FROM order_payment WHERE order_id="+racing));
        assertOrderReturned(racing,"0.5");assertCreditState(racing,"10","0","0","0","5","0");
        assertDecimal("两单完成后账本剩19.5袋","19.5",jdbcDecimal("SELECT SUM(base_quantity) FROM inventory_batch_balance WHERE product_id=140"));
        long full=createPOSViaHttp(manager,posSale(140L,"1","袋","10",true,null,"10"));
        long fullItem=jdbcLong("SELECT id FROM order_item WHERE order_id="+full);
        long fullRequest=jdbcLong("SELECT id FROM order_pickup_request WHERE order_item_id="+fullItem);
        pickupReturn(manager,full,pickupReturnPreview(manager,full,fullItem,"1"));
        var blocked=pickupFulfillmentService.listPickupRequests(1L,"PENDING").stream().filter(r->r.getId().equals(fullRequest)).findFirst().orElseThrow();
        assertTrue(!blocked.isCanFulfill());assertDecimal("待交前全部退货剩余为0","0",blocked.getRemainingBaseQuantity());
        DeliveryFacts beforeRefusal=deliveryFacts();
        assertCreditRejected(postCreditJson(manager,"/api/admin/orders/pickup/requests/"+fullRequest+"/fulfill",Map.of("allocationIds",List.of())));
        assertEquals(beforeRefusal,deliveryFacts());
        assertEquals("PENDING",jdbcString("SELECT status FROM order_pickup_request WHERE id="+fullRequest),"全退不能伪造已交货");
        log("[提货] 两次采购收货、先退一批再确认剩余批次及退货确认竞争均不重复钱货事实");
    }

    private void pickupReturn(E2EToken token,long order,JsonNode quote) throws Exception {
        JsonNode result=postCreditJson(token,"/api/admin/after-sales",creditReturnBody(order,quote));
        assertTrue(result.isIntegralNumber() && result.asLong()>0,result.toString());
    }

    private JsonNode pickupReturnPreview(E2EToken token,long order,long item,String quantity) throws Exception {
        return requireCreditSuccess(postCreditJson(token,"/api/admin/orders/"+order+"/refund-preview",
                Map.of("items",List.of(Map.of("orderItemId",item,"quantity",new BigDecimal(quantity))))));
    }

    private void assertConcurrentReturnCannotExceedOriginalSale() throws Exception {
        long order = orderApplicationService.createPOSOrder(posSale(101L, "2", "瓶", "5.00", false, null, "10.00"), 1L);
        long item = jdbcLong("SELECT id FROM order_item WHERE order_id=" + order);
        long allocation = jdbcLong("SELECT id FROM order_item_batch_allocation WHERE order_item_id=" + item);
        assertStock(1, "0.8", "16");
        List<Boolean> outcomes = raceWithOldSnapshot(i -> {
            var command = new com.agri_system.application.inventory.dto.command.InventoryInCommand();
            command.setStoreId(1L); command.setProductId(101L); command.setUnit("瓶");
            command.setQuantity(new BigDecimal("2")); command.setSourceType("SALES_RETURN");
            command.setSourceId(9100L + i); command.setSourceLineNo(Math.toIntExact(item));
            inventoryApplicationService.inbound(command, 1L);
        }, "累计退货数量超过");
        assertEquals(1L, outcomes.stream().filter(Boolean::booleanValue).count(),
                "两笔不同退款单在旧快照并发退同一销售，必须只有一笔成功");
        assertStock(1, "0.9", "18");
        assertDecimal("累计退货不得超过原单实际销售的2瓶", "2", jdbcDecimal(
                "SELECT COALESCE(SUM(base_quantity),0) FROM inventory_movement WHERE movement_type='RETURN'"
                        + " AND reference_type='SALE_ALLOCATION' AND reference_id=" + allocation));
        log("[并发] 两个 REPEATABLE_READ 旧快照退货事务只有一笔回库，另一笔超量回滚");
    }

    private void assertConcurrentSalesCannotOversell() throws Exception {
        var initial = new com.agri_system.application.inventory.dto.command.InventoryInCommand();
        initial.setStoreId(1L); initial.setProductId(102L); initial.setUnit("袋");
        initial.setBatchNo("E2E-CONCURRENT-STOCK"); initial.setQuantity(new BigDecimal("2"));
        initial.setCostPrice(new BigDecimal("3")); initial.setSourceType("E2E"); initial.setSourceId(9200L);
        inventoryApplicationService.inbound(initial, 1L);
        long before = movementCount();
        List<Boolean> outcomes = raceWithOldSnapshot(i -> {
            InventoryOutCommand command = outCmd(102L, "2", "袋");
            command.setReferenceId(9201L + i);
            inventoryApplicationService.outbound(command, 1L);
        }, "可用库存不足");
        assertEquals(1L, outcomes.stream().filter(Boolean::booleanValue).count(), "最后2袋被并发购买，必须只有一笔出库");
        assertEquals(before + 1, movementCount(), "失败销售不得留下部分流水");
        assertDecimal("并发销售后快照为0", "0", jdbcDecimal("SELECT quantity FROM inventory WHERE store_id=1 AND product_id=102"));
        assertDecimal("并发销售后批次余额为0", "0", jdbcDecimal("SELECT SUM(base_quantity) FROM inventory_batch_balance WHERE store_id=1 AND product_id=102"));
        log("[并发] 两个 REPEATABLE_READ 旧快照争抢最后2袋只有一笔出库，余额不负");
    }

    private List<Boolean> raceWithOldSnapshot(java.util.function.IntConsumer action, String expectedRejection) throws Exception {
        var barrier = new java.util.concurrent.CyclicBarrier(2);
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        List<java.util.concurrent.Future<Boolean>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < 2; i++) {
                final int index = i;
                futures.add(pool.submit(() -> {
                    var transaction = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
                    transaction.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ);
                    transaction.setTimeout(20);
                    try {
                        transaction.execute(status -> {
                            // 两边先建立一致性读快照，再放行写入，稳定复现旧快照漏看另一事务已退量。
                            jdbcTemplate.queryForObject("SELECT COUNT(*) FROM inventory_movement", Long.class);
                            try { barrier.await(10, java.util.concurrent.TimeUnit.SECONDS); }
                            catch (Exception e) { throw new IllegalStateException("并发测试屏障失败", e); }
                            action.accept(index);
                            return null;
                        });
                        return true;
                    } catch (IllegalArgumentException e) {
                        assertTrue(e.getMessage() != null && e.getMessage().contains(expectedRejection),
                                "失败必须来自业务数量守卫，不能用死锁或其它错误假装通过: " + e.getMessage());
                        return false;
                    }
                }));
            }
            List<Boolean> result = new ArrayList<>();
            for (var future : futures) result.add(future.get(25, java.util.concurrent.TimeUnit.SECONDS));
            return result;
        } finally { pool.shutdownNow(); }
    }

    private long movementCount() { return jdbcLong("SELECT COUNT(*) FROM inventory_movement"); }

    private void assertStock(long store, String mainQuantity, String baseQuantity) {
        assertDecimal("门店快照必须使用主单位", mainQuantity,
                jdbcDecimal("SELECT quantity FROM inventory WHERE store_id=" + store + " AND product_id=101"));
        assertDecimal("批次余额必须使用基准单位", baseQuantity,
                jdbcDecimal("SELECT COALESCE(SUM(base_quantity),0) FROM inventory_batch_balance WHERE store_id=" + store + " AND product_id=101"));
    }

    private void assertLedgerReconciles() {
        assertEquals(0L, jdbcLong("SELECT COUNT(*) FROM inventory_batch_balance b WHERE b.base_quantity<>"
                + "(SELECT COALESCE(SUM(CASE WHEN m.to_location_id=b.location_id THEN m.base_quantity ELSE 0 END),0)"
                + "-COALESCE(SUM(CASE WHEN m.from_location_id=b.location_id THEN m.base_quantity ELSE 0 END),0)"
                + " FROM inventory_movement m WHERE m.batch_id=b.batch_id)"),
                "每个批次货位余额必须精确等于流水净额，收货也必须入流水");
        assertEquals(0L, jdbcLong("SELECT COUNT(*) FROM inventory_batch_balance WHERE base_quantity<reserved_base_quantity OR base_quantity<0"));
    }

    // ------------------------------------------------------------------
    // 夹具（真实 NOT NULL 约束的最小集；legacy inventory 无 unit 列）
    // ------------------------------------------------------------------
    private void seedFixture() {
        exec("INSERT INTO store (id, code, name, status) VALUES (1, 'E2E-STORE', '端到端门店', 1), (2, 'E2E-TARGET', '调入门店', 1)");
        exec("INSERT INTO staff (id, code, name, store_id, role, status) "
                + "VALUES (1, 'E2E-STAFF', '端到端员工', 1, 'STAFF', 1)");
        exec("INSERT INTO supplier (id, code, name) VALUES (1, 'E2E-SUP', '端到端供应商')");
        exec("INSERT INTO product (id, code, name, main_unit, aux_unit, main_to_aux_ratio, "
                + "suggested_retail_price, min_price, avg_cost_price, status) VALUES "
                + "(101, 'E2E-P101', 'E2E多单位商品', '箱', '瓶', 20, 5.00, 1.00, 3.00, 1), "
                + "(102, 'E2E-P102', 'E2E对照商品', '袋', NULL, NULL, 3.00, 1.00, 3.00, 1)");
        exec("INSERT INTO product_unit (id, product_id, unit_name, base_quantity_per_unit, is_base_unit, "
                + "allow_purchase, allow_sale, allow_inventory, allow_transfer, status) VALUES "
                + "(1, 101, '瓶', 1, 1, 1, 1, 1, 1, 1), "
                + "(2, 101, '箱', 20, 0, 1, 1, 1, 1, 1), "
                + "(3, 102, '袋', 1, 1, 1, 1, 1, 1, 1)");
        exec("INSERT INTO inventory_location (id, store_id, location_type, code, name, status) VALUES "
                + "(1, 1, 'SALES_FLOOR', 'FLOOR-A', '前仓', 1), "
                + "(2, 1, 'STORE_BACKROOM', 'BACK-A', '后仓', 1), "
                + "(3, 1, 'OTHER_STORE', 'EMPTY-LOC', '空对照货位', 1), (4, 2, 'STORE_BACKROOM', 'BACK-B', '调入后仓', 1)");
        exec("INSERT INTO purchase_order (id, purchase_no, supplier_id, store_id, total_amount, status) VALUES "
                + "(1, 'PO-E2E-1', 1, 1, 200.00, 2), "
                + "(2, 'PO-E2E-2', 1, 1, 90.00, 2)");
        exec("INSERT INTO purchase_order_item (id, purchase_order_id, product_id, quantity, unit, buy_price, total_price, sort_order) VALUES "
                + "(1, 1, 101, 2, '箱', 100.00, 200.00, 1), "
                + "(2, 2, 101, 1, '箱', 90.00, 90.00, 1)");
        log("[fixture] 两个门店与多单位商品已就绪，库存全部由业务入口建立");
    }

    // ------------------------------------------------------------------
    // 命令构造
    // ------------------------------------------------------------------
    private POSOrderCreateCommand posSale(Long productId, String quantity, String unit, String unitPrice,
                                          boolean warehousePickup, String priceChangeReason, String receiveAmount) {
        OrderItemDTO item = new OrderItemDTO();
        item.setProductId(productId);
        item.setQuantity(new BigDecimal(quantity));
        item.setUnit(unit);
        item.setUnitPrice(new BigDecimal(unitPrice));
        item.setWarehousePickup(warehousePickup);
        item.setPriceChangeReason(priceChangeReason);

        POSOrderCreateCommand command = new POSOrderCreateCommand();
        command.setStoreId(1L);
        command.setStaffId(1L);
        command.setPaymentType(1); // 现金
        command.setDiscountAmount(BigDecimal.ZERO);
        command.setReceiveAmount(new BigDecimal(receiveAmount));
        command.setItems(List.of(item));
        return command;
    }

    private InventoryOutCommand outCmd(Long productId, String quantity, String unit) {
        InventoryOutCommand command = new InventoryOutCommand();
        command.setStoreId(1L);
        command.setProductId(productId);
        command.setQuantity(new BigDecimal(quantity));
        command.setUnit(unit);
        command.setReferenceType("E2E");
        command.setReferenceId(1L);
        return command;
    }

    private ReceivePurchaseCommand receiveCmd(Long purchaseOrderId, Long purchaseOrderItemId, String supplierBatchNo,
                                              String originalUnit, String originalQuantity, String originalUnitCost,
                                              LocalDate productionDate, LocalDate expiryDate) {
        ReceiptBatchCommand batch = new ReceiptBatchCommand();
        batch.setSupplierBatchNo(supplierBatchNo);
        batch.setOriginalUnit(originalUnit);
        batch.setOriginalQuantity(new BigDecimal(originalQuantity));
        batch.setOriginalUnitCost(new BigDecimal(originalUnitCost));
        batch.setProductionDate(productionDate);
        batch.setExpiryDate(expiryDate);

        ReceivePurchaseCommand.ReceiptLineCommand line = new ReceivePurchaseCommand.ReceiptLineCommand();
        line.setPurchaseOrderItemId(purchaseOrderItemId);
        line.setProductId(101L);
        line.setBatches(List.of(batch));

        ReceivePurchaseCommand command = new ReceivePurchaseCommand();
        command.setPurchaseOrderId(purchaseOrderId);
        command.setLines(List.of(line));
        return command;
    }

    // ------------------------------------------------------------------
    // JDBC 直查辅助（夹具 seed 与断言查询同一数据源）
    // ------------------------------------------------------------------
    private List<Object[]> rows(String sql) {
        List<Object[]> result = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            int columns = rs.getMetaData().getColumnCount();
            while (rs.next()) {
                Object[] row = new Object[columns];
                for (int i = 0; i < columns; i++) {
                    row[i] = rs.getObject(i + 1);
                }
                result.add(row);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("JDBC 查询失败: " + sql, e);
        }
        return result;
    }

    private void exec(String sql) {
        try (Connection conn = dataSource.getConnection();
             Statement st = conn.createStatement()) {
            st.execute(sql);
        } catch (SQLException e) {
            throw new IllegalStateException("JDBC 执行失败: " + sql, e);
        }
    }

    private long jdbcLong(String sql) {
        Object value = rows(sql).get(0)[0];
        assertNotNull(value, "查询应返回非 NULL: " + sql);
        return ((Number) value).longValue();
    }

    private String jdbcString(String sql) {
        return (String) rows(sql).get(0)[0];
    }

    private BigDecimal jdbcDecimal(String sql) {
        Object value = rows(sql).get(0)[0];
        assertNotNull(value, "查询应返回非 NULL: " + sql);
        return value instanceof BigDecimal ? (BigDecimal) value : new BigDecimal(value.toString());
    }

    private Timestamp jdbcTimestamp(String sql) {
        Object value = rows(sql).get(0)[0];
        assertNotNull(value, "查询应返回非 NULL: " + sql);
        // MySQL Connector/J 9.x 对 DATETIME 列默认返回 LocalDateTime，此处统一归一化为 Timestamp
        if (value instanceof Timestamp) {
            return (Timestamp) value;
        }
        if (value instanceof LocalDateTime) {
            return Timestamp.valueOf((LocalDateTime) value);
        }
        throw new IllegalStateException("未知时间类型: " + value.getClass().getName() + " 值=" + value);
    }

    private static void assertDecimal(String message, String expected, BigDecimal actual) {
        assertNotNull(actual, message + "（实际为 NULL）");
        if (new BigDecimal(expected).compareTo(actual) != 0) {
            fail(message + "，期望 " + new BigDecimal(expected).toPlainString()
                    + " 实际 " + actual.toPlainString() + "（tolerance=0）");
        }
    }

    private static void log(String message) {
        System.out.println("[E2E] " + message);
    }

    // ------------------------------------------------------------------
    // 测试专用 Spring 配置：扫描 application+infrastructure（排除 SaTokenConfigure，
    // 保持原库存 API 场景），价格/订单 API 通过真实 SaToken 注解权限与登录 token 验收。
    // ------------------------------------------------------------------
    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EnableCaching
    @MapperScan("com.agri_system.infrastructure")
    @ComponentScan(basePackages = {"com.agri_system.application", "com.agri_system.infrastructure"},
            excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SaTokenConfigure.class))
    @Import({InventoryController.class, InventoryWarningController.class, ProductController.class, OrderAdminController.class,
            ProductPackageController.class, AfterSalesAdminController.class, CreditAdminController.class, ReportDashboardController.class,
            RetailFactChainEndToEndTest.E2ELoginController.class, DomainServiceConfig.class})
    static class E2EConfig {

        @Bean
        @Primary
        StpInterface e2eStpInterface() {
            return new StpInterface() {
                @Override
                public List<String> getPermissionList(Object loginId, String loginType) {
                    if ("STAFF_2".equals(String.valueOf(loginId))) {
                        return List.of("pos:order:create", "product:query", "credit:repay",
                                "credit:account:view", "credit:repayment:view", "credit:statistics:view");
                    }
                    return List.of("*");
                }

                @Override
                public List<String> getRoleList(Object loginId, String loginType) {
                    return List.of("E2E");
                }
            };
        }

        @Bean
        WebMvcConfigurer pricingPermissionInterceptor() {
            return new WebMvcConfigurer() {
                @Override
                public void addInterceptors(InterceptorRegistry registry) {
                    registry.addInterceptor(new SaInterceptor())
                            .addPathPatterns("/api/admin/products/**", "/api/admin/orders/**",
                                    "/api/admin/product-packages/**", "/api/admin/after-sales/**", "/api/admin/credit/**", "/api/admin/report/**");
                    // 测试不加载生产租户工厂；仅还原请求级身份缓存清理，不能跨token复用旧员工。
                    registry.addInterceptor(new org.springframework.web.servlet.HandlerInterceptor() {
                        @Override public boolean preHandle(jakarta.servlet.http.HttpServletRequest request,
                                jakarta.servlet.http.HttpServletResponse response, Object handler) {
                            com.agri_system.application.auth.util.CurrentUser.clearRequestCache(); return true;
                        }
                        @Override public void afterCompletion(jakarta.servlet.http.HttpServletRequest request,
                                jakarta.servlet.http.HttpServletResponse response, Object handler, Exception exception) {
                            com.agri_system.application.auth.util.CurrentUser.clearRequestCache();
                        }
                    }).addPathPatterns("/**");
                }
            };
        }
    }

    /** 测试专用登录端点：给 MockMvc 提供 token，不进入生产代码。 */
    @RestController
    static class E2ELoginController {

        @GetMapping("/__e2e__/login")
        public Map<String, Object> login(@RequestParam("staffId") Long staffId) {
            StpUtil.login("STAFF_" + staffId);
            SaTokenInfo info = StpUtil.getTokenInfo();
            Map<String, Object> body = new HashMap<>();
            body.put("tokenName", info.getTokenName());
            body.put("tokenValue", info.getTokenValue());
            return body;
        }
    }
}
