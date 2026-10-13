package com.agri_system.infrastructure.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataAccessException;

import java.math.BigDecimal;
import java.sql.Connection;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;


/** 仅使用独立内存库，不访问开发数据库。 */
class UnifiedInventoryMigrationTest {
    private JdbcDataSource source;
    private JdbcTemplate jdbc;

    @BeforeEach
    void schema() {
        source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:unified_" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(source);
        jdbc.execute("""
            CREATE TABLE store(id BIGINT PRIMARY KEY);
            CREATE TABLE staff(id BIGINT PRIMARY KEY);
            CREATE TABLE supplier(id BIGINT PRIMARY KEY);
            CREATE TABLE purchase_order(id BIGINT PRIMARY KEY);
            CREATE TABLE purchase_order_item(id BIGINT PRIMARY KEY, product_id BIGINT NOT NULL);
            CREATE TABLE product(id BIGINT PRIMARY KEY, main_unit VARCHAR(20), aux_unit VARCHAR(20), main_to_aux_ratio DECIMAL(10,4));
            CREATE TABLE order_item(id BIGINT PRIMARY KEY, product_id BIGINT NOT NULL);
            CREATE TABLE staff_performance_log(id BIGINT PRIMARY KEY,staff_id BIGINT,status TINYINT,freeze_until DATETIME);
            CREATE TABLE inventory_batch(id BIGINT PRIMARY KEY,store_id BIGINT,product_id BIGINT,batch_no VARCHAR(50),production_date DATE,expire_date DATE,quantity DECIMAL(18,8),cost_price DECIMAL(10,2),is_active TINYINT,created_at DATETIME DEFAULT CURRENT_TIMESTAMP);
            CREATE TABLE inventory(id BIGINT AUTO_INCREMENT PRIMARY KEY,store_id BIGINT,product_id BIGINT,quantity DECIMAL(18,8),UNIQUE(store_id,product_id));
            CREATE TABLE inventory_transaction(id BIGINT PRIMARY KEY,batch_id BIGINT,quantity DECIMAL(18,8),cost_price DECIMAL(10,2),before_quantity DECIMAL(18,8),after_quantity DECIMAL(18,8),batch_before_qty DECIMAL(18,8),batch_after_qty DECIMAL(18,8),original_quantity DECIMAL(10,4));
            CREATE TABLE inventory_transfer_detail(id BIGINT PRIMARY KEY,from_batch_id BIGINT NOT NULL,to_batch_id BIGINT NOT NULL,quantity DECIMAL(10,4),cost_price DECIMAL(10,2));
            CREATE TABLE inventory_transfer(id BIGINT PRIMARY KEY,quantity DECIMAL(10,4));
            CREATE TABLE inventory_adjustment(id BIGINT PRIMARY KEY,quantity_before DECIMAL(10,4),quantity_after DECIMAL(10,4),quantity_diff DECIMAL(10,4));
            """);
        Flyway.configure().dataSource(source).locations("classpath:db/migration")
                .baselineOnMigrate(true).baselineVersion("118").target("124").load().migrate();
    }

    private void migrate() throws Exception {
        BaseJavaMigration migration = (BaseJavaMigration) Class.forName("db.migration.V129__unify_inventory_facts").getDeclaredConstructor().newInstance();
        try (Connection connection = source.getConnection()) {
            Context context = new Context() {
                public org.flywaydb.core.api.configuration.Configuration getConfiguration() { return null; }
                public Connection getConnection() { return connection; }
            };
            migration.migrate(context);
        }
    }

    private void legacy(String quantity, String cost, String ratio) {
        jdbc.update("INSERT INTO store VALUES(1)");
        jdbc.update("INSERT INTO product VALUES(1,'箱','瓶',?)", new BigDecimal(ratio));
        jdbc.update("INSERT INTO inventory_batch(id,store_id,product_id,batch_no,production_date,expire_date,quantity,cost_price,is_active) VALUES(41,1,1,'旧批次','2026-01-01','2027-01-01',?,?,1)",new BigDecimal(quantity),new BigDecimal(cost));
        jdbc.update("INSERT INTO inventory(store_id,product_id,quantity) VALUES(1,1,999)");
    }

    @Test void cleanSchemaSupportsNewReferencesAndMovementTypes() throws Exception {
        migrate();
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM inventory_receipt_batch",Integer.class));
        jdbc.update("INSERT INTO store VALUES(1)");
        jdbc.update("INSERT INTO inventory_receipt(receipt_no,store_id,request_key) VALUES('R1',1,'r-key')");
        assertThrows(DataAccessException.class,()->jdbc.update("INSERT INTO inventory_receipt(receipt_no,store_id,request_key) VALUES('R2',1,'r-key')"));
        jdbc.update("INSERT INTO inventory_transfer(id,quantity,request_key) VALUES(1,1,'t-key')");
        assertThrows(DataAccessException.class,()->jdbc.update("INSERT INTO inventory_transfer(id,quantity,request_key) VALUES(2,1,'t-key')"));
        jdbc.execute("INSERT INTO inventory_transfer_detail(id,from_batch_id,to_batch_id,quantity) VALUES(1,NULL,NULL,1)");
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM inventory_transfer_detail WHERE from_receipt_batch_id IS NULL",Integer.class));
    }

