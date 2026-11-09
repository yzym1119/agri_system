package com.agri_system.infrastructure.inventory.repository;

import com.agri_system.infrastructure.inventory.mapper.InventoryBatchBalanceMapper;
import com.mybatisflex.core.datasource.FlexDataSource;
import com.mybatisflex.core.mybatis.FlexConfiguration;
import com.mybatisflex.core.mybatis.FlexSqlSessionFactoryBuilder;
import com.mybatisflex.core.tenant.TenantFactory;
import com.mybatisflex.core.tenant.TenantManager;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class SaleableStockQueryTest {
    JdbcTemplate jdbc; SqlSession session; InventoryBatchBalanceRepositoryImpl repository; TenantFactory previous;
    @BeforeEach void setup() {
        previous=TenantManager.getTenantFactory();TenantManager.setTenantFactory(null);
        var source=new JdbcDataSource();source.setURL("jdbc:h2:mem:saleable_"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1");
        jdbc=new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE inventory_receipt_batch(id BIGINT,base_unit_id BIGINT,expiry_date DATE)");
        jdbc.execute("CREATE TABLE inventory_location(id BIGINT,status INT)");
        jdbc.execute("CREATE TABLE inventory_batch_balance(batch_id BIGINT,store_id BIGINT,product_id BIGINT,location_id BIGINT,base_quantity DECIMAL(20,8),reserved_base_quantity DECIMAL(20,8))");
        jdbc.execute("INSERT INTO inventory_location VALUES(1,1),(2,0)");
        jdbc.execute("INSERT INTO inventory_receipt_batch VALUES(1,100,NULL),(2,100,CURRENT_DATE),(3,100,DATEADD('DAY',-1,CURRENT_DATE)),(4,101,NULL),(5,200,NULL)");
        jdbc.execute("INSERT INTO inventory_batch_balance VALUES(1,1,10,1,10,3),(2,1,10,1,5,0),(3,1,10,1,100,0),(1,1,10,2,100,0),(1,2,10,1,100,0),(1,1,10,1,3,3),(4,1,10,1,2,0),(5,1,20,1,0.0001,0)");
        var config=new FlexConfiguration(new Environment("saleable",new JdbcTransactionFactory(),new FlexDataSource("saleable",source,false)));
        config.setMapUnderscoreToCamelCase(true);config.addMapper(InventoryBatchBalanceMapper.class);
        session=new FlexSqlSessionFactoryBuilder().build(config).openSession(true);
        repository=new InventoryBatchBalanceRepositoryImpl(session.getMapper(InventoryBatchBalanceMapper.class));
    }
    @AfterEach void close() {TenantManager.setTenantFactory(previous);if(session!=null)session.close();if(jdbc!=null)jdbc.execute("SHUTDOWN");}
    @Test void saleableMatchesOutboundFiltersAndKeepsUnitEvidence() {
        var rows=repository.findSaleableStock(1L,List.of(10L,20L));assertEquals(3,rows.size());
        var primary=rows.stream().filter(r->r.productId().equals(10L)&&r.baseUnitId().equals(100L)).findFirst().orElseThrow();
        assertEquals(0,new BigDecimal("112").compareTo(primary.availableBaseQuantity()),"日期仅提醒：昨天/今天/未知均保留，扣预留，排除停用/别店");
        assertEquals(2,rows.stream().filter(r->r.productId().equals(10L)).count(),"不同原批单位不能合并");
        assertEquals(0,new BigDecimal("0.0001").compareTo(rows.stream().filter(r->r.productId().equals(20L)).findFirst().orElseThrow().availableBaseQuantity()));
        assertEquals(2,repository.findSaleableStock(1L,List.of(10L)).size(),"只查询当前页商品");
    }
    @Test void noCandidatesIsEmptyAndMissingScopeIsRejected() {
        assertTrue(repository.findSaleableStock(1L,List.of(30L)).isEmpty());
        assertTrue(repository.findSaleableStock(1L,List.of()).isEmpty());
        assertThrows(IllegalArgumentException.class,()->repository.findSaleableStock(null,List.of(10L)));
    }
}
