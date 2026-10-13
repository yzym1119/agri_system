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
        assertThrows(RuntimeException.class, () -> pickupFulfillmentService.fulfill(pickup, scan, 1L));
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
        assertLedgerReconciles();
        assertEquals(0L, jdbcLong("SELECT COUNT(*) FROM inventory_balance_feature_status"));
        log("收货、销售、原单退货、提货、调拨、盘点及失败回滚使用同一账本，验收通过");
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
        JsonNode response=objectMapper.readTree(mockMvc.perform(post("/api/admin/after-sales")
                        .header(token.name(),token.value()).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(returnCommand(orderId,itemId,quantity,amount))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertTrue(response.isIntegralNumber()&&response.asLong()>0,response.toString()); return response.asLong();
    }

    private void assertReturnRejected(E2EToken token,long orderId,long itemId,String quantity,String amount) throws Exception {
        JsonNode response=objectMapper.readTree(mockMvc.perform(post("/api/admin/after-sales")
                        .header(token.name(),token.value()).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(returnCommand(orderId,itemId,quantity,amount))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertEquals(10001,response.path("code").asInt(),response.toString());
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
            ProductPackageController.class, AfterSalesAdminController.class,
            RetailFactChainEndToEndTest.E2ELoginController.class, DomainServiceConfig.class})
    static class E2EConfig {

        @Bean
        @Primary
        StpInterface e2eStpInterface() {
            return new StpInterface() {
                @Override
                public List<String> getPermissionList(Object loginId, String loginType) {
                    if ("STAFF_2".equals(String.valueOf(loginId))) {
                        return List.of("pos:order:create", "product:query");
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
                                    "/api/admin/product-packages/**", "/api/admin/after-sales/**");
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
