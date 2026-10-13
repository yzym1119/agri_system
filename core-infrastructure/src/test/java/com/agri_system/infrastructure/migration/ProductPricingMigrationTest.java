package com.agri_system.infrastructure.migration;

import db.migration.V130__simplify_product_pricing;
import org.flywaydb.core.api.migration.Context;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.sql.Connection;
import java.math.BigDecimal;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;


/** 独立库验证旧价格计价单位转换，不修改成本或历史成交价。 */
class ProductPricingMigrationTest {
    private JdbcDataSource source;
    private JdbcTemplate jdbc;
    @BeforeEach void schema() {
        source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:pricing_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(source);
        jdbc.execute("""
            CREATE TABLE product(id BIGINT PRIMARY KEY,main_unit VARCHAR(20),aux_unit VARCHAR(20),suggested_retail_price DECIMAL(10,2),vip_price DECIMAL(10,2),min_price DECIMAL(10,2),avg_cost_price DECIMAL(10,2),online_price DECIMAL(10,2));
            CREATE TABLE product_unit(id BIGINT PRIMARY KEY,product_id BIGINT,unit_name VARCHAR(20),base_quantity_per_unit DECIMAL(20,8),is_base_unit INT,status INT);
            CREATE TABLE sys_menu(id BIGINT PRIMARY KEY,parent_id BIGINT,name VARCHAR(50),permission VARCHAR(100),type INT,sort_order INT,visible INT);
            CREATE TABLE sys_role(id BIGINT PRIMARY KEY);
            CREATE TABLE sys_role_menu(role_id BIGINT,menu_id BIGINT,PRIMARY KEY(role_id,menu_id));
            CREATE TABLE order_item(id BIGINT PRIMARY KEY,unit_price DECIMAL(10,2));
            INSERT INTO sys_role VALUES(1);
            INSERT INTO product VALUES(1,'箱','瓶',10,9,8,100,12);
            INSERT INTO product_unit VALUES(11,1,'箱',1,1,1),(12,1,'瓶',0.05,0,1);
            INSERT INTO order_item VALUES(1,77);
            """);
    }
    private void migrate() throws Exception {
        try (Connection connection = source.getConnection()) {
            Context context = new Context() {
                @Override public Connection getConnection() { return connection; }
                @Override public org.flywaydb.core.api.configuration.Configuration getConfiguration() { return null; }
            };
            new V130__simplify_product_pricing().migrate(context);
        }
    }
    @Test void convertsOldAuxiliaryUnitPricesToPersistedBaseUnitWithoutChangingCostOrOrders() throws Exception {
        migrate();
        assertEquals(new BigDecimal("200.00"), jdbc.queryForObject("SELECT suggested_retail_price FROM product", BigDecimal.class));
        assertEquals(new BigDecimal("180.00"), jdbc.queryForObject("SELECT vip_price FROM product", BigDecimal.class));
        assertEquals(new BigDecimal("160.00"), jdbc.queryForObject("SELECT min_price FROM product", BigDecimal.class));
        assertEquals(new BigDecimal("100.00"), jdbc.queryForObject("SELECT avg_cost_price FROM product", BigDecimal.class));
        assertEquals(new BigDecimal("77.00"), jdbc.queryForObject("SELECT unit_price FROM order_item", BigDecimal.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM sys_role_menu", Integer.class));
    }
    @Test void existingAuxiliaryBaseNeedsNoPriceConversion() throws Exception {
        jdbc.update("UPDATE product_unit SET base_quantity_per_unit=20,is_base_unit=0 WHERE id=11");
        jdbc.update("UPDATE product_unit SET base_quantity_per_unit=1,is_base_unit=1 WHERE id=12");
        migrate();
        assertEquals(new BigDecimal("10.00"), jdbc.queryForObject("SELECT suggested_retail_price FROM product", BigDecimal.class));
    }
    @Test void ambiguousPriceUnitStopsBeforeAnyPriceChanges() {
        jdbc.update("DELETE FROM product_unit WHERE id=12");
        assertThrows(Exception.class, this::migrate);
        assertEquals(new BigDecimal("10.00"), jdbc.queryForObject("SELECT suggested_retail_price FROM product", BigDecimal.class));
    }
    @Test void nonExactPriceConversionStopsRatherThanChangingOldSellingPrice() {
        jdbc.update("UPDATE product_unit SET base_quantity_per_unit=3 WHERE id=12");
        assertThrows(Exception.class, this::migrate);
        assertEquals(new BigDecimal("10.00"), jdbc.queryForObject("SELECT suggested_retail_price FROM product", BigDecimal.class));
    }

}