    @Test void migratesExactQuantitiesCostsDatesAndKeepsLegacy() throws Exception {
        legacy("1.25000001","30","3");
        migrate();
        assertEquals(new BigDecimal("3.75000003"),jdbc.queryForObject("SELECT base_quantity FROM inventory_batch_balance",BigDecimal.class));
        assertEquals(new BigDecimal("1.25000001"),jdbc.queryForObject("SELECT quantity FROM inventory",BigDecimal.class));
        assertEquals(new BigDecimal("10.00000000"),jdbc.queryForObject("SELECT base_unit_cost FROM inventory_receipt_batch",BigDecimal.class));
        assertEquals(41L,jdbc.queryForObject("SELECT legacy_batch_id FROM inventory_receipt_batch",Long.class));
        assertEquals("2027-01-01",jdbc.queryForObject("SELECT expiry_date FROM inventory_receipt_batch",String.class));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM inventory_batch WHERE id=41",Integer.class));
        assertEquals("RECEIPT",jdbc.queryForObject("SELECT movement_type FROM inventory_movement",String.class));
        assertEquals("瓶",jdbc.queryForObject("SELECT unit_name FROM product_unit WHERE is_base_unit=1",String.class));
    }

    @Test void roundsRecurringBaseCostAndPreservesOriginalCost() throws Exception {
        legacy("1","10","3");
        migrate();
        assertEquals(new BigDecimal("3.33333333"),jdbc.queryForObject("SELECT base_unit_cost FROM inventory_receipt_batch",BigDecimal.class));
        assertEquals(new BigDecimal("10.00000000"),jdbc.queryForObject("SELECT original_unit_cost FROM inventory_receipt_batch",BigDecimal.class));
        assertEquals(new BigDecimal("3.33333333"),jdbc.queryForObject("SELECT unit_cost FROM inventory_movement",BigDecimal.class));
    }

    @Test void refusesInvalidConversionBeforeAnyMutation() {
        legacy("1","10","0");
        assertTrue(assertThrows(Exception.class,this::migrate).getMessage().contains("product"));
        assertUnchangedBeforeDdl();
    }

    @Test void refusesTrueQuantityOverflowBeforeAnyMutation() {
        legacy("9999999999","10","999999");
        assertTrue(assertThrows(Exception.class,this::migrate).getMessage().contains("41"));
        assertUnchangedBeforeDdl();
    }

    @Test void refusesDdlNullQuantityBeforeAnyMutation() {
        legacy("1","10","3");
        jdbc.update("INSERT INTO inventory_transfer(id,quantity) VALUES(1,NULL)");
        assertTrue(assertThrows(Exception.class,this::migrate).getMessage().contains("inventory_transfer.quantity"));
        assertUnchangedBeforeDdl();
    }

    @Test void retriesSchemaAfterPartialDdlWithoutDuplicatingFacts() throws Exception {
        legacy("1","10","3");
        jdbc.execute("ALTER TABLE inventory_receipt_batch ADD COLUMN legacy_batch_id BIGINT NULL");
        jdbc.execute("ALTER TABLE inventory_receipt_batch ADD CONSTRAINT uk_receipt_legacy_batch UNIQUE(legacy_batch_id)");
        jdbc.execute("ALTER TABLE inventory_movement DROP CONSTRAINT chk_inventory_movement_type");
        jdbc.execute("ALTER TABLE inventory_transfer_detail ADD COLUMN from_receipt_batch_id BIGINT NULL");
        migrate();
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM inventory_receipt_batch",Integer.class));
    }

    @Test void respectsExistingConsistentBaseAndMainUnits() throws Exception {
        legacy("1","10","3");
        jdbc.update("INSERT INTO product_unit(product_id,unit_name,base_quantity_per_unit,is_base_unit) VALUES(1,'瓶',1,1),(1,'箱',3,0)");
        migrate();
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM product_unit",Integer.class));
        assertEquals(new BigDecimal("3.00000000"),jdbc.queryForObject("SELECT base_quantity FROM inventory_batch_balance",BigDecimal.class));
    }

