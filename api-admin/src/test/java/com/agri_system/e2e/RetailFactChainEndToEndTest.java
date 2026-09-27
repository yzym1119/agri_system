package com.agri_system.e2e;

import cn.dev33.satoken.stp.SaTokenInfo;
import cn.dev33.satoken.stp.StpInterface;
import cn.dev33.satoken.stp.StpUtil;
import com.agri_system.api.admin.controller.InventoryController;
import com.agri_system.api.admin.controller.InventoryWarningController;
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
 * Task 10c 零售事实链端到端验收测试（仅当 -Dmysql.it=true 时执行）。
 *
 * <p>覆盖 brief §4 的十步弧线：legacy 耗尽 → 激活（enable）→ 收货+守卫 → 移动/销售证据
 * （RULE_ALLOCATED / UNKNOWN / SCANNED）→ 改价留痕 → 日期未知风险建议 → L1/L2/L3 对账 →
 * 回滚（save LEGACY）→ M4 HTTP 端点 → M2 边界。
 *
 * <p>有意简化（brief §3）：不模拟历史初始化（那是 10b 运营脚本已 smoke 的职责）。
 * 本 E2E 让 enable 前的两笔 legacy 销售恰好耗尽 legacy 库存，因此激活后投影覆写无遗留差额。
 *
 * <p>本类只使用一次性测试库 agri_retail_e2e（@DynamicPropertySource 中建库并跑 Flyway，
 * @AfterAll drop）。绝不连接 agri_system / tlcloud_* / agri_retail_it。
 *
 * <p>MySQL 不可达时本测试必须失败（抛 IllegalStateException），禁止 Assumptions 静默跳过。
 */
