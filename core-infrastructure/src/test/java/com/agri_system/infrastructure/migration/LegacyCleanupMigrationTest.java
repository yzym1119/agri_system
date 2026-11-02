package com.agri_system.infrastructure.migration;

import java.sql.*;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named="mysql.it",matches="true")
class LegacyCleanupMigrationTest {
    private final String database="agri_cleanup_it_"+UUID.randomUUID().toString().replace("-", "");
    private final String user=System.getProperty("mysql.it.user","root"),password=System.getProperty("mysql.it.password","");
    private final String server="jdbc:mysql://"+System.getProperty("mysql.it.host","127.0.0.1")+":"+System.getProperty("mysql.it.port","3306")+"/";
    private final String params="?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai";
    private boolean created;
    private String url(){return server+database+params;}
    private Flyway migration(String target){return Flyway.configure().dataSource(url(),user,password).locations("classpath:db/migration").callbacks(new LegacyPerformanceDropGuard()).target(target).load();}
    @BeforeEach void create() throws Exception {
        try(var c=DriverManager.getConnection(server+params,user,password);var s=c.createStatement()) {s.execute("CREATE DATABASE `"+database+"` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");created=true;}
        migration("135").migrate();
    }
    @AfterEach void remove() throws Exception {
        if(created)try(var c=DriverManager.getConnection(server+params,user,password);var s=c.createStatement()){s.execute("DROP DATABASE `"+database+"`");}
    }
    private long count(String sql) throws Exception {try(var c=DriverManager.getConnection(url(),user,password);var s=c.createStatement();var r=s.executeQuery(sql)){r.next();return r.getLong(1);}}
    private void sql(String sql) throws Exception {try(var c=DriverManager.getConnection(url(),user,password);var s=c.createStatement()){s.execute(sql);}}
    @Test void nonemptyHistoryStopsBeforeDeletingAnyColumnOrTable() throws Exception {
        sql("INSERT INTO store(id,code,name,status) VALUES(999,'MIGRATION-STORE','虚构迁移门店',1)");
        sql("INSERT INTO staff(id,code,name,password,role,status,store_id) VALUES(999,'MIGRATION-HISTORY','虚构迁移员工','unused','STAFF',1,999)");
        sql("INSERT INTO staff_performance(staff_id,performance_type,performance_date,commission_amount) VALUES(999,1,'2026-01-01',12.34)");
        assertThrows(FlywayException.class,()->migration("138").migrate());
        assertEquals(1,count("SELECT COUNT(*) FROM staff_performance WHERE commission_amount=12.34"));
        assertEquals(1,count("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='product' AND column_name='online_price'"));
        assertEquals(0,count("SELECT COUNT(*) FROM flyway_schema_history WHERE version IN ('136','137','138')"));
    }
    @Test void emptyHistoryUpgradesAndRetainsChecksumsOnReplay() throws Exception {
        var flyway=migration("138");flyway.migrate();flyway.validate();
        assertEquals("138",flyway.info().current().getVersion().getVersion());
        assertEquals(0,count("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='product' AND column_name='online_price'"));
        assertEquals(0,count("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='staff_performance'"));
        assertEquals(2,count("SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='inventory_batch_balance' AND index_name='idx_ibb_store_product' AND ((seq_in_index=1 AND column_name='store_id') OR (seq_in_index=2 AND column_name='product_id'))"));
        assertEquals(1,count("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='wallet_auth_code' AND table_collation='utf8mb4_unicode_ci'"));
        assertEquals(0,flyway.migrate().migrationsExecuted);
    }
}