    @Test void refusesConflictingExistingUnitFactorBeforeDdl() {
        legacy("1","10","3");
        jdbc.update("INSERT INTO product_unit(product_id,unit_name,base_quantity_per_unit,is_base_unit) VALUES(1,'瓶',1,1),(1,'箱',4,0)");
        assertTrue(assertThrows(Exception.class,this::migrate).getMessage().contains("换算比例不匹配"));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME='inventory_receipt_batch' AND COLUMN_NAME='legacy_batch_id'",Integer.class));
    }

    @Test void rollsBackFactsAfterDmlFailureThenRetriesRemainingSchema() throws Exception {
        legacy("1","10","3");
        jdbc.execute("CREATE TRIGGER migration_insert_failure BEFORE INSERT ON inventory_receipt FOR EACH ROW CALL '"+FailInsertTrigger.class.getName()+"'");
        assertThrows(Exception.class,this::migrate);
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM product_unit",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM inventory_receipt_batch",Integer.class));
        assertEquals(new BigDecimal("999.00000000"),jdbc.queryForObject("SELECT quantity FROM inventory",BigDecimal.class));
        jdbc.execute("DROP TRIGGER migration_insert_failure");
        migrate();
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM inventory_receipt_batch",Integer.class));
        assertEquals(new BigDecimal("1.00000000"),jdbc.queryForObject("SELECT quantity FROM inventory",BigDecimal.class));
    }

    @Test void projectsExistingReceiptBalanceInMainUnits() throws Exception {
        jdbc.update("INSERT INTO store VALUES(1)");
        jdbc.update("INSERT INTO product VALUES(1,'箱','瓶',3)");
        jdbc.update("INSERT INTO product_unit(id,product_id,unit_name,base_quantity_per_unit,is_base_unit) VALUES(1,1,'瓶',1,1),(2,1,'箱',3,0)");
        jdbc.update("INSERT INTO inventory_receipt(id,receipt_no,store_id) VALUES(1,'EXISTING',1)");
        jdbc.update("INSERT INTO inventory_receipt_line(id,receipt_id,line_no,product_id,store_id) VALUES(1,1,1,1,1)");
        jdbc.update("INSERT INTO inventory_receipt_batch(id,receipt_line_id,product_id,store_id,internal_receipt_batch_no,original_unit_id,original_unit_name,original_quantity,original_unit_cost,base_unit_id,base_quantity,base_unit_cost) VALUES(1,1,1,1,'B1',2,'箱',2,10,1,6,3.33333333)");
        jdbc.update("INSERT INTO inventory_location(id,store_id,location_type,name) VALUES(1,1,'STORE_BACKROOM','后仓')");
        jdbc.update("INSERT INTO inventory_batch_balance(batch_id,product_id,store_id,location_id,base_quantity) VALUES(1,1,1,1,6)");
        migrate();
        assertEquals(new BigDecimal("2.00000000"),jdbc.queryForObject("SELECT quantity FROM inventory",BigDecimal.class));
    }

    public static class FailInsertTrigger implements org.h2.api.Trigger {
        public void fire(Connection connection,Object[] oldRow,Object[] newRow) throws java.sql.SQLException {
            throw new java.sql.SQLException("故意注入收货写入失败");
        }
    }

    private void assertUnchangedBeforeDdl() {
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM product_unit",Integer.class));
        assertEquals(new BigDecimal("999.00000000"),jdbc.queryForObject("SELECT quantity FROM inventory",BigDecimal.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME='inventory_receipt_batch' AND COLUMN_NAME='legacy_batch_id'",Integer.class));
    }

    @Test void migratesExpiryWithoutInventingProductionDate() throws Exception {
        legacy("1", "30", "3");
        jdbc.update("UPDATE inventory_batch SET production_date=NULL");
        migrate();
        assertNull(jdbc.queryForObject("SELECT production_date FROM inventory_receipt_batch", java.sql.Date.class));
        assertEquals("2027-01-01", jdbc.queryForObject("SELECT expiry_date FROM inventory_receipt_batch", String.class));
        assertEquals("PARTIAL", jdbc.queryForObject("SELECT date_status FROM inventory_receipt_batch", String.class));
    }

    @Test void refusesReversedDatesBeforeAnyMutation() {
        legacy("1","30","3");
        jdbc.update("UPDATE inventory_batch SET expire_date='2025-01-01'");
        assertTrue(assertThrows(Exception.class,this::migrate).getMessage().contains("41"));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM product_unit",Integer.class));
    }

    @Test void refusesSnapshotWithoutStockFacts() {
        jdbc.update("INSERT INTO store VALUES(1)");
        jdbc.update("INSERT INTO product VALUES(1,'瓶',NULL,NULL)");
        jdbc.update("INSERT INTO inventory(store_id,product_id,quantity) VALUES(1,1,7)");
        assertTrue(assertThrows(Exception.class,this::migrate).getMessage().contains("inventory"));
    }
}
