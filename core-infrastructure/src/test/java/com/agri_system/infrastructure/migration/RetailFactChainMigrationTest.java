package com.agri_system.infrastructure.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RetailFactChainMigrationTest {

    private static DataSource dataSource;
    private static Flyway flyway;

    @BeforeAll
    static void migrateFromLegacySchema() {
        org.h2.jdbcx.JdbcDataSource h2 = new org.h2.jdbcx.JdbcDataSource();
        h2.setURL("jdbc:h2:mem:retail_fact_chain;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false");
        h2.setUser("sa");
        h2.setPassword("");
        dataSource = h2;

        new JdbcTemplate(dataSource).execute("""
                CREATE TABLE `store` (`id` BIGINT PRIMARY KEY);
                CREATE TABLE `staff` (`id` BIGINT PRIMARY KEY);
                CREATE TABLE `supplier` (`id` BIGINT PRIMARY KEY);
                CREATE TABLE `purchase_order` (`id` BIGINT PRIMARY KEY);
                CREATE TABLE `purchase_order_item` (
                    `id` BIGINT PRIMARY KEY,
                    `product_id` BIGINT NOT NULL
                );
                CREATE TABLE `product` (
                    `id` BIGINT PRIMARY KEY,
                    `main_unit` VARCHAR(20) NOT NULL,
                    `aux_unit` VARCHAR(20),
                    `main_to_aux_ratio` DECIMAL(10,4)
                );
                CREATE TABLE `inventory_batch` (
                    `id` BIGINT PRIMARY KEY,
                    `quantity` DECIMAL(18,8) NOT NULL
                );
                CREATE TABLE `order_item` (
                    `id` BIGINT PRIMARY KEY,
                    `product_id` BIGINT NOT NULL,
                    `unit` VARCHAR(20) NOT NULL
                );
                -- 合并 master 后 V122(提成冻结索引)进入 V119-V124 迁移路径,基线需含其依赖表(V118 后最小投影)
                CREATE TABLE `staff_performance_log` (
                    `id` BIGINT PRIMARY KEY,
                    `staff_id` BIGINT NOT NULL,
                    `status` TINYINT NOT NULL DEFAULT 2,
                    `freeze_until` DATETIME NULL
                );
                -- V126(按销售单位报价)对 product_price 加列,基线(V118)早于 V7 建表路径,需最小投影
                CREATE TABLE `product_price` (
                    `id` BIGINT PRIMARY KEY
                );
                -- V127(作业界面菜单)向 sys_menu/sys_role_menu 写入菜单行,基线(V118)无这两张表,需最小投影
                CREATE TABLE `sys_menu` (
                    `id` BIGINT PRIMARY KEY,
                    `parent_id` BIGINT,
                    `name` VARCHAR(64) NOT NULL,
                    `permission` VARCHAR(64),
                    `type` TINYINT NOT NULL DEFAULT 1,
                    `sort_order` INT NOT NULL DEFAULT 0,
                    `path` VARCHAR(128),
                    `icon` VARCHAR(64),
                    `visible` TINYINT NOT NULL DEFAULT 1
                );
                CREATE TABLE `sys_role_menu` (
                    `role_id` BIGINT NOT NULL,
                    `menu_id` BIGINT NOT NULL,
                    PRIMARY KEY (`role_id`, `menu_id`)
                );
                -- V128(补页面菜单)按「已持有按钮权限的角色」推导授权,基线需含对应按钮与授权行
                INSERT INTO `sys_menu` (`id`, `parent_id`, `name`, `permission`, `type`, `sort_order`, `path`, `icon`, `visible`) VALUES
                    (203, 200, '调拨', 'inventory:transfer', 2, 30, NULL, NULL, 1),
                    (206, 200, '库存日志', 'inventory:log:list', 2, 60, NULL, NULL, 1),
                    (1409, 0, '客户跟进管理', 'customer:followup:manage', 2, 130, NULL, NULL, 1),
                    (2600, 0, '工作台', 'report:dashboard', 1, 10, '/dashboard', 'Odometer', 1);
                INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (3, 203), (3, 206), (4, 1409);
                INSERT INTO `store` (`id`) VALUES (1), (2);
                INSERT INTO `staff` (`id`) VALUES (1);
                INSERT INTO `supplier` (`id`) VALUES (1);
                INSERT INTO `purchase_order` (`id`) VALUES (1);
                INSERT INTO `product` (`id`, `main_unit`, `aux_unit`, `main_to_aux_ratio`)
                    VALUES (1, '箱', '瓶', 24);
                INSERT INTO `product` (`id`, `main_unit`, `aux_unit`, `main_to_aux_ratio`)
                    VALUES (2, '袋', '克', 500);
                INSERT INTO `purchase_order_item` (`id`, `product_id`) VALUES (1, 1), (2, 2);
                INSERT INTO `inventory_batch` (`id`, `quantity`) VALUES (1, 12.00000000);
                INSERT INTO `order_item` (`id`, `product_id`, `unit`) VALUES (1, 1, '箱');
                """);

        flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("118")
                .target(MigrationVersion.fromVersion("128"))
                .load();
        flyway.migrate();
        createH2AppendOnlyTriggers();
    }

    @BeforeEach
    void resetFactTables() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        dropH2AppendOnlyTriggers(jdbc);
        jdbc.update("UPDATE order_item SET base_unit_id = NULL, base_quantity = NULL, unit_cost = NULL");
        jdbc.update("DELETE FROM order_item_price_audit");
        jdbc.update("DELETE FROM inventory_decision_log");
        jdbc.update("DELETE FROM order_item_batch_allocation");
        jdbc.update("DELETE FROM order_pickup_request");
        jdbc.update("DELETE FROM inventory_movement");
        jdbc.update("DELETE FROM inventory_batch_balance");
        jdbc.update("DELETE FROM inventory_balance_feature_status");
        jdbc.update("DELETE FROM inventory_receipt_batch");
        jdbc.update("DELETE FROM inventory_receipt_line");
        jdbc.update("DELETE FROM inventory_receipt");
        jdbc.update("DELETE FROM inventory_location");
        jdbc.update("DELETE FROM product_unit");
        createH2AppendOnlyTriggers(jdbc);
    }

    @AfterAll
    static void closeDatabase() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.createStatement().execute("SHUTDOWN");
        }
    }

    @Test
    void retailFactChainMigrationCreatesUniqueUnitAndBatchBalanceConstraints() throws SQLException {
        assertTableExists("product_unit");
        assertTableExists("inventory_batch_balance");
        assertUniqueIndex("product_unit", "uk_product_unit_name", "product_id", "unit_name");
        assertUniqueIndex("product_unit", "uk_product_enabled_base_unit", "product_id", "enabled_base_unit_key");
        assertUniqueIndex("inventory_batch_balance", "uk_batch_location", "batch_id", "location_id");
        assertUniqueIndex("inventory_receipt_line", "uk_inventory_receipt_line_product", "id", "product_id");
        assertUniqueIndex("inventory_receipt_line", "uk_inventory_receipt_line_store", "id", "store_id");
        assertUniqueIndex("inventory_receipt_batch", "uk_inventory_receipt_batch_product", "id", "product_id");
        assertUniqueIndex("inventory_receipt_batch", "uk_inventory_receipt_batch_store", "id", "store_id");
        assertUniqueIndex("inventory_location", "uk_inventory_location_id_store", "id", "store_id");
        assertIndex("inventory_receipt", "idx_inventory_receipt_supplier", "supplier_id");
        assertIndex("inventory_receipt_batch", "idx_inventory_receipt_batch_supplier_no", "supplier_batch_no");
        assertIndex("inventory_receipt_batch", "idx_inventory_receipt_batch_line", "receipt_line_id");
        assertIndex("inventory_movement", "idx_inventory_movement_reference", "reference_type", "reference_id");
        assertIndex("inventory_movement", "idx_inventory_movement_transfer", "transfer_id", "occurred_at");
        assertUniqueIndex("inventory_movement", "uk_inventory_movement_idempotency", "idempotency_key");
        assertIndex("order_item_batch_allocation", "idx_order_item_batch_allocation_pickup", "pickup_request_id");
        assertIndex("order_pickup_request", "idx_order_pickup_request_status", "store_id", "status");
    }

    @Test
    void retailFactChainMigrationCreatesAllFactTablesAndSnapshots() throws SQLException {
        assertEquals("128", flyway.info().current().getVersion().getVersion());

        List.of(
                "product_unit",
                "inventory_receipt",
                "inventory_receipt_line",
                "inventory_receipt_batch",
                "inventory_location",
                "inventory_batch_balance",
                "inventory_balance_feature_status",
                "inventory_movement",
                "order_pickup_request",
                "order_item_batch_allocation",
                "order_item_price_audit",
                "inventory_decision_log"
        ).forEach(this::assertTableExistsUnchecked);

        assertColumnExists("order_item", "base_unit_id");
        assertColumnExists("order_item", "base_quantity");
        assertColumnExists("order_item", "unit_cost");
        assertColumnExists("order_item_batch_allocation", "pickup_request_id");
    }

    @Test
    void retailFactChainMigrationPreservesLegacyTablesAndColumns() throws SQLException {
        assertColumnExists("product", "main_unit");
        assertColumnExists("product", "aux_unit");
        assertColumnExists("product", "main_to_aux_ratio");
        assertColumnExists("inventory_batch", "quantity");
        assertColumnExists("order_item", "unit");
    }

    @Test
    void retailPricingUnitChannelMigrationAddsQuoteColumns() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        assertInformationSchemaColumn(jdbc, "product_price", "sale_unit");
        assertInformationSchemaColumn(jdbc, "product_price", "channel");
        assertInformationSchemaColumn(jdbc, "product_price", "rule_version");
        assertInformationSchemaColumn(jdbc, "order_item_price_audit", "quote_id");
    }

    @Test
    void workScreenMenuMigrationRegistersFourMenusAndRoleGrants() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        assertEquals(Integer.valueOf(4), jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_menu WHERE id IN (2703, 2704, 2705, 2706)", Integer.class));
        assertMenuRow(jdbc, 2703L, 2000L, "单位设置", "product:unit:query", "/product/units");
        assertMenuRow(jdbc, 2704L, 2100L, "收货", "purchase:inbound", "/purchase/receipt");
        assertMenuRow(jdbc, 2705L, 2100L, "货位", "inventory:view", "/inventory/locations");
        assertMenuRow(jdbc, 2706L, 2100L, "取货", "order:pickup:verify", "/inventory/pickup");
        assertEquals(Integer.valueOf(4), jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_role_menu WHERE role_id = 1 AND menu_id IN (2703, 2704, 2705, 2706)",
                Integer.class));
    }

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

    private void assertMenuRow(JdbcTemplate jdbc, Long id, Long parentId, String name, String permission, String path) {
        assertEquals(parentId, jdbc.queryForObject("SELECT parent_id FROM sys_menu WHERE id = ?", Long.class, id));
        assertEquals(name, jdbc.queryForObject("SELECT name FROM sys_menu WHERE id = ?", String.class, id));
        assertEquals(permission, jdbc.queryForObject("SELECT permission FROM sys_menu WHERE id = ?", String.class, id));
        assertEquals(path, jdbc.queryForObject("SELECT path FROM sys_menu WHERE id = ?", String.class, id));
        assertEquals(Integer.valueOf(1), jdbc.queryForObject("SELECT type FROM sys_menu WHERE id = ?", Integer.class, id));
        assertEquals(Integer.valueOf(1), jdbc.queryForObject("SELECT visible FROM sys_menu WHERE id = ?", Integer.class, id));
        assertEquals(Integer.valueOf(1), jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_role_menu WHERE role_id = 1 AND menu_id = ?", Integer.class, id));
    }

    @Test
    void migrationDirectoryHasUniqueVersionsThroughRetailFactChain() throws Exception {
        Path migrationDirectory = Paths.get(getClass().getClassLoader()
                .getResource("db/migration").toURI());
        Pattern versionPattern = Pattern.compile("^V([0-9_]+)__[^/]+\\.sql$");
        List<Path> migrationFiles;
        try (var paths = Files.list(migrationDirectory)) {
            migrationFiles = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().startsWith("V")
                            && path.getFileName().toString().endsWith(".sql"))
                    .toList();
        }
        List<MigrationVersion> versions = migrationFiles.stream()
                .map(path -> versionPattern.matcher(path.getFileName().toString()))
                .peek(matcher -> assertTrue(matcher.matches(),
                        "无法解析 Flyway 版本迁移文件: " + matcher.group(0)))
                .map(matcher -> MigrationVersion.fromVersion(matcher.group(1).replace('_', '.')))
                .sorted()
                .toList();
        assertTrue(!versions.isEmpty(), "未发现 Flyway 版本迁移文件");
        assertEquals(migrationFiles.size(), versions.size(), "所有 V*.sql 文件都必须被枚举和解析");
        assertEquals(versions.size(), versions.stream().distinct().count(), "迁移版本号必须唯一");
        assertTrue(versions.stream().allMatch(version -> version.compareTo(MigrationVersion.fromVersion("1")) >= 0
                        && version.compareTo(MigrationVersion.fromVersion("141")) <= 0),
                "存在超出 V1..V141 范围的迁移版本");
        assertTrue(versions.containsAll(List.of(MigrationVersion.fromVersion("119"),
                MigrationVersion.fromVersion("120"), MigrationVersion.fromVersion("121"),
                MigrationVersion.fromVersion("123"), MigrationVersion.fromVersion("124"),
                MigrationVersion.fromVersion("125"), MigrationVersion.fromVersion("126"),
                MigrationVersion.fromVersion("127"), MigrationVersion.fromVersion("128"), MigrationVersion.fromVersion("131"),MigrationVersion.fromVersion("134"),MigrationVersion.fromVersion("135"),
                MigrationVersion.fromVersion("136"), MigrationVersion.fromVersion("137"), MigrationVersion.fromVersion("138"),
                MigrationVersion.fromVersion("139"), MigrationVersion.fromVersion("140"),
                MigrationVersion.fromVersion("141"))),
                "缺少 V119/V120/V121/V123/V124/V125/V126/V127/V128/V131/V134/V135/V136/V137/V138/V139/V140/V141");
        assertEquals("128", flyway.info().current().getVersion().getVersion());
    }

    @Test
    void schemaKeepsRetailIndexParity() throws Exception {
        Path schemaPath = List.of(Paths.get("ddl/schema.sql"), Paths.get("../ddl/schema.sql"))
                .stream()
                .filter(Files::exists)
                .findFirst()
                .orElseThrow(() -> new AssertionError("未找到 ddl/schema.sql"));
        String schema = Files.readString(schemaPath);
        assertTrue(schema.contains("KEY `idx_inventory_receipt_supplier` (`supplier_id`)"));
        assertTrue(schema.contains("KEY `idx_inventory_receipt_batch_supplier_no` (`supplier_batch_no`)"));
        assertTrue(schema.contains("KEY `idx_inventory_receipt_batch_line` (`receipt_line_id`)"));
        assertTrue(schema.contains("KEY `idx_inventory_movement_reference` (`reference_type`, `reference_id`)"));
        assertTrue(schema.contains("KEY `idx_inventory_movement_transfer` (`transfer_id`, `occurred_at`)"));
    }

    @Test
    void factForeignKeysRejectCrossSkuUnitBatchMovementAllocationAndPriceAudit() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        long unitOne = insertUnit(jdbc, 1, "瓶", 1);
        long unitTwo = insertUnit(jdbc, 2, "袋", 1);
        long receipt = insertReceipt(jdbc, "R-CROSS", 1);
        long line = insertReceiptLine(jdbc, receipt, 1, 1);
        long batch = insertBatch(jdbc, line, 1, unitOne, unitOne, "B-CROSS", "KNOWN", "2026-01-01", "2026-12-31");
        long location = insertLocation(jdbc, 1, "L-CROSS");
        long otherStoreLocation = insertLocation(jdbc, 2, "L-CROSS-OTHER");

        assertThrows(DataAccessException.class, () -> jdbc.update("""
                INSERT INTO inventory_receipt_batch
                    (receipt_line_id, product_id, store_id, internal_receipt_batch_no,
                     original_unit_id, original_unit_name, original_quantity, original_unit_cost,
                     base_unit_id, base_quantity, base_unit_cost, date_status)
                VALUES (?, 2, 1, 'B-WRONG', ?, '袋', 1, 1, ?, 1, 1, 'KNOWN')
                """, line, unitTwo, unitTwo));

        assertThrows(DataAccessException.class, () -> jdbc.update("""
                INSERT INTO inventory_receipt_line
                    (receipt_id, line_no, purchase_order_item_id, product_id, store_id)
                VALUES (?, 2, 2, 1, 1)
                """, receipt));

        assertThrows(DataAccessException.class, () -> jdbc.update("""
                INSERT INTO inventory_movement
                    (movement_no, batch_id, product_id, store_id, from_location_id,
                     original_unit_id, original_unit_name, original_quantity, unit_cost, base_quantity)
                VALUES ('M-WRONG', ?, 2, 1, ?, ?, '袋', 1, 1, 1)
                """, batch, otherStoreLocation, unitTwo));

        assertThrows(DataAccessException.class, () -> jdbc.update("""
                INSERT INTO inventory_movement
                    (movement_no, batch_id, product_id, store_id, from_location_id,
                     original_unit_id, original_unit_name, original_quantity, unit_cost, base_quantity)
                VALUES ('M-WRONG-LOCATION', ?, 1, 1, ?, ?, '瓶', 1, 1, 1)
                """, batch, otherStoreLocation, unitOne));

        assertThrows(DataAccessException.class, () -> jdbc.update("""
                INSERT INTO order_item_batch_allocation
                    (order_item_id, product_id, store_id, batch_id, location_id, allocated_base_quantity,
                     batch_unit_cost, allocated_cost, evidence_level)
                VALUES (1, 2, 1, ?, ?, 1, 1, 1, 'SCANNED')
                """, batch, location));

        assertThrows(DataAccessException.class, () -> jdbc.update("""
                INSERT INTO order_item_batch_allocation
                    (order_item_id, product_id, store_id, batch_id, location_id, allocated_base_quantity,
                     batch_unit_cost, allocated_cost, evidence_level)
                VALUES (1, 1, 1, ?, ?, 1, 1, 1, 'SCANNED')
                """, batch, otherStoreLocation));

        assertThrows(DataAccessException.class, () -> jdbc.update("""
                INSERT INTO order_item_price_audit
                    (order_item_id, product_id, sale_unit_id, suggested_unit_price,
                     final_unit_price, price_difference)
                VALUES (1, 2, ?, 1, 1, 0)
                """, unitTwo));

        assertThrows(DataAccessException.class, () -> jdbc.update("""
                INSERT INTO inventory_batch_balance
                    (batch_id, product_id, store_id, location_id, base_quantity)
                VALUES (?, 1, 2, ?, 1)
                """, batch, location));
    }

    @Test
    void receiptMovementAndAllocationRejectNonPositiveQuantitiesAndCosts() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        long unit = insertUnit(jdbc, 1, "瓶", 1);
        long receipt = insertReceipt(jdbc, "R-POSITIVE", 1);
        long line = insertReceiptLine(jdbc, receipt, 1, 1);

        assertThrows(DataAccessException.class, () -> insertBatch(jdbc, line, 1, unit, unit,
                "B-ZERO-QTY", "KNOWN", "2026-01-01", "2026-12-31", 0, 1, 1));
        long batch = insertBatch(jdbc, line, 1, unit, unit, "B-POSITIVE", "KNOWN", "2026-01-01", "2026-12-31");
        assertThrows(DataAccessException.class, () -> jdbc.update("""
                INSERT INTO inventory_movement
                    (movement_no, batch_id, product_id, store_id, original_unit_id, original_unit_name,
                     original_quantity, unit_cost, base_quantity)
                VALUES ('M-ZERO-COST', ?, 1, 1, ?, '瓶', 1, 0, 1)
                """, batch, unit));
        assertThrows(DataAccessException.class, () -> jdbc.update("""
                INSERT INTO inventory_movement
                    (movement_no, batch_id, product_id, store_id, original_unit_id, original_unit_name,
                     original_quantity, unit_cost, base_quantity)
                VALUES ('M-ZERO-QTY', ?, 1, 1, ?, '瓶', 0, 1, 0)
                """, batch, unit));
        assertThrows(DataAccessException.class, () -> jdbc.update("""
                INSERT INTO order_item_batch_allocation
                    (order_item_id, product_id, store_id, batch_id, allocated_base_quantity,
                     batch_unit_cost, allocated_cost, evidence_level)
                VALUES (1, 1, 1, ?, 0, 1, 1, 'SCANNED')
                """, batch));
        assertThrows(DataAccessException.class, () -> jdbc.update("""
                INSERT INTO order_item_batch_allocation
                    (order_item_id, product_id, store_id, batch_id, allocated_base_quantity,
                     batch_unit_cost, allocated_cost, evidence_level)
                VALUES (1, 1, 1, ?, 1, 0, 0, 'SCANNED')
                """, batch));
        assertThrows(DataAccessException.class,
                () -> jdbc.update("UPDATE order_item SET base_quantity = 0 WHERE id = 1"));
        assertThrows(DataAccessException.class,
                () -> jdbc.update("UPDATE order_item SET unit_cost = 0 WHERE id = 1"));
    }

    @Test
    void receiptBatchDateStatusRejectsContradictoryOrReversedDates() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        long unit = insertUnit(jdbc, 1, "瓶", 1);
        long receipt = insertReceipt(jdbc, "R-DATE", 1);
        long line = insertReceiptLine(jdbc, receipt, 1, 1);

        assertThrows(DataAccessException.class, () -> insertBatch(jdbc, line, 1, unit, unit,
                "B-UNKNOWN-DATE", "UNKNOWN", "2026-01-01", null));
        assertThrows(DataAccessException.class, () -> insertBatch(jdbc, line, 1, unit, unit,
                "B-KNOWN-MISSING", "KNOWN", null, "2026-12-31"));
        assertThrows(DataAccessException.class, () -> insertBatch(jdbc, line, 1, unit, unit,
                "B-REVERSED", "KNOWN", "2026-12-31", "2026-01-01"));
    }

    @Test
    void appendOnlyAuditAndDecisionTablesRejectUpdateAndDelete() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        long unit = insertUnit(jdbc, 1, "瓶", 1);
        jdbc.update("""
                INSERT INTO order_item_price_audit
                    (order_item_id, product_id, sale_unit_id, suggested_unit_price,
                     final_unit_price, price_difference)
                VALUES (1, 1, ?, 10, 9, -1)
                """, unit);
        jdbc.update("""
                INSERT INTO inventory_decision_log
                    (risk_kind, entity_type, entity_id, decision_action, decision_reason)
                VALUES ('DATE_UNKNOWN', 'BATCH', 1, 'HOLD', '人工复核')
                """);

        assertThrows(DataAccessException.class,
                () -> jdbc.update("UPDATE order_item_price_audit SET final_unit_price = 8 WHERE id = 1"));
        assertThrows(DataAccessException.class,
                () -> jdbc.update("DELETE FROM order_item_price_audit WHERE id = 1"));
        assertThrows(DataAccessException.class,
                () -> jdbc.update("UPDATE inventory_decision_log SET decision_action = 'SELL' WHERE id = 1"));
        assertThrows(DataAccessException.class,
                () -> jdbc.update("DELETE FROM inventory_decision_log WHERE id = 1"));
    }

    private long insertUnit(JdbcTemplate jdbc, long productId, String unitName, int baseQuantity) {
        jdbc.update("""
                INSERT INTO product_unit
                    (product_id, unit_name, base_quantity_per_unit, is_base_unit, status)
                VALUES (?, ?, ?, 1, 1)
                """, productId, unitName, baseQuantity);
        return jdbc.queryForObject("SELECT id FROM product_unit WHERE product_id = ? AND unit_name = ?",
                Long.class, productId, unitName);
    }

    private long insertReceipt(JdbcTemplate jdbc, String receiptNo, long storeId) {
        jdbc.update("INSERT INTO inventory_receipt (receipt_no, store_id) VALUES (?, ?)", receiptNo, storeId);
        return jdbc.queryForObject("SELECT id FROM inventory_receipt WHERE receipt_no = ?", Long.class, receiptNo);
    }

    private long insertReceiptLine(JdbcTemplate jdbc, long receiptId, long storeId, long productId) {
        jdbc.update("""
                INSERT INTO inventory_receipt_line (receipt_id, line_no, product_id, store_id)
                VALUES (?, 1, ?, ?)
                """, receiptId, productId, storeId);
        return jdbc.queryForObject("SELECT id FROM inventory_receipt_line WHERE receipt_id = ?",
                Long.class, receiptId);
    }

    private long insertBatch(JdbcTemplate jdbc, long lineId, long productId, long originalUnitId,
                             long baseUnitId, String batchNo, String dateStatus,
                             String productionDate, String expiryDate) {
        return insertBatch(jdbc, lineId, productId, originalUnitId, baseUnitId, batchNo,
                dateStatus, productionDate, expiryDate, 1, 1, 1);
    }

    private long insertBatch(JdbcTemplate jdbc, long lineId, long productId, long originalUnitId,
                             long baseUnitId, String batchNo, String dateStatus,
                             String productionDate, String expiryDate, int quantity, int originalCost,
                             int baseCost) {
        jdbc.update("""
                INSERT INTO inventory_receipt_batch
                    (receipt_line_id, product_id, store_id, internal_receipt_batch_no,
                     original_unit_id, original_unit_name, original_quantity, original_unit_cost,
                     base_unit_id, base_quantity, base_unit_cost, date_status, production_date, expiry_date)
                VALUES (?, ?, 1, ?, ?, '瓶', ?, ?, ?, ?, ?, ?, ?, ?)
                """, lineId, productId, batchNo, originalUnitId, quantity, originalCost,
                baseUnitId, quantity, baseCost, dateStatus, productionDate, expiryDate);
        return jdbc.queryForObject("SELECT id FROM inventory_receipt_batch WHERE internal_receipt_batch_no = ?",
                Long.class, batchNo);
    }

    private long insertLocation(JdbcTemplate jdbc, long storeId, String code) {
        jdbc.update("""
                INSERT INTO inventory_location (store_id, location_type, code, name)
                VALUES (?, 'SALES_FLOOR', ?, ?)
                """, storeId, code, code);
        return jdbc.queryForObject("SELECT id FROM inventory_location WHERE code = ?", Long.class, code);
    }

    private long insertPickupRequest(JdbcTemplate jdbc, long orderId, long orderItemId, long storeId,
                                     long productId, int requestedBaseQuantity, Long sourceLocationId) {
        jdbc.update("""
                INSERT INTO order_pickup_request
                    (order_id, order_item_id, store_id, product_id, requested_base_quantity, source_location_id)
                VALUES (?, ?, ?, ?, ?, ?)
                """, orderId, orderItemId, storeId, productId, requestedBaseQuantity, sourceLocationId);
        return jdbc.queryForObject("SELECT id FROM order_pickup_request WHERE order_item_id = ?",
                Long.class, orderItemId);
    }

    private static void createH2AppendOnlyTriggers() {
        createH2AppendOnlyTriggers(new JdbcTemplate(dataSource));
    }

    private static void createH2AppendOnlyTriggers(JdbcTemplate jdbc) {
        String triggerClass = AppendOnlyTrigger.class.getName();
        jdbc.execute("CREATE TRIGGER price_audit_no_update BEFORE UPDATE ON order_item_price_audit "
                + "FOR EACH ROW CALL '" + triggerClass + "'");
        jdbc.execute("CREATE TRIGGER price_audit_no_delete BEFORE DELETE ON order_item_price_audit "
                + "FOR EACH ROW CALL '" + triggerClass + "'");
        jdbc.execute("CREATE TRIGGER decision_log_no_update BEFORE UPDATE ON inventory_decision_log "
                + "FOR EACH ROW CALL '" + triggerClass + "'");
        jdbc.execute("CREATE TRIGGER decision_log_no_delete BEFORE DELETE ON inventory_decision_log "
                + "FOR EACH ROW CALL '" + triggerClass + "'");
    }

    private static void dropH2AppendOnlyTriggers(JdbcTemplate jdbc) {
        jdbc.execute("DROP TRIGGER IF EXISTS price_audit_no_update");
        jdbc.execute("DROP TRIGGER IF EXISTS price_audit_no_delete");
        jdbc.execute("DROP TRIGGER IF EXISTS decision_log_no_update");
        jdbc.execute("DROP TRIGGER IF EXISTS decision_log_no_delete");
    }

    public static final class AppendOnlyTrigger implements org.h2.api.Trigger {
        @Override
        public void init(Connection connection, String schemaName, String triggerName,
                          String tableName, boolean before, int type) {
        }

        @Override
        public void fire(Connection connection, Object[] oldRow, Object[] newRow) throws SQLException {
            throw new SQLException("append-only fact");
        }

        @Override
        public void close() {
        }

        @Override
        public void remove() {
        }
    }

    @Test
    void retailFactChainMigrationDoesNotRewriteLegacyRows() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        assertEquals("箱", jdbc.queryForObject(
                "SELECT main_unit FROM product WHERE id = 1", String.class));
        assertEquals("瓶", jdbc.queryForObject(
                "SELECT aux_unit FROM product WHERE id = 1", String.class));
        assertEquals(new BigDecimal("12.00000000"), jdbc.queryForObject(
                "SELECT quantity FROM inventory_batch WHERE id = 1", BigDecimal.class));
        assertEquals("箱", jdbc.queryForObject(
                "SELECT unit FROM order_item WHERE id = 1", String.class));
    }

    @Test
    void productUnitDatabaseGuardRejectsTwoEnabledBaseUnitsForOneProduct() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.update("""
                INSERT INTO product_unit
                    (product_id, unit_name, base_quantity_per_unit, is_base_unit, status)
                VALUES (1, '瓶', 1, 1, 1)
                """);

        assertThrows(DataAccessException.class, () -> jdbc.update("""
                INSERT INTO product_unit
                    (product_id, unit_name, base_quantity_per_unit, is_base_unit, status)
                VALUES (1, '箱', 24, 1, 1)
                """));
    }

    @Test
    void inventoryMovementRejectsDuplicateIdempotencyKeyButAllowsMultipleNulls() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        long unit = insertUnit(jdbc, 1, "瓶", 1);
        long receipt = insertReceipt(jdbc, "R-IDEM", 1);
        long line = insertReceiptLine(jdbc, receipt, 1, 1);
        long batch = insertBatch(jdbc, line, 1, unit, unit, "B-IDEM", "KNOWN", "2026-01-01", "2026-12-31");

        jdbc.update("""
                INSERT INTO inventory_movement
                    (movement_no, idempotency_key, batch_id, product_id, store_id, movement_type,
                     original_unit_id, original_unit_name, original_quantity, unit_cost, base_quantity)
                VALUES ('M-IDEM-1', 'IDEM-DUP', ?, 1, 1, 'COUNT_ADJUSTMENT', ?, '瓶', 1, 1, 1)
                """, batch, unit);
        assertThrows(DataAccessException.class, () -> jdbc.update("""
                INSERT INTO inventory_movement
                    (movement_no, idempotency_key, batch_id, product_id, store_id, movement_type,
                     original_unit_id, original_unit_name, original_quantity, unit_cost, base_quantity)
                VALUES ('M-IDEM-2', 'IDEM-DUP', ?, 1, 1, 'COUNT_ADJUSTMENT', ?, '瓶', 1, 1, 1)
                """, batch, unit));

        // 补货/拣货不强制幂等键,多行 NULL 必须能在唯一索引下共存
        jdbc.update("""
                INSERT INTO inventory_movement
                    (movement_no, batch_id, product_id, store_id, movement_type,
                     original_unit_id, original_unit_name, original_quantity, unit_cost, base_quantity)
                VALUES ('M-NULL-1', ?, 1, 1, 'REPLENISHMENT', ?, '瓶', 1, 1, 1)
                """, batch, unit);
        jdbc.update("""
                INSERT INTO inventory_movement
                    (movement_no, batch_id, product_id, store_id, movement_type,
                     original_unit_id, original_unit_name, original_quantity, unit_cost, base_quantity)
                VALUES ('M-NULL-2', ?, 1, 1, 'REPLENISHMENT', ?, '瓶', 1, 1, 1)
                """, batch, unit);
    }

    @Test
    void pickupRequestIsLinkedFromAllocationAndEnforcesStatusQuantityAndCrossSkuGuards() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        long location = insertLocation(jdbc, 1, "L-PICKUP");
        long requestId = insertPickupRequest(jdbc, 1, 1, 1, 1, 5, null);

        // 合法：PENDING 请求可被批次归属引用（货架缺货/库房取货场景）
        jdbc.update("""
                INSERT INTO order_item_batch_allocation
                    (order_item_id, product_id, store_id, allocated_base_quantity, pickup_request_id, evidence_level)
                VALUES (1, 1, 1, 5, ?, 'UNKNOWN')
                """, requestId);

        assertThrows(DataAccessException.class, () -> jdbc.update("""
                INSERT INTO order_pickup_request
                    (order_id, order_item_id, store_id, product_id, requested_base_quantity, status)
                VALUES (1, 1, 1, 1, 5, 'DONE')
                """));
        assertThrows(DataAccessException.class, () -> jdbc.update("""
                INSERT INTO order_pickup_request
                    (order_id, order_item_id, store_id, product_id, requested_base_quantity)
                VALUES (1, 1, 1, 1, 0)
                """));
        assertThrows(DataAccessException.class, () -> jdbc.update("""
                INSERT INTO order_pickup_request
                    (order_id, order_item_id, store_id, product_id, requested_base_quantity)
                VALUES (1, 1, 1, 2, 5)
                """));
        assertThrows(DataAccessException.class, () -> jdbc.update("""
                INSERT INTO order_pickup_request
                    (order_id, order_item_id, store_id, product_id, requested_base_quantity, source_location_id)
                VALUES (1, 1, 2, 1, 5, ?)
                """, location));
        assertThrows(DataAccessException.class, () -> jdbc.update("""
                INSERT INTO order_item_batch_allocation
                    (order_item_id, product_id, store_id, allocated_base_quantity, pickup_request_id, evidence_level)
                VALUES (1, 1, 1, 5, 999999, 'UNKNOWN')
                """));
    }

    private void assertTableExistsUnchecked(String tableName) {
        try {
            assertTableExists(tableName);
        } catch (SQLException exception) {
            throw new AssertionError("读取表元数据失败: " + tableName, exception);
        }
    }

    private void assertTableExists(String tableName) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             ResultSet tables = connection.getMetaData().getTables(null, null, tableName, new String[]{"TABLE"})) {
            assertTrue(tables.next(), () -> "缺少表: " + tableName);
        }
    }

    private void assertColumnExists(String tableName, String columnName) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             ResultSet columns = connection.getMetaData().getColumns(null, null, tableName, columnName)) {
            assertTrue(columns.next(), () -> "缺少列: " + tableName + "." + columnName);
        }
    }

    private void assertInformationSchemaColumn(JdbcTemplate jdbc, String tableName, String columnName) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
                WHERE UPPER(TABLE_NAME) = UPPER(?) AND UPPER(COLUMN_NAME) = UPPER(?)
                """, Integer.class, tableName, columnName);
        assertEquals(1, count == null ? 0 : count.intValue(), "缺少列: " + tableName + "." + columnName);
    }

    private void assertUniqueIndex(String tableName, String indexName, String... expectedColumns)
            throws SQLException {
        List<String> actualColumns = new ArrayList<>();
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            try (ResultSet indexes = metadata.getIndexInfo(null, null, tableName, true, false)) {
                while (indexes.next()) {
                    String actualIndexName = indexes.getString("INDEX_NAME");
                    if (actualIndexName != null && (indexName.equalsIgnoreCase(actualIndexName)
                            || actualIndexName.regionMatches(true, 0, indexName + "_INDEX_", 0,
                            indexName.length() + "_INDEX_".length()))) {
                        actualColumns.add(indexes.getString("COLUMN_NAME"));
                    }
                }
            }
        }
        assertEquals(List.of(expectedColumns), actualColumns, "唯一索引列不匹配: " + indexName);
    }

    private void assertIndex(String tableName, String indexName, String... expectedColumns)
            throws SQLException {
        List<String> actualColumns = new ArrayList<>();
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            try (ResultSet indexes = metadata.getIndexInfo(null, null, tableName, false, false)) {
                while (indexes.next()) {
                    String actualIndexName = indexes.getString("INDEX_NAME");
                    if (actualIndexName != null && indexName.equalsIgnoreCase(actualIndexName)) {
                        actualColumns.add(indexes.getString("COLUMN_NAME"));
                    }
                }
            }
        }
        assertEquals(List.of(expectedColumns), actualColumns, "索引列不匹配: " + indexName);
    }
}
