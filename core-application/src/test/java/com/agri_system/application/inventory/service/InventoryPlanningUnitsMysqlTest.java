package com.agri_system.application.inventory.service;

import com.agri_system.infrastructure.inventory.mapper.InventoryPlanningMapper;
import com.agri_system.infrastructure.inventory.query.InventoryPlanningQueryImpl;
import com.mybatisflex.core.datasource.FlexDataSource;
import com.mybatisflex.core.mybatis.FlexConfiguration;
import com.mybatisflex.core.mybatis.FlexSqlSessionFactoryBuilder;
import com.mybatisflex.core.tenant.TenantFactory;
import com.mybatisflex.core.tenant.TenantManager;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.sql.DriverManager;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** 只在随机MySQL库验证真实单位、可用量、未知证据和门店范围，不使用既有业务库。 */
@EnabledIfSystemProperty(named="mysql.it",matches="true")
class InventoryPlanningUnitsMysqlTest {
    private final String database="agri_planning_it_"+UUID.randomUUID().toString().replace("-", "");
    private final String user=System.getProperty("mysql.it.user","root"),password=System.getProperty("mysql.it.password","");
    private final String server="jdbc:mysql://"+System.getProperty("mysql.it.host","127.0.0.1")+":"+System.getProperty("mysql.it.port","3306")+"/";
    private final String options="?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai";
    private JdbcTemplate jdbc;
    private SqlSession session;
    private SmartThresholdService service;
    private TenantFactory originalTenant;
    private boolean created;
    @BeforeEach void setup() throws Exception {
        originalTenant=TenantManager.getTenantFactory();TenantManager.setTenantFactory(null);
        try(var c=DriverManager.getConnection(server+options,user,password);var q=c.createStatement()){q.execute("CREATE DATABASE `"+database+"` CHARACTER SET utf8mb4");created=true;}
        var source=new DriverManagerDataSource(server+database+options,user,password);jdbc=new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE product(id BIGINT PRIMARY KEY,name VARCHAR(100),code VARCHAR(30),main_unit VARCHAR(20),min_stock DECIMAL(18,4),max_stock DECIMAL(18,4),reorder_quantity DECIMAL(18,4),deleted INT)");
        jdbc.execute("CREATE TABLE product_unit(product_id BIGINT,unit_name VARCHAR(20),base_quantity_per_unit DECIMAL(18,4))");
        jdbc.execute("CREATE TABLE `order`(id BIGINT PRIMARY KEY,store_id BIGINT,order_status INT,created_at DATETIME,deleted INT)");
        jdbc.execute("CREATE TABLE order_item(id BIGINT,order_id BIGINT,product_id BIGINT,item_type INT,quantity DECIMAL(18,4),unit VARCHAR(20))");
        jdbc.execute("CREATE TABLE inventory_batch_balance(product_id BIGINT,store_id BIGINT,base_quantity DECIMAL(18,4),reserved_base_quantity DECIMAL(18,4))");
        jdbc.execute("INSERT INTO product VALUES(1,'测试商品','P1','箱',3,10,5,0)");jdbc.execute("INSERT INTO product_unit VALUES(1,'箱',12),(1,'瓶',1)");
        jdbc.execute("INSERT INTO `order` VALUES(1,1,6,NOW(),0),(2,1,6,NOW(),0),(3,2,6,NOW(),0)");
        jdbc.execute("INSERT INTO order_item VALUES(1,1,1,1,2,'箱'),(2,2,1,1,6,'瓶'),(3,3,1,1,100,'箱')");
        jdbc.execute("INSERT INTO inventory_batch_balance VALUES(1,1,36,12),(1,2,1200,0)");
        var config=new FlexConfiguration(new Environment("planning-test",new JdbcTransactionFactory(),new FlexDataSource("planning-test",source,false)));
        config.setMapUnderscoreToCamelCase(true);config.setLocalCacheScope(org.apache.ibatis.session.LocalCacheScope.STATEMENT);
        config.addMapper(InventoryPlanningMapper.class);
        session=new FlexSqlSessionFactoryBuilder().build(config).openSession(true);
        service=new SmartThresholdService(new InventoryPlanningQueryImpl(session.getMapper(InventoryPlanningMapper.class)));
    }
    @AfterEach void close() throws Exception {
        TenantManager.setTenantFactory(originalTenant);if(session!=null)session.close();
        if(created)try(var c=DriverManager.getConnection(server+options,user,password);var q=c.createStatement()){q.execute("DROP DATABASE `"+database+"`");}
    }
    @Test void mixedSalesUnitsAndReservedStockAreComparedInMainUnit() {
        var row=service.calculateRecommendations(1L,7).get(0);
        assertEquals(0,new java.math.BigDecimal("2").compareTo(row.getCurrentStock()));
        assertEquals(0,new java.math.BigDecimal("2.5").compareTo(row.getTotalSold()));
        assertEquals(0,new java.math.BigDecimal("4").compareTo(row.getRecommendedMinStock()));
        assertEquals("箱",row.getMainUnit());assertEquals("LOW",row.getStatus());
        var all=service.calculateRecommendations(null,7).get(0);assertEquals(0,new java.math.BigDecimal("102.5").compareTo(all.getTotalSold()));assertEquals(0,new java.math.BigDecimal("102").compareTo(all.getCurrentStock()));
    }
    @Test void employeeOmittedStoreDoesNotMixOtherStores() {
        TenantManager.setTenantFactory(()->new Object[]{1L});
        var row=service.calculateRecommendations(null,7).get(0);
        assertEquals(0,new java.math.BigDecimal("2.5").compareTo(row.getTotalSold()));
        assertEquals(0,new java.math.BigDecimal("2").compareTo(row.getCurrentStock()));
        assertTrue(service.calculateRecommendations(2L,7).isEmpty());
        TenantManager.setTenantFactory(()->new Object[0]);assertTrue(service.calculateRecommendations(null,7).isEmpty());
    }
    @Test void missingUnitEvidenceCannotProduceAdoptableThresholds() {
        jdbc.execute("DELETE FROM product_unit WHERE unit_name='瓶'");
        var row=service.calculateRecommendations(1L,7).get(0);
        assertEquals("DATA_INSUFFICIENT",row.getStatus());assertNull(row.getRecommendedMinStock());assertNull(row.getAvgDailySales());
        jdbc.execute("DELETE FROM product_unit WHERE unit_name='箱'");
        var missingMain=service.calculateRecommendations(1L,7).get(0);assertNull(missingMain.getCurrentStock());assertNull(missingMain.getTotalSold());assertNull(missingMain.getRecommendedMinStock());
    }
    @Test void knownZeroStockIsDifferentFromMissingStockEvidence() {
        jdbc.execute("UPDATE inventory_batch_balance SET base_quantity=0,reserved_base_quantity=0 WHERE store_id=1");
        var known=service.calculateRecommendations(1L,7).get(0);assertEquals(0,java.math.BigDecimal.ZERO.compareTo(known.getCurrentStock()));assertEquals("LOW",known.getStatus());
        jdbc.execute("DELETE FROM inventory_batch_balance WHERE store_id=1");
        var unknown=service.calculateRecommendations(1L,7).get(0);assertNull(unknown.getCurrentStock());assertEquals("DATA_INSUFFICIENT",unknown.getStatus());assertNull(unknown.getRecommendedMinStock());
    }
}
