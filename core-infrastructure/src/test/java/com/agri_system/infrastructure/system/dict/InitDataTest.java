package com.agri_system.infrastructure.system.dict;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * H2 对 MySQL 的 KEY 语法兼容性有限（全局索引名冲突），
 * 此测试在真实 MySQL 环境下由集成测试覆盖。
 */
@Disabled("H2 不兼容 MySQL 的多表同名 KEY 语法，由集成测试覆盖")
public class InitDataTest {

    private DataSource dataSource;

    @BeforeEach
    void setUp() {
        org.h2.jdbcx.JdbcDataSource ds = new org.h2.jdbcx.JdbcDataSource();
        ds.setURL("jdbc:h2:mem:testdict;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false");
        ds.setUser("sa");
        ds.setPassword("");
        this.dataSource = ds;

        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .load();
        flyway.migrate();
    }

    @Test
    void testProductUnitInit() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        List<String> labels = jdbcTemplate.queryForList(
                "SELECT label FROM sys_dict_data WHERE type_code = 'product_unit'",
                String.class
        );
        assertTrue(labels.contains("箱"));
        assertTrue(labels.contains("瓶"));
    }
}






