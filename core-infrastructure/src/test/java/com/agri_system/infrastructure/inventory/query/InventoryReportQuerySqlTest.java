package com.agri_system.infrastructure.inventory.query;

import com.agri_system.infrastructure.inventory.mapper.InventoryBatchMapper;
import com.mybatisflex.core.datasource.FlexDataSource;
import com.mybatisflex.core.mybatis.FlexConfiguration;
import com.mybatisflex.core.mybatis.FlexSqlSessionFactoryBuilder;
import com.mybatisflex.core.tenant.TenantFactory;
import com.mybatisflex.core.tenant.TenantManager;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** 固定库存报表在统一批次余额上的真实SQL口径，旧批次不是读源。 */
class InventoryReportQuerySqlTest {
    private JdbcTemplate jdbc;
    private SqlSession session;
    private com.agri_system.domain.inventory.query.InventoryReportQuery query;
    private TenantFactory originalTenant;
    @BeforeEach void setup() {
        originalTenant=TenantManager.getTenantFactory();TenantManager.setTenantFactory(null);
        var source=new JdbcDataSource();source.setURL("jdbc:h2:mem:stock_report_"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1");
        jdbc=new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE inventory_receipt_batch(id BIGINT,store_id BIGINT,product_id BIGINT,supplier_batch_no VARCHAR(50),production_date DATE,expiry_date DATE,base_unit_cost DECIMAL(18,4),received_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE inventory_batch_balance(batch_id BIGINT,base_quantity DECIMAL(18,4))");
        jdbc.execute("CREATE TABLE product(id BIGINT,main_unit VARCHAR(20))");
        jdbc.execute("CREATE TABLE product_unit(product_id BIGINT,unit_name VARCHAR(20),base_quantity_per_unit DECIMAL(18,4))");
        jdbc.execute("CREATE TABLE inventory_batch(id BIGINT,quantity DECIMAL(18,4))");
        jdbc.execute("INSERT INTO inventory_batch VALUES(999,999999)");
        jdbc.execute("INSERT INTO product VALUES(10,'箱')");jdbc.execute("INSERT INTO product_unit VALUES(10,'箱',4),(10,'瓶',1)");
        jdbc.execute("INSERT INTO inventory_receipt_batch VALUES(1,1,10,'LOT1','2026-01-01','2027-01-01',2,'2026-10-01 12:00:00'),(2,2,10,'LOT2',NULL,'2027-02-01',3,'2026-10-02 12:00:00'),(3,1,10,'EMPTY',NULL,NULL,4,'2026-10-03 12:00:00')");
        jdbc.execute("INSERT INTO inventory_batch_balance VALUES(1,3),(1,5),(2,20),(3,0)");
        var config=new FlexConfiguration(new Environment("stock-report",new JdbcTransactionFactory(),new FlexDataSource("stock-report",source,false)));
        config.setMapUnderscoreToCamelCase(true);config.setLocalCacheScope(org.apache.ibatis.session.LocalCacheScope.STATEMENT);config.addMapper(InventoryBatchMapper.class);
        session=new FlexSqlSessionFactoryBuilder().build(config).openSession(true);query=new InventoryReportQueryImpl(session.getMapper(InventoryBatchMapper.class));
    }
    @AfterEach void close() {TenantManager.setTenantFactory(originalTenant);if(session!=null)session.close();if(jdbc!=null)jdbc.execute("SHUTDOWN");}
    @Test void aggregatesLocationsAndConvertsToMainUnitWithoutReadingLegacyTable() {
        var rows=query.findCurrentBatches(1L);assertEquals(1,rows.size());var row=rows.get(0);
        assertEquals(1L,row.batchId());assertEquals(1L,row.storeId());assertEquals(10L,row.productId());assertEquals("LOT1",row.supplierBatchNo());
        assertEquals(0,new BigDecimal("2").compareTo(row.quantityInMainUnit()));assertEquals(0,new BigDecimal("8").compareTo(row.unitCostInMainUnit()));
        assertEquals(LocalDate.of(2026,1,1),row.productionDate());assertEquals(LocalDate.of(2027,1,1),row.expiryDate());assertEquals(LocalDateTime.of(2026,10,1,12,0),row.receivedAt());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM inventory_batch",Integer.class));
    }
    @Test void allStoresKeepIndependentCostsAndUnknownProductionDateAndRefreshBalances() {
        var all=query.findCurrentBatches(null);assertEquals(2,all.size());var second=all.stream().filter(r->r.storeId()==2L).findFirst().orElseThrow();
        assertNull(second.productionDate());assertEquals(LocalDate.of(2027,2,1),second.expiryDate());
        assertEquals(0,new BigDecimal("5").compareTo(second.quantityInMainUnit()));assertEquals(0,new BigDecimal("12").compareTo(second.unitCostInMainUnit()));
        jdbc.update("UPDATE inventory_batch_balance SET base_quantity=4 WHERE batch_id=2");
        assertEquals(0,BigDecimal.ONE.compareTo(query.findCurrentBatches(2L).get(0).quantityInMainUnit()));assertTrue(query.findCurrentBatches(3L).isEmpty());
    }
}