@EnabledIfSystemProperty(named = "mysql.it", matches = "true")
@SpringBootTest(classes = RetailFactChainEndToEndTest.E2EConfig.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class RetailFactChainEndToEndTest {

    private static final String DB_NAME = "agri_retail_e2e";

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
            st.execute("DROP DATABASE IF EXISTS " + DB_NAME);
            st.execute("CREATE DATABASE " + DB_NAME + " CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
            databaseCreated = true;
            log("[db] 已重建一次性测试库 " + DB_NAME + "（未触碰 agri_system / tlcloud_* / agri_retail_it）");
        } catch (SQLException e) {
            throw new IllegalStateException("E2E 验收需要可达的真实 MySQL（-Dmysql.it=true，"
                    + "mysql.it.host/port/user/password 可覆盖；当前 " + serverUrl + "）。"
                    + "本测试禁止静默跳过：MySQL 不可达即失败", e);
        }

        MigrateResult migrateResult = Flyway.configure()
                .dataSource(jdbcUrl, dbUser, dbPassword)
                .locations("classpath:db/migration")
                .load()
                .migrate();
        log("[flyway] migrationsExecuted=" + migrateResult.migrationsExecuted);

        selfCheckFlywayHistory();

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
            classpathMigrations = distinct.size();
            sb.append("[flyway-selfcheck] classpath 迁移文件(按文件名去重)=").append(classpathMigrations).append('\n');
        } catch (Exception e) {
            throw new IllegalStateException("枚举 classpath:db/migration/*.sql 失败（自证无法完成即失败）", e);
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
    void retailFactChainTenStepArc() throws Exception {
        seedFixture();

        // ============ 步骤 1：enable 前两笔 legacy 销售恰好耗尽 legacy 库存 ============
        // 40 瓶 + 1 箱 = 60 瓶 = 3 箱（主单位），与 legacy 快照 3 箱恰好抵消；
        // 有意不模拟历史初始化（见类注释）。两笔销售均走 legacy 出库路径。
        Long saleALegacyOrderId = orderApplicationService.createPOSOrder(
                posSale(101L, "40", "瓶", "5.00", false, null, "200.00"), 1L);
        assertNotNull(saleALegacyOrderId, "第一笔 legacy 销售应成功");
        log("[step1] legacy 销售1(40瓶) orderId=" + saleALegacyOrderId);

        Long saleBLegacyOrderId = orderApplicationService.createPOSOrder(
                posSale(101L, "1", "箱", "100.00", false, null, "100.00"), 1L);
        assertNotNull(saleBLegacyOrderId, "第二笔 legacy 销售应成功");
        log("[step1] legacy 销售2(1箱) orderId=" + saleBLegacyOrderId);

        assertDecimal("步骤1 legacy 快照应被两笔销售恰好耗尽",
                "0", jdbcDecimal("SELECT quantity FROM inventory WHERE store_id=1 AND product_id=101"));
        assertEquals(0L, jdbcLong("SELECT COUNT(*) FROM order_item_batch_allocation"),
                "enable 前销售不得产生批次归属行（新链路零写入）");
        assertDecimal("LB-A 应被 FIFO 耗尽为 0",
                "0", jdbcDecimal("SELECT quantity FROM inventory_batch WHERE store_id=1 AND product_id=101 AND batch_no='LB-A'"));
        assertEquals(0L, jdbcLong("SELECT is_active FROM inventory_batch WHERE store_id=1 AND product_id=101 AND batch_no='LB-A'"));
        assertDecimal("LB-B 应被 FIFO 耗尽为 0",
                "0", jdbcDecimal("SELECT quantity FROM inventory_batch WHERE store_id=1 AND product_id=101 AND batch_no='LB-B'"));
        assertEquals(0L, jdbcLong("SELECT is_active FROM inventory_batch WHERE store_id=1 AND product_id=101 AND batch_no='LB-B'"));

        // ============ 步骤 2：激活（enable）→ BATCH_LOCATION + cutover 留痕 ============
        InventoryBalanceFeatureStatus enabled = featureStatusRepository.enable(1L, 101L, 1L);
        assertNotNull(enabled, "enable 应返回已激活实体");
        assertNotNull(enabled.getId(), "enable 返回值应已回填实体 id");
        assertEquals(InventoryBalanceFeatureStatus.BATCH_LOCATION, enabled.getBalanceMode());
        assertEquals("BATCH_LOCATION",
                jdbcString("SELECT balance_mode FROM inventory_balance_feature_status WHERE store_id=1 AND product_id=101"));
        assertNotNull(jdbcTimestamp("SELECT cutover_at FROM inventory_balance_feature_status WHERE store_id=1 AND product_id=101"),
                "enable 应落 cutover_at");
        assertEquals(1L, jdbcLong("SELECT cutover_by FROM inventory_balance_feature_status WHERE store_id=1 AND product_id=101"));
        log("[step2] enable 完成: featureStatusId=" + enabled.getId());

        // ============ 步骤 3：收货 2 箱（必须先 enable 再收货）→ 后仓余额 + 守卫对照 ============
        Long receiptId1 = purchaseApplicationService.receive(
                receiveCmd(1L, 1L, "SUP-BATCH-1", "箱", "2", "100.00", null, null), 1L);
        assertNotNull(receiptId1, "PO-E2E-1 收货应成功");
        long r1BatchId = jdbcLong("SELECT id FROM inventory_receipt_batch WHERE store_id=1 AND product_id=101 ORDER BY id LIMIT 1");
        String r1InternalNo = jdbcString("SELECT internal_receipt_batch_no FROM inventory_receipt_batch WHERE id=" + r1BatchId);
        assertNotNull(r1InternalNo, "内部批次标签号应生成");
        log("[step3] R1 batchId=" + r1BatchId + " internalNo=" + r1InternalNo);

        assertDecimal("R1 base_quantity 应为 40（2箱×20）",
                "40", jdbcDecimal("SELECT base_quantity FROM inventory_receipt_batch WHERE id=" + r1BatchId));
        assertEquals(1L, jdbcLong("SELECT base_unit_id FROM inventory_receipt_batch WHERE id=" + r1BatchId));
        assertDecimal("R1 base_unit_cost 应为 5.00（100/20）",
                "5.00", jdbcDecimal("SELECT base_unit_cost FROM inventory_receipt_batch WHERE id=" + r1BatchId));
        assertEquals("UNKNOWN", jdbcString("SELECT date_status FROM inventory_receipt_batch WHERE id=" + r1BatchId));
        // 后仓（id=2）余额 = 40
        assertDecimal("R1 后仓余额应为 40",
                "40", jdbcDecimal("SELECT base_quantity FROM inventory_batch_balance WHERE batch_id=" + r1BatchId + " AND location_id=2"));
        // 收货不写 movement
        assertEquals(0L, jdbcLong("SELECT COUNT(*) FROM inventory_movement WHERE store_id=1"),
                "收货不得产生 inventory_movement");
        // 投影已触发：legacy 快照 = 40
        assertDecimal("收货后投影应把快照改写为基准单位量 40",
                "40", jdbcDecimal("SELECT quantity FROM inventory WHERE store_id=1 AND product_id=101"));

        // 守卫对照：已激活 SKU 走 legacy 出库入口被拒
        IllegalArgumentException guardEx = assertThrows(IllegalArgumentException.class,
                () -> inventoryApplicationService.outbound(outCmd(101L, "1", "箱"), 1L),
                "已切换到批次货位余额模式的 SKU 必须拒绝 legacy 出库入口");
        assertTrue(guardEx.getMessage() != null && guardEx.getMessage().contains("已切换到批次货位余额模式"),
                "守卫消息应包含『已切换到批次货位余额模式』，实际: " + guardEx.getMessage());
        assertTrue(guardEx.getMessage().contains("禁止走传统出库路径"),
                "守卫消息应包含『禁止走传统出库路径』，实际: " + guardEx.getMessage());
        log("[step3] 守卫拒绝消息: " + guardEx.getMessage());

        // 未启用对照 SKU（102）仍放行 legacy 路径
        inventoryApplicationService.outbound(outCmd(102L, "2", "袋"), 1L);
        assertDecimal("对照 SKU 102 legacy 出库后快照应为 8",
                "8", jdbcDecimal("SELECT quantity FROM inventory WHERE store_id=1 AND product_id=102"));
        assertDecimal("对照 SKU 102 legacy 批次 LB-C 应扣减为 8",
                "8", jdbcDecimal("SELECT quantity FROM inventory_batch WHERE store_id=1 AND product_id=102 AND batch_no='LB-C'"));

        // ============ 步骤 4：移动 + 三类销售证据（RULE_ALLOCATED / UNKNOWN / SCANNED） ============
        // 4a. 10 瓶 后仓→前仓（REPLENISHMENT 须两端货位）
        InventoryMovement m1 = batchBalanceService.move(new BatchBalanceService.MoveRequest(
                r1BatchId, 2L, 1L, BatchBalanceService.MovementPurpose.REPLENISHMENT,
                null, 1L, "瓶", new BigDecimal("10"), new BigDecimal("10"), new BigDecimal("5.00"),
                null, null, 1L, "E2E 前仓补货", null, null));
        assertNotNull(m1.getId(), "补货移动应落库");
        assertEquals("REPLENISHMENT", m1.getMovementType());
        assertEquals(Long.valueOf(2L), m1.getFromLocationId());
        assertEquals(Long.valueOf(1L), m1.getToLocationId());
        assertDecimal("R1 后仓 40→30",
                "30", jdbcDecimal("SELECT base_quantity FROM inventory_batch_balance WHERE batch_id=" + r1BatchId + " AND location_id=2"));
        assertDecimal("R1 前仓 0→10",
                "10", jdbcDecimal("SELECT base_quantity FROM inventory_batch_balance WHERE batch_id=" + r1BatchId + " AND location_id=1"));
        assertEquals(1L, jdbcLong("SELECT COUNT(*) FROM inventory_movement WHERE store_id=1"));
        assertDecimal("移动不改变快照（仍为 40）",
                "40", jdbcDecimal("SELECT quantity FROM inventory WHERE store_id=1 AND product_id=101"));
        log("[step4a] 补货移动 movementNo=" + m1.getMovementNo());

        // 4b. 前仓单批次可满足 → RULE_ALLOCATED（RULE_FLOOR_SINGLE_BATCH）
        Long saleAId = orderApplicationService.createPOSOrder(
                posSale(101L, "2", "瓶", "5.00", false, null, "10.00"), 1L);
        assertNotNull(saleAId);
        assertEquals(1L, jdbcLong("SELECT COUNT(*) FROM order_item_batch_allocation"),
                "Sale A 应产生 1 条归属行");
        assertEquals("RULE_ALLOCATED",
                jdbcString("SELECT evidence_level FROM order_item_batch_allocation ORDER BY id LIMIT 1"));
        assertEquals("FLOOR_SINGLE_BATCH",
                jdbcString("SELECT allocation_rule FROM order_item_batch_allocation ORDER BY id LIMIT 1"));
        assertEquals("R1",
                jdbcString("SELECT allocation_rule_version FROM order_item_batch_allocation ORDER BY id LIMIT 1"));
        assertEquals(r1BatchId,
                jdbcLong("SELECT batch_id FROM order_item_batch_allocation ORDER BY id LIMIT 1"));
        assertEquals(1L, jdbcLong("SELECT location_id FROM order_item_batch_allocation ORDER BY id LIMIT 1"));
        assertDecimal("Sale A 归属基准量 2",
                "2", jdbcDecimal("SELECT allocated_base_quantity FROM order_item_batch_allocation ORDER BY id LIMIT 1"));
        assertDecimal("Sale A 批次单位成本 5.00",
                "5.00", jdbcDecimal("SELECT batch_unit_cost FROM order_item_batch_allocation ORDER BY id LIMIT 1"));
        assertDecimal("Sale A 归属成本 10.00",
                "10.00", jdbcDecimal("SELECT allocated_cost FROM order_item_batch_allocation ORDER BY id LIMIT 1"));
        // 新链直接销售不扣减余额、不写 movement
        assertDecimal("直接销售不扣前仓余额（仍 10）",
                "10", jdbcDecimal("SELECT base_quantity FROM inventory_batch_balance WHERE batch_id=" + r1BatchId + " AND location_id=1"));
        assertEquals(1L, jdbcLong("SELECT COUNT(*) FROM inventory_movement WHERE store_id=1"));
        log("[step4b] Sale A orderId=" + saleAId + " → RULE_ALLOCATED/FLOOR_SINGLE_BATCH");

        // 4c. 第二次收货（日期 KNOWN）制造多批次：PO-E2E-2 收 1 箱
        Long receiptId2 = purchaseApplicationService.receive(
                receiveCmd(2L, 2L, "SUP-BATCH-2", "箱", "1", "90.00",
                        LocalDate.of(2026, 9, 1), LocalDate.of(2027, 9, 1)), 1L);
        assertNotNull(receiptId2);
        long r2BatchId = jdbcLong("SELECT id FROM inventory_receipt_batch WHERE store_id=1 AND product_id=101 ORDER BY id DESC LIMIT 1");
        assertDecimal("R2 base_quantity 应为 20",
                "20", jdbcDecimal("SELECT base_quantity FROM inventory_receipt_batch WHERE id=" + r2BatchId));
        assertEquals("KNOWN", jdbcString("SELECT date_status FROM inventory_receipt_batch WHERE id=" + r2BatchId));
        assertDecimal("R2 base_unit_cost 应为 4.50",
                "4.50", jdbcDecimal("SELECT base_unit_cost FROM inventory_receipt_batch WHERE id=" + r2BatchId));
        assertDecimal("R2 后仓余额应为 20",
                "20", jdbcDecimal("SELECT base_quantity FROM inventory_batch_balance WHERE batch_id=" + r2BatchId + " AND location_id=2"));
        assertDecimal("两次收货后快照应为 60",
                "60", jdbcDecimal("SELECT quantity FROM inventory WHERE store_id=1 AND product_id=101"));
        assertEquals(1L, jdbcLong("SELECT COUNT(*) FROM inventory_movement WHERE store_id=1"),
                "第二次收货同样不写 movement");
        log("[step4c] R2 batchId=" + r2BatchId);

        // 4d. R2 补 6 瓶到前仓 → 前仓合计 16 < 20（1 箱）
        batchBalanceService.move(new BatchBalanceService.MoveRequest(
                r2BatchId, 2L, 1L, BatchBalanceService.MovementPurpose.REPLENISHMENT,
                null, 1L, "瓶", new BigDecimal("6"), new BigDecimal("6"), new BigDecimal("4.50"),
                null, null, 1L, "E2E 前仓补货 2", null, null));
        assertDecimal("R2 后仓 20→14",
                "14", jdbcDecimal("SELECT base_quantity FROM inventory_batch_balance WHERE batch_id=" + r2BatchId + " AND location_id=2"));
        assertDecimal("R2 前仓 0→6",
                "6", jdbcDecimal("SELECT base_quantity FROM inventory_batch_balance WHERE batch_id=" + r2BatchId + " AND location_id=1"));
        assertEquals(2L, jdbcLong("SELECT COUNT(*) FROM inventory_movement WHERE store_id=1"));

        // 4e. Sale B：1 箱（20 基准），前仓 16 不足 → UNKNOWN + pending 取货请求
        Long saleBId = orderApplicationService.createPOSOrder(
                posSale(101L, "1", "箱", "100.00", false, null, "100.00"), 1L);
        assertNotNull(saleBId);
        assertEquals(2L, jdbcLong("SELECT COUNT(*) FROM order_item_batch_allocation"),
                "Sale B 应新增 1 条归属行");
        assertEquals("UNKNOWN",
                jdbcString("SELECT evidence_level FROM order_item_batch_allocation ORDER BY id DESC LIMIT 1"));
        long reqBId = jdbcLong("SELECT id FROM order_pickup_request ORDER BY id DESC LIMIT 1");
        assertDecimal("reqB 请求基准量 20",
                "20", jdbcDecimal("SELECT requested_base_quantity FROM order_pickup_request WHERE id=" + reqBId));
        assertEquals(2L, jdbcLong("SELECT source_location_id FROM order_pickup_request WHERE id=" + reqBId));
        assertEquals("PENDING", jdbcString("SELECT status FROM order_pickup_request WHERE id=" + reqBId));
        assertEquals(reqBId, jdbcLong("SELECT pickup_request_id FROM order_item_batch_allocation ORDER BY id DESC LIMIT 1"));
        assertEquals(2L, jdbcLong("SELECT COUNT(*) FROM inventory_movement WHERE store_id=1"),
                "UNKNOWN 登记不得产生 movement");
        log("[step4e] Sale B orderId=" + saleBId + " → UNKNOWN, reqB=" + reqBId);

        // 4f. SCANNED：扫 R1 内部批次标签履约
        PickupFulfillmentCommand scanCmd = new PickupFulfillmentCommand();
        scanCmd.setScannedCode(r1InternalNo);
        List<?> fulfilled = pickupFulfillmentService.fulfill(reqBId, scanCmd, 1L);
        assertEquals(1, fulfilled.size(), "履约应回写 1 条最终归属");
        assertEquals("FULFILLED", jdbcString("SELECT status FROM order_pickup_request WHERE id=" + reqBId));
        assertEquals("SCANNED",
                jdbcString("SELECT evidence_level FROM order_item_batch_allocation ORDER BY id DESC LIMIT 1"));
        assertEquals("PICK_SCAN",
                jdbcString("SELECT allocation_rule FROM order_item_batch_allocation ORDER BY id DESC LIMIT 1"));
        assertEquals(r1BatchId, jdbcLong("SELECT batch_id FROM order_item_batch_allocation ORDER BY id DESC LIMIT 1"));
        assertDecimal("SCANNED 归属成本 100.00（20×5.00）",
                "100.00", jdbcDecimal("SELECT allocated_cost FROM order_item_batch_allocation ORDER BY id DESC LIMIT 1"));
        assertEquals(2L, jdbcLong("SELECT COUNT(*) FROM order_item_batch_allocation"),
                "SCANNED 应回写既有 UNKNOWN 行而非新增");
        assertEquals(3L, jdbcLong("SELECT COUNT(*) FROM inventory_movement WHERE store_id=1"),
                "SCANNED 履约应产生 1 条 PICK movement");
        assertEquals("PICK", jdbcString("SELECT movement_type FROM inventory_movement ORDER BY id DESC LIMIT 1"));
        assertEquals(2L, jdbcLong("SELECT from_location_id FROM inventory_movement ORDER BY id DESC LIMIT 1"));
        assertEquals(1L, jdbcLong("SELECT to_location_id FROM inventory_movement ORDER BY id DESC LIMIT 1"));
        assertDecimal("PICK 基准量 20",
                "20", jdbcDecimal("SELECT base_quantity FROM inventory_movement ORDER BY id DESC LIMIT 1"));
        assertDecimal("PICK 原始量 1（箱）",
                "1", jdbcDecimal("SELECT original_quantity FROM inventory_movement ORDER BY id DESC LIMIT 1"));
        assertEquals("箱", jdbcString("SELECT original_unit_name FROM inventory_movement ORDER BY id DESC LIMIT 1"));
        assertEquals(jdbcLong("SELECT id FROM inventory_movement ORDER BY id DESC LIMIT 1"),
                jdbcLong("SELECT source_movement_id FROM order_item_batch_allocation ORDER BY id DESC LIMIT 1"));
        assertDecimal("PICK 后 R1 后仓 10",
                "10", jdbcDecimal("SELECT base_quantity FROM inventory_batch_balance WHERE batch_id=" + r1BatchId + " AND location_id=2"));
        assertDecimal("PICK 后 R1 前仓 30",
                "30", jdbcDecimal("SELECT base_quantity FROM inventory_batch_balance WHERE batch_id=" + r1BatchId + " AND location_id=1"));
        log("[step4f] SCANNED 履约完成: movementId=" + jdbcLong("SELECT id FROM inventory_movement ORDER BY id DESC LIMIT 1"));

        // ============ 步骤 5：改价留痕（88.88 vs 服务端 100.00 → 差价 -11.12） ============
        Long saleCId = orderApplicationService.createPOSOrder(
                posSale(101L, "1", "箱", "88.88", true, "E2E人工改价", "88.88"), 1L);
        assertNotNull(saleCId);
        assertEquals(1L, jdbcLong("SELECT COUNT(*) FROM order_item_price_audit"),
                "改价应产生 1 条价格审计");
        assertEquals(2L, jdbcLong("SELECT sale_unit_id FROM order_item_price_audit ORDER BY id LIMIT 1"),
                "销售单位为箱（product_unit id=2）");
        assertDecimal("建议单价 100.00",
                "100.00", jdbcDecimal("SELECT suggested_unit_price FROM order_item_price_audit ORDER BY id LIMIT 1"));
        assertDecimal("最终单价 88.88",
                "88.88", jdbcDecimal("SELECT final_unit_price FROM order_item_price_audit ORDER BY id LIMIT 1"));
        assertDecimal("差价 -11.12",
                "-11.12", jdbcDecimal("SELECT price_difference FROM order_item_price_audit ORDER BY id LIMIT 1"));
        assertEquals(1L, jdbcLong("SELECT operator_id FROM order_item_price_audit ORDER BY id LIMIT 1"));
        assertNotNull(jdbcString("SELECT change_reason FROM order_item_price_audit ORDER BY id LIMIT 1"),
                "改价原因应留痕");
        log("[step5] 改价审计 change_reason=" + jdbcString("SELECT change_reason FROM order_item_price_audit ORDER BY id LIMIT 1"));

        // Sale C 是 warehousePickup=true → UNKNOWN + pending reqC（保留 PENDING 供步骤 10 使用）
        long reqCId = jdbcLong("SELECT id FROM order_pickup_request ORDER BY id DESC LIMIT 1");
        assertEquals("PENDING", jdbcString("SELECT status FROM order_pickup_request WHERE id=" + reqCId));
        assertEquals(3L, jdbcLong("SELECT COUNT(*) FROM order_item_batch_allocation"));
        assertEquals(3L, jdbcLong("SELECT COUNT(*) FROM inventory_movement WHERE store_id=1"));
        log("[step5] Sale C orderId=" + saleCId + " → UNKNOWN, reqC=" + reqCId);

        // ============ 步骤 6：日期 UNKNOWN 风险建议（纯建议、无自动阻断） ============
        List<InventoryRiskVO> risks = inventoryRiskService.list(1L);
        InventoryRiskVO dateUnknown = risks.stream()
                .filter(r -> "DATE_UNKNOWN".equals(r.getKind()) && r1BatchId == (r.getBatchId() == null ? -1L : r.getBatchId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("风险列表应包含 R1 批次的 DATE_UNKNOWN 建议，实际: " + risks));
        assertEquals("UNKNOWN", dateUnknown.getDateStatus());
        assertNotNull(dateUnknown.getCurrentStock());
        log("[step6] /risks 同源风险条目 kind=" + dateUnknown.getKind()
                + " productId=" + dateUnknown.getProductId()
                + " batchId=" + dateUnknown.getBatchId()
                + " dateStatus=" + dateUnknown.getDateStatus()
                + " currentStock=" + dateUnknown.getCurrentStock() + "（纯建议，无自动阻断）");
        assertEquals(0L, jdbcLong("SELECT COUNT(*) FROM inventory_decision_log"),
                "风险建议不得自动产生人工决策记录");

        // ============ 步骤 7：L1/L2/L3 对账（与 verify-retail-fact-chain.sh 同源数学，tolerance 0） ============
        BigDecimal snapshot = jdbcDecimal("SELECT quantity FROM inventory WHERE store_id=1 AND product_id=101");
        BigDecimal sumBase = jdbcDecimal("SELECT COALESCE(SUM(base_quantity),0) FROM inventory_batch_balance WHERE store_id=1 AND product_id=101");
        // L1：启用后 factor 按 1 处理 ⇒ Σbase == 快照换算
        assertDecimal("L1: Σbalance 应等于 legacy 快照换算（factor=1）", snapshot.toPlainString(), sumBase);
        log("[L1] Σinventory_batch_balance.base_quantity=" + sumBase.toPlainString()
                + " legacy 快照=" + snapshot.toPlainString() + " factor=1 → OK");
        // L2：启用快照 == Σbase
        assertDecimal("L2: 快照应等于 Σbalance", sumBase.toPlainString(), snapshot);
        log("[L2] inventory.quantity=" + snapshot.toPlainString() + " == Σbase=" + sumBase.toPlainString() + " → OK");

        // L3（per (batch, location)）：balance == receipt_term + net_movements
        // 与 R9 脚本同源：收货项只记在该批次门店“启用的 STORE_BACKROOM”货位上，其余货位记 0
        // （脚本 SQL_L3 中 rt.batch_id = bal.batch_id AND rt.location_id = bal.location_id）。
        Map<String, BigDecimal> receiptTermByKey = new LinkedHashMap<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT b.id, l.id, b.base_quantity FROM inventory_receipt_batch b "
                             + "JOIN inventory_location l ON l.store_id=b.store_id "
                             + "AND l.location_type='STORE_BACKROOM' AND l.status=1 "
                             + "WHERE b.store_id=1 AND b.product_id=101");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                receiptTermByKey.put(rs.getLong(1) + ":" + rs.getLong(2), rs.getBigDecimal(3));
            }
        }
        Map<String, BigDecimal> netMovements = new LinkedHashMap<>();
        try (Connection conn = dataSource.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT batch_id, to_location_id, SUM(base_quantity) FROM inventory_movement "
                     + "WHERE product_id=101 AND to_location_id IS NOT NULL GROUP BY batch_id, to_location_id")) {
            while (rs.next()) {
                netMovements.merge(rs.getLong(1) + ":" + rs.getLong(2), rs.getBigDecimal(3), BigDecimal::add);
            }
        }
        try (Connection conn = dataSource.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT batch_id, from_location_id, SUM(base_quantity) FROM inventory_movement "
                     + "WHERE product_id=101 AND from_location_id IS NOT NULL GROUP BY batch_id, from_location_id")) {
            while (rs.next()) {
                netMovements.merge(rs.getLong(1) + ":" + rs.getLong(2),
                        rs.getBigDecimal(3).negate(), BigDecimal::add);
            }
        }
        int l3Rows = 0;
        try (Connection conn = dataSource.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT batch_id, location_id, base_quantity FROM inventory_batch_balance "
                     + "WHERE store_id=1 AND product_id=101 ORDER BY batch_id, location_id")) {
            while (rs.next()) {
                long batchId = rs.getLong("batch_id");
                long locationId = rs.getLong("location_id");
                BigDecimal balance = rs.getBigDecimal("base_quantity");
                BigDecimal receiptTerm = receiptTermByKey.getOrDefault(batchId + ":" + locationId, BigDecimal.ZERO);
                BigDecimal net = netMovements.getOrDefault(batchId + ":" + locationId, BigDecimal.ZERO);
                BigDecimal expected = receiptTerm.add(net);
                log("[L3] batch=" + batchId + " loc=" + locationId
                        + " balance=" + balance.toPlainString()
                        + " receipt_term=" + receiptTerm.toPlainString()
                        + " net_movements=" + net.toPlainString());
                assertDecimal("L3: batch=" + batchId + " loc=" + locationId + " balance != receipt_term+net",
                        expected.toPlainString(), balance);
                l3Rows++;
            }
        }
        assertEquals(4, l3Rows, "核心 SKU 应有 4 条 (batch,location) 余额行");
        // ORPHAN 方向检查：movement 的 (batch, location) 端点必须有余额行
        int orphanGroups = 0;
        try (Connection conn = dataSource.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT batch_id, from_location_id, to_location_id FROM inventory_movement "
                     + "WHERE product_id=101")) {
            while (rs.next()) {
                long batchId = rs.getLong("batch_id");
                long from = rs.getLong("from_location_id");
                boolean fromNull = rs.wasNull();
                long to = rs.getLong("to_location_id");
                boolean toNull = rs.wasNull();
                if (!fromNull && jdbcLong("SELECT COUNT(*) FROM inventory_batch_balance WHERE batch_id=" + batchId
                        + " AND location_id=" + from) == 0) {
                    orphanGroups++;
                }
                if (!toNull && jdbcLong("SELECT COUNT(*) FROM inventory_batch_balance WHERE batch_id=" + batchId
                        + " AND location_id=" + to) == 0) {
                    orphanGroups++;
                }
            }
        }
        assertEquals(0, orphanGroups, "不应存在无余额行的移动孤儿组");
        log("[L3] 无余额行的移动孤儿组=" + orphanGroups + "（ORPHAN 方向检查通过；断言范围仅核心 SKU 101，对照 SKU 102 已排除）");

        // ============ 步骤 8：回滚 = save(LEGACY)，cutover 留痕保留、事实行计数不变 ============
        long receiptsBefore = jdbcLong("SELECT COUNT(*) FROM inventory_receipt_batch WHERE store_id=1");
        long balancesBefore = jdbcLong("SELECT COUNT(*) FROM inventory_batch_balance WHERE store_id=1");
        long movementsBefore = jdbcLong("SELECT COUNT(*) FROM inventory_movement WHERE store_id=1");
        long allocationsBefore = jdbcLong("SELECT COUNT(*) FROM order_item_batch_allocation WHERE store_id=1");
        long auditsBefore = jdbcLong("SELECT COUNT(*) FROM order_item_price_audit WHERE product_id=101");
        assertEquals(2L, receiptsBefore);
        assertEquals(4L, balancesBefore);
        assertEquals(3L, movementsBefore);
        assertEquals(3L, allocationsBefore);
        assertEquals(1L, auditsBefore);

        Timestamp cutoverBefore = jdbcTimestamp("SELECT cutover_at FROM inventory_balance_feature_status WHERE store_id=1 AND product_id=101");
        Timestamp updatedBefore = jdbcTimestamp("SELECT updated_at FROM inventory_balance_feature_status WHERE store_id=1 AND product_id=101");
        assertNotNull(cutoverBefore, "回滚前 cutover_at 应已落值");
        assertNotNull(updatedBefore);

        // updated_at 为秒级精度 DATETIME：推进时钟，保证回滚写入严格晚于激活写入，供下方 after 断言
        Thread.sleep(1100);
        InventoryBalanceFeatureStatus feature = featureStatusRepository.findByStoreAndProduct(1L, 101L).orElseThrow();
        feature.setBalanceMode(InventoryBalanceFeatureStatus.LEGACY);
        featureStatusRepository.save(feature);

        assertEquals("LEGACY",
                jdbcString("SELECT balance_mode FROM inventory_balance_feature_status WHERE store_id=1 AND product_id=101"),
                "回滚应将 balance_mode 置回 LEGACY");
        assertEquals(cutoverBefore,
                jdbcTimestamp("SELECT cutover_at FROM inventory_balance_feature_status WHERE store_id=1 AND product_id=101"),
                "回滚不得清空 cutover_at");
        assertEquals(1L,
                jdbcLong("SELECT cutover_by FROM inventory_balance_feature_status WHERE store_id=1 AND product_id=101"),
                "回滚不得清空 cutover_by");
        Timestamp updatedAfter = jdbcTimestamp("SELECT updated_at FROM inventory_balance_feature_status WHERE store_id=1 AND product_id=101");
        assertTrue(updatedAfter.after(updatedBefore), "回滚写入应刷新 updated_at");
        assertEquals(receiptsBefore, jdbcLong("SELECT COUNT(*) FROM inventory_receipt_batch WHERE store_id=1"));
        assertEquals(balancesBefore, jdbcLong("SELECT COUNT(*) FROM inventory_batch_balance WHERE store_id=1"));
        assertEquals(movementsBefore, jdbcLong("SELECT COUNT(*) FROM inventory_movement WHERE store_id=1"));
        assertEquals(allocationsBefore, jdbcLong("SELECT COUNT(*) FROM order_item_batch_allocation WHERE store_id=1"));
        assertEquals(auditsBefore, jdbcLong("SELECT COUNT(*) FROM order_item_price_audit WHERE product_id=101"));
        log("[step8] 回滚完成: balance_mode=LEGACY, cutover_at/cutover_by 保留, 5 张事实表计数不变");

        // 回滚后 legacy 入口恢复：补一条 legacy 批次后走 legacy 出库成功（brief §4 步骤 8 授权）
        exec("INSERT INTO inventory_batch (store_id, product_id, batch_no, quantity, cost_price, is_active) "
                + "VALUES (1, 101, 'LB-ROLLBACK', 1, 5.00, 1)");
        inventoryApplicationService.outbound(outCmd(101L, "1", "箱"), 1L);
        assertDecimal("回滚后 legacy 出库应扣减 LB-ROLLBACK 为 0",
                "0", jdbcDecimal("SELECT quantity FROM inventory_batch WHERE store_id=1 AND product_id=101 AND batch_no='LB-ROLLBACK'"));
        assertEquals(0L, jdbcLong("SELECT is_active FROM inventory_batch WHERE store_id=1 AND product_id=101 AND batch_no='LB-ROLLBACK'"),
                "耗尽批次应归档 is_active=0");
        log("[step8] 回滚后 legacy 出库成功（快照口径变化属预期，本步不断言）");

        // ============ 步骤 9：M4 HTTP 端点（MockMvc，登录拿 token 后带 header） ============
        String loginJson = mockMvc.perform(get("/__e2e__/login").param("staffId", "1"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode login = objectMapper.readTree(loginJson);
        String tokenName = login.path("tokenName").asText();
        String tokenValue = login.path("tokenValue").asText();
        assertTrue(tokenName != null && !tokenName.isEmpty(), "登录应返回 tokenName");
        assertTrue(tokenValue != null && !tokenValue.isEmpty(), "登录应返回 tokenValue");
        log("[step9] 登录成功 tokenName=" + tokenName);

        String risksJson = mockMvc.perform(get("/api/admin/inventory/warning/risks")
                        .param("storeId", "1")
                        .header(tokenName, tokenValue))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode risksRoot = objectMapper.readTree(risksJson);
        assertEquals("200", risksRoot.path("code").asText(), "风险查询应返回 code=200");
        JsonNode risksData = risksRoot.path("data");
        assertTrue(risksData.isArray() && risksData.size() >= 1, "风险列表应非空，实际: " + risksJson);
        boolean foundDateUnknown = false;
        for (JsonNode item : risksData) {
            assertTrue(item.hasNonNull("kind"), "风险条目必须含 kind: " + item);
            if ("DATE_UNKNOWN".equals(item.path("kind").asText())) {
                foundDateUnknown = true;
            }
        }
        assertTrue(foundDateUnknown, "风险列表应包含 DATE_UNKNOWN 条目");
        log("[step9] GET /api/admin/inventory/warning/risks → data 条数=" + risksData.size() + "，含 DATE_UNKNOWN");

        Map<String, Object> decisionPayload = new HashMap<>();
        decisionPayload.put("riskKind", "DATE_UNKNOWN");
        decisionPayload.put("entityType", "BATCH");
        decisionPayload.put("entityId", r1BatchId);
        decisionPayload.put("decisionAction", "CONTINUE_SALE");
        decisionPayload.put("decisionReason", "E2E日期未知人工确认继续销售");
        String decisionsJson = mockMvc.perform(post("/api/admin/inventory/decisions")
                        .header(tokenName, tokenValue)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(decisionPayload)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode decisionsRoot = objectMapper.readTree(decisionsJson);
        assertEquals("200", decisionsRoot.path("code").asText(), "决策记录应返回 code=200");
        JsonNode decisionId = decisionsRoot.path("data").path("decisionId");
        assertTrue(decisionId.isNumber() && decisionId.asLong() > 0,
                "决策应返回 data.decisionId，实际: " + decisionsJson);
        assertEquals(1L, jdbcLong("SELECT COUNT(*) FROM inventory_decision_log"),
                "人工决策应留痕 1 条");
        log("[step9] POST /api/admin/inventory/decisions → decisionId=" + decisionId.asLong());

        // ============ 步骤 10：M2 边界（service 级） ============
        assertTrue(pickupFulfillmentService.listPickupRequests(9999L, null).isEmpty(),
                "无请求门店应返回空列表");
        PickupFulfillmentCommand blankCmd = new PickupFulfillmentCommand();
        blankCmd.setScannedCode("   ");
        IllegalArgumentException blankEx = assertThrows(IllegalArgumentException.class,
                () -> pickupFulfillmentService.fulfill(reqCId, blankCmd, 1L),
                "空白扫码应被拒绝");
        assertTrue(blankEx.getMessage() != null && blankEx.getMessage().contains("必须扫描内部批次标签"),
                "空白扫码拒绝消息应提示必须扫描内部批次标签，实际: " + blankEx.getMessage());
        PickupFulfillmentCommand emptyLocCmd = new PickupFulfillmentCommand();
        emptyLocCmd.setScannedCode("EMPTY-LOC");
        IllegalArgumentException locEx = assertThrows(IllegalArgumentException.class,
                () -> pickupFulfillmentService.fulfill(reqCId, emptyLocCmd, 1L),
                "货位存在但无可用批次应被拒绝");
        assertTrue(locEx.getMessage() != null && locEx.getMessage().contains("扫描码不能解析为内部批次标签"),
                "无批次货位扫码拒绝消息应说明无法解析，实际: " + locEx.getMessage());
        assertEquals("PENDING", jdbcString("SELECT status FROM order_pickup_request WHERE id=" + reqCId),
                "被拒的履约不得改变请求状态");
        assertEquals(3L, jdbcLong("SELECT COUNT(*) FROM order_item_batch_allocation"),
                "被拒的履约不得写归属行");
        log("[step10] 边界检查通过: 空列表/空白扫码拒绝/无批次货位拒绝");

        log("十步弧线全部通过");
    }

    // ------------------------------------------------------------------
    // 夹具（真实 NOT NULL 约束的最小集；legacy inventory 无 unit 列）
    // ------------------------------------------------------------------
    private void seedFixture() {
        exec("INSERT INTO store (id, code, name, status) VALUES (1, 'E2E-STORE', '端到端门店', 1)");
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
                + "(3, 1, 'OTHER_STORE', 'EMPTY-LOC', '空对照货位', 1)");
        // legacy inventory 快照：核心 SKU 3 箱（主单位），对照 SKU 10 袋
        exec("INSERT INTO inventory (store_id, product_id, quantity) VALUES (1, 101, 3), (1, 102, 10)");
        exec("INSERT INTO inventory_batch (store_id, product_id, batch_no, quantity, cost_price, is_active) VALUES "
                + "(1, 101, 'LB-A', 2, 5.00, 1), "
                + "(1, 101, 'LB-B', 1, 5.00, 1), "
                + "(1, 102, 'LB-C', 10, 3.00, 1)");
        exec("INSERT INTO purchase_order (id, purchase_no, supplier_id, store_id, total_amount, status) VALUES "
                + "(1, 'PO-E2E-1', 1, 1, 200.00, 2), "
                + "(2, 'PO-E2E-2', 1, 1, 90.00, 2)");
        exec("INSERT INTO purchase_order_item (id, purchase_order_id, product_id, quantity, unit, buy_price, total_price, sort_order) VALUES "
                + "(1, 1, 101, 2, '箱', 100.00, 200.00, 1), "
                + "(2, 2, 101, 1, '箱', 90.00, 90.00, 1)");
        log("[fixture] 夹具就绪: store=1 staff=1 product=101(箱/瓶/20) + 对照 102, legacy 快照 3 箱, LB-A/LB-B");
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
    // 保持 MockMvc 未鉴权先例），只 import M4 两个 controller + 登录端点 + 领域服务装配。
    // ------------------------------------------------------------------
    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EnableCaching
    @MapperScan("com.agri_system.infrastructure")
    @ComponentScan(basePackages = {"com.agri_system.application", "com.agri_system.infrastructure"},
            excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SaTokenConfigure.class))
    @Import({InventoryController.class, InventoryWarningController.class,
            RetailFactChainEndToEndTest.E2ELoginController.class, DomainServiceConfig.class})
    static class E2EConfig {

        @Bean
        @Primary
        StpInterface e2eStpInterface() {
            return new StpInterface() {
                @Override
                public List<String> getPermissionList(Object loginId, String loginType) {
                    return List.of("*");
                }

                @Override
                public List<String> getRoleList(Object loginId, String loginType) {
                    return List.of("E2E");
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
