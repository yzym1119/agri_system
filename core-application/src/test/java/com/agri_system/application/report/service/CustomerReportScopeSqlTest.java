package com.agri_system.application.report.service;

import com.agri_system.infrastructure.customer.mapper.CustomerMapper;
import com.agri_system.infrastructure.order.mapper.OrderMapper;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** 真实Flex SQL证明客户报表的门店订单范围、逻辑删除、阈值与租户交集。 */
class CustomerReportScopeSqlTest {
    private JdbcTemplate jdbc;
    private SqlSession session;
    private ReportAnalysisService service;
    private TenantFactory originalTenant;
    @BeforeEach void setup() {
        originalTenant=TenantManager.getTenantFactory();TenantManager.setTenantFactory(null);
        var source=new JdbcDataSource();source.setURL("jdbc:h2:mem:customer_report_"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1");jdbc=new JdbcTemplate(source);
        jdbc.execute("""
            CREATE TABLE customer(id BIGINT PRIMARY KEY,code VARCHAR(50),name VARCHAR(100),company_name VARCHAR(100),tax_id VARCHAR(100),
            invoice_title VARCHAR(100),invoice_bank VARCHAR(100),invoice_address VARCHAR(100),invoice_phone VARCHAR(50),phone VARCHAR(50),type INT,vip_level INT,
            vip_start_date DATE,vip_end_date DATE,address VARCHAR(200),land_area DECIMAL(18,4),crop_type VARCHAR(100),id_card VARCHAR(50),credit_limit DECIMAL(18,2),
            status INT,auth_version BIGINT NOT NULL DEFAULT 1,remark VARCHAR(200),created_at TIMESTAMP,updated_at TIMESTAMP,created_by BIGINT,updated_by BIGINT,total_order_count INT,
            total_order_amount DECIMAL(18,2),last_order_time TIMESTAMP,bind_staff_id BIGINT,version INT,deleted INT)
            """);
        jdbc.execute("CREATE TABLE `order`(id BIGINT PRIMARY KEY,store_id BIGINT,customer_id BIGINT,deleted INT)");
        customer(1,"本店",2000,120,0);customer(2,"他店",9000,130,0);customer(3,"跨店客户",3000,100,0);customer(4,"无有效订单",5000,150,0);
        customer(5,"已删除",10000,200,1);customer(6,"千元阈值",1000,150,0);customer(7,"近期客户",6000,10,0);customer(8,"金额未知",null,150,0);customer(9,"日期未知",7000,null,0);
        jdbc.execute("INSERT INTO `order` VALUES(1,1,1,0),(2,1,3,0),(3,2,2,0),(4,2,3,0),(5,1,5,0),(6,1,6,0),(7,1,7,0),(8,1,8,0),(9,1,9,0),(10,1,1,0),(11,1,4,1)");
        var config=new FlexConfiguration(new Environment("customer-report",new JdbcTransactionFactory(),new FlexDataSource("customer-report",source,false)));
        config.setMapUnderscoreToCamelCase(true);config.setLocalCacheScope(org.apache.ibatis.session.LocalCacheScope.STATEMENT);config.addMapper(OrderMapper.class);config.addMapper(CustomerMapper.class);
        session=new FlexSqlSessionFactoryBuilder().build(config).openSession(true);
        service=new ReportAnalysisService(null,null,null,new com.agri_system.infrastructure.customer.query.CustomerReportQueryImpl(session.getMapper(CustomerMapper.class),session.getMapper(OrderMapper.class)),null,null);
    }
    @AfterEach void close() {TenantManager.setTenantFactory(originalTenant);if(session!=null)session.close();if(jdbc!=null)jdbc.execute("SHUTDOWN");}
    private void customer(long id,String name,Integer amount,Integer days,int deleted) {
        jdbc.update("INSERT INTO customer(id,name,phone,total_order_amount,last_order_time,created_at,deleted) VALUES(?,?,?,?,?,?,?)",id,name,"TEST-"+id,amount,days==null?null:LocalDateTime.now().minusDays(days),LocalDateTime.now(),deleted);
    }
    private List<Long> top(Long store,int limit) {return service.topCustomers(limit,store).stream().map(r->r.getCustomerId()).toList();}
    private List<Long> churn(Long store) {return service.churnRisks(store).stream().map(r->r.getCustomerId()).toList();}
    @Test void administratorKeepsAllStoresAndExplicitStoreMembershipWithSqlLimitAndFilters() {
        assertEquals(List.of(2L,9L,7L,4L,3L,1L,6L),top(null,20));
        assertEquals(List.of(9L,7L,3L,1L,6L),top(1L,20));assertEquals(List.of(9L,7L),top(1L,2));assertEquals(List.of(2L,3L),top(2L,20));
        assertEquals(List.of(1L,3L),churn(1L));assertEquals("TEST-1",service.topCustomers(20,1L).stream().filter(r->r.getCustomerId()==1L).findFirst().orElseThrow().getPhone());
    }
    @Test void employeeOmittedStoreDoesNotExposeOtherStoresOrGlobalDirectory() {
        TenantManager.setTenantFactory(()->new Object[]{1L});
        assertEquals(List.of(9L,7L,3L,1L,6L),top(null,20));assertEquals(List.of(1L,3L),churn(null));
        assertTrue(top(2L,20).isEmpty());assertTrue(churn(2L).isEmpty());
    }
    @Test void emptyTenantRangeDoesNotBecomeAdministratorGlobalView() {
        TenantManager.setTenantFactory(()->new Object[0]);jdbc.execute("DROP TABLE customer");
        assertTrue(top(null,20).isEmpty());assertTrue(churn(null).isEmpty());
    }
    @Test void emptyStoreDoesNotFallBackToGlobalCustomerQuery() {
        jdbc.execute("DROP TABLE customer");assertTrue(top(3L,20).isEmpty());assertTrue(churn(3L).isEmpty());
    }
}
