package com.agri_system.infrastructure.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

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
                CREATE TABLE `purchase_order_item` (`id` BIGINT PRIMARY KEY);
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
                    `unit` VARCHAR(20) NOT NULL
                );
                INSERT INTO `product` (`id`, `main_unit`, `aux_unit`, `main_to_aux_ratio`)
                    VALUES (1, '箱', '瓶', 24);
                INSERT INTO `inventory_batch` (`id`, `quantity`) VALUES (1, 12.00000000);
                INSERT INTO `order_item` (`id`, `unit`) VALUES (1, '箱');
                """);

        flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("118")
                .target(MigrationVersion.LATEST)
                .load();
        flyway.migrate();
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
    }

    @Test
    void retailFactChainMigrationCreatesAllFactTablesAndSnapshots() throws SQLException {
        assertEquals("120", flyway.info().current().getVersion().getVersion());

        List.of(
                "product_unit",
                "inventory_receipt",
                "inventory_receipt_line",
                "inventory_receipt_batch",
                "inventory_location",
                "inventory_batch_balance",
                "inventory_movement",
                "order_item_batch_allocation",
                "order_item_price_audit",
                "inventory_decision_log"
        ).forEach(this::assertTableExistsUnchecked);

        assertColumnExists("order_item", "base_unit_id");
        assertColumnExists("order_item", "base_quantity");
        assertColumnExists("order_item", "unit_cost");
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
}
