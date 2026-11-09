package com.agri_system.infrastructure.inventory.repository;

import com.agri_system.infrastructure.inventory.mapper.InventoryMapper;
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
import java.util.UUID;
import javax.sql.DataSource;
import static org.junit.jupiter.api.Assertions.*;

class InventorySaleablePageSqlTest {
    JdbcTemplate jdbc;SqlSession session;InventoryRepositoryImpl repository;TenantFactory previous;
    @BeforeEach void setup() {
        previous=TenantManager.getTenantFactory();TenantManager.setTenantFactory(null);
        var source=dataSource();jdbc=new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE product(id BIGINT,name VARCHAR(50),specification VARCHAR(50),manufacturer VARCHAR(50),main_unit VARCHAR(20),aux_unit VARCHAR(20),main_to_aux_ratio DECIMAL(20,8),deleted INT DEFAULT 0)");
        jdbc.execute("CREATE TABLE store(id BIGINT,name VARCHAR(50),deleted INT DEFAULT 0)");jdbc.execute("INSERT INTO store(id,name) VALUES(1,'S1'),(2,'S2')");
        jdbc.execute("CREATE TABLE inventory(id BIGINT,product_id BIGINT,store_id BIGINT,quantity DECIMAL(20,8))");
        jdbc.execute("CREATE TABLE product_unit(id BIGINT,product_id BIGINT,unit_name VARCHAR(20),is_base_unit INT,status INT,base_quantity_per_unit DECIMAL(20,8))");
        jdbc.execute("CREATE TABLE inventory_receipt_batch(id BIGINT,base_unit_id BIGINT,expiry_date DATE)");
        jdbc.execute("CREATE TABLE inventory_location(id BIGINT,status INT)");jdbc.execute("INSERT INTO inventory_location VALUES(1,1),(2,0)");
        jdbc.execute("CREATE TABLE inventory_batch_balance(batch_id BIGINT,store_id BIGINT,product_id BIGINT,location_id BIGINT,base_quantity DECIMAL(20,8),reserved_base_quantity DECIMAL(20,8))");
        for(long id=1;id<=10;id++) {
            jdbc.update("INSERT INTO product(id,name,specification,manufacturer,main_unit,aux_unit,main_to_aux_ratio) VALUES(?,?,NULL,NULL,'袋',NULL,NULL)",id,String.format("P%02d",id));
            jdbc.update("INSERT INTO inventory VALUES(?,?,1,100)",id,id);
            if(id!=5)jdbc.update("INSERT INTO product_unit VALUES(?,?,'袋',1,1,?)",100+id,id,id==8?2:1);
            Long batchUnit = id==9 ? null : Long.valueOf(id==4?999:100+id);
            jdbc.update("INSERT INTO inventory_receipt_batch VALUES(?,?,?)",id,batchUnit,java.time.LocalDate.now().minusDays(1));
            jdbc.update("INSERT INTO inventory_batch_balance VALUES(?,1,?,?,?,?)",id,id,id==3?2:1,id==1?0:id==10?0.0001:3,id==2?3:0);
        }
        jdbc.update("INSERT INTO inventory_batch_balance VALUES(2,2,2,1,99,0)");
        var config=new FlexConfiguration(new Environment("page",new JdbcTransactionFactory(),new FlexDataSource("page",source,false)));
        config.setMapUnderscoreToCamelCase(true);config.addMapper(InventoryMapper.class);
        session=new FlexSqlSessionFactoryBuilder().build(config).openSession(true);repository=new InventoryRepositoryImpl(session.getMapper(InventoryMapper.class));
    }
    @AfterEach void close(){TenantManager.setTenantFactory(previous);if(session!=null)session.close();releaseDataSource();}
    protected DataSource dataSource() {
        var source=new JdbcDataSource();source.setURL("jdbc:h2:mem:stockpage_"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1");return source;
    }
    protected void releaseDataSource(){if(jdbc!=null)jdbc.execute("SHUTDOWN");}
    @Test void saleablePredicateRunsBeforeCountingAndPagingAndDatesAreOnlyReference() {
        var first=repository.findPage(1,1,1L,null,false,true);assertEquals(3,first.getTotalRow());assertEquals(6L,first.getRecords().get(0).getProductId());
        var second=repository.findPage(2,1,1L,null,false,true);assertEquals(7L,second.getRecords().get(0).getProductId());
        var third=repository.findPage(3,1,1L,null,false,true);assertEquals(10L,third.getRecords().get(0).getProductId());
        assertEquals(10,repository.findPage(1,20,1L,null,true,false).getTotalRow(),"旧在手筛选语义不改");
    }
    @Test void allStoresKeepIndependentRowsAndDoNotSumAcrossStores() {
        jdbc.update("INSERT INTO inventory VALUES(99,2,2,99)");
        var result=repository.findPage(1,20,null,null,false,true);
        assertEquals(4,result.getTotalRow());
        var other=result.getRecords().stream().filter(r->r.getProductId().equals(2L)).findFirst().orElseThrow();
        assertEquals(2L,other.getStoreId());
    }
    @Test void duplicateValidBaseUnitsAreUnknownWithoutScalarSubqueryFailure() {
        jdbc.update("INSERT INTO product_unit VALUES(707,7,'另一基准',1,1,1)");
        assertEquals(2,repository.findPage(1,20,1L,null,false,true).getTotalRow());
    }
    @Test void anyEligibleMismatchedBatchMakesTheQuantityUnknownNotSaleable() {
        jdbc.update("INSERT INTO inventory_batch_balance VALUES(4,1,6,1,1,0)");
        assertEquals(2,repository.findPage(1,20,1L,null,false,true).getTotalRow());
    }
}
