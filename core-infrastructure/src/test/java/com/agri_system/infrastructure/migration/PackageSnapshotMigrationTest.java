package com.agri_system.infrastructure.migration;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import java.math.BigDecimal;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** 验证实际迁移保留历史成交，并拒绝不完整的新套餐快照。 */
class PackageSnapshotMigrationTest {
    private JdbcTemplate jdbc;

    @BeforeEach void migrate() throws Exception {
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:package_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE order_item(id BIGINT PRIMARY KEY,package_id BIGINT,unit_price DECIMAL(10,2) NOT NULL,total_price DECIMAL(10,2))");
        jdbc.execute("CREATE TABLE order_item_price_audit(id BIGINT PRIMARY KEY,suggested_unit_price DECIMAL(10,2),final_unit_price DECIMAL(10,2),price_difference DECIMAL(10,2))");
        jdbc.update("INSERT INTO order_item VALUES(1,7,25,100)");
        try (var connection = source.getConnection()) {
            ScriptUtils.executeSqlScript(connection,new ClassPathResource("db/migration/V131__package_sale_snapshots.sql"));
        }
    }

    @Test void historicalPackageDoesNotInventCompositionOrSetCount() {
        assertNull(jdbc.queryForObject("SELECT package_group_no FROM order_item WHERE id=1",Integer.class));
        assertNull(jdbc.queryForObject("SELECT package_quantity FROM order_item WHERE id=1",BigDecimal.class));
        assertEquals(0,new BigDecimal("100").compareTo(jdbc.queryForObject("SELECT total_price FROM order_item WHERE id=1",BigDecimal.class)));
    }

    @Test void missingSetCountOrPriceIsRejectedInsteadOfPassingSqlUnknown() {
        assertThrows(Exception.class, () -> jdbc.update("INSERT INTO order_item(id,package_id,unit_price,package_group_no,package_name,package_unit_price) VALUES(2,7,0,1,'套餐',10)"));
        assertThrows(Exception.class, () -> jdbc.update("INSERT INTO order_item(id,package_id,unit_price,package_group_no,package_name,package_quantity) VALUES(3,7,0,1,'套餐',1)"));
    }

    @Test void fractionalPackageSetsAreRejected() {
        assertThrows(Exception.class, () -> jdbc.update("INSERT INTO order_item(id,package_id,unit_price,package_group_no,package_name,package_quantity,package_unit_price) VALUES(2,7,0,1,'套餐',0.5,10)"));
    }

    @Test void allocationUnitPriceAndAuditRetainTwelveDecimals() {
        BigDecimal allocated = new BigDecimal("1.428571428571");
        jdbc.update("UPDATE order_item SET unit_price=?,total_price=10 WHERE id=1",allocated);
        jdbc.update("INSERT INTO order_item_price_audit VALUES(1,?,0,?)",allocated,allocated.negate());
        assertEquals(allocated,jdbc.queryForObject("SELECT unit_price FROM order_item WHERE id=1",BigDecimal.class));
        assertEquals(allocated.negate(),jdbc.queryForObject("SELECT price_difference FROM order_item_price_audit WHERE id=1",BigDecimal.class));
    }
}
