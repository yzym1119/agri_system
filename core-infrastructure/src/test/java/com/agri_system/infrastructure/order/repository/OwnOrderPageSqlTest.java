package com.agri_system.infrastructure.order.repository;

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
import javax.sql.DataSource;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;

class OwnOrderPageSqlTest {
    JdbcTemplate jdbc; SqlSession session; OrderRepositoryImpl repository; TenantFactory previous;
    @BeforeEach void setup() {
        previous=TenantManager.getTenantFactory();TenantManager.setTenantFactory(null);
        var source=dataSource();jdbc=new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE `order`(id BIGINT PRIMARY KEY,order_no VARCHAR(50),store_id BIGINT,staff_id BIGINT,customer_id BIGINT,order_type INT,order_status INT,total_amount DECIMAL(12,2),discount_amount DECIMAL(12,2),coupon_amount DECIMAL(12,2),pay_amount DECIMAL(12,2),payment_method INT,receiver_name VARCHAR(100),delivery_phone VARCHAR(50),delivery_address VARCHAR(500),pickup_code VARCHAR(50),remark VARCHAR(500),deleted INT DEFAULT 0,created_at TIMESTAMP,updated_at TIMESTAMP,created_by BIGINT)");
        for(int n=1;n<=25;n++) jdbc.update("INSERT INTO `order`(id,order_no,store_id,staff_id,order_type,order_status,created_at,created_by) VALUES(?,?,3,8,1,6,?,8)",n,"O"+n,LocalDateTime.of(2026,11,9,12,0));
        // 更大的编号不应挤占本人本店的21条候选。
        jdbc.update("INSERT INTO `order`(id,order_no,store_id,staff_id,order_type,order_status,created_at,created_by,deleted) VALUES(90,'OTHER',3,9,1,6,'2026-11-09 12:00:00',8,0),(91,'STORE',4,8,1,6,'2026-11-09 12:00:00',8,0),(92,'LEGACY',3,NULL,1,6,'2026-11-09 12:00:00',8,0),(93,'ONLINE',3,8,2,6,'2026-11-09 12:00:00',8,0),(94,'DELETED',3,8,1,6,'2026-11-09 12:00:00',8,1)");
        var config=new FlexConfiguration(new Environment("own",new JdbcTransactionFactory(),new FlexDataSource("own",source,false)));
        config.setMapUnderscoreToCamelCase(true);config.addMapper(OrderMapper.class);
        session=new FlexSqlSessionFactoryBuilder().build(config).openSession(true);
        repository=new OrderRepositoryImpl(session.getMapper(OrderMapper.class));
    }
    protected DataSource dataSource(){var s=new JdbcDataSource();s.setURL("jdbc:h2:mem:own_"+java.util.UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1");return s;}
    protected void releaseDataSource(){if(jdbc!=null)jdbc.execute("SHUTDOWN");}
    @AfterEach void close(){TenantManager.setTenantFactory(previous);if(session!=null)session.close();releaseDataSource();}
    @Test void filtersOwnershipAndMissingStaffBeforeCursorLimit() {
        var rows=repository.findOwnSales(3L,8L,null,null,null,null);
        assertEquals(21,rows.size());assertEquals(25L,rows.get(0).getId());assertEquals(5L,rows.get(20).getId());
        var next=repository.findOwnSales(3L,8L,6L,null,null,null);assertEquals(5,next.size());assertEquals(5L,next.get(0).getId());
        var other=repository.findOwnSales(3L,9L,null,null,null,null);assertEquals(1,other.size());assertEquals(90L,other.get(0).getId());
    }
    @Test void filtersDateHalfOpenAndStateWithoutChangingStoredFacts() {
        var from=LocalDateTime.of(2026,11,9,0,0);var to=from.plusDays(1);
        jdbc.update("UPDATE `order` SET created_at=? WHERE id=25",from);
        jdbc.update("UPDATE `order` SET created_at=?,order_status=9 WHERE id=24",to.minusSeconds(1));
        jdbc.update("UPDATE `order` SET created_at=? WHERE id=23",to);
        var rows=repository.findOwnSales(3L,8L,null,from,to,9);assertEquals(1,rows.size());assertEquals(24L,rows.get(0).getId());
        assertTrue(repository.findOwnSales(3L,8L,null,from,to,6).stream().noneMatch(o->o.getId()==23));
        assertTrue(repository.findOwnSales(3L,8L,null,from,to,6).stream().anyMatch(o->o.getId()==25));
    }
    @Test void missingScopeNeverBecomesAllStoreQuery() {
        assertThrows(IllegalArgumentException.class,()->repository.findOwnSales(null,8L,null,null,null,null));
        assertThrows(IllegalArgumentException.class,()->repository.findOwnSales(3L,null,null,null,null,null));
    }
}
