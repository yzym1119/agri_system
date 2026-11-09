package com.agri_system.infrastructure.order.query;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import javax.sql.DataSource;
import static org.junit.jupiter.api.Assertions.*;

class PickupPreparationQuerySqlTest {
    JdbcTemplate jdbc;PickupTaskQueryImpl query;
    protected DataSource dataSource(){var s=new JdbcDataSource();s.setURL("jdbc:h2:mem:prep_"+java.util.UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1");return s;}
    protected void releaseDataSource(){if(jdbc!=null)jdbc.execute("SHUTDOWN");}
    @BeforeEach void setup(){jdbc=new JdbcTemplate(dataSource());query=new PickupTaskQueryImpl(jdbc);
        jdbc.execute("CREATE TABLE `order`(id BIGINT PRIMARY KEY,store_id BIGINT,customer_id BIGINT,order_no VARCHAR(50),deleted INT)");
        jdbc.execute("CREATE TABLE customer(id BIGINT PRIMARY KEY,name VARCHAR(100))");jdbc.execute("CREATE TABLE product(id BIGINT PRIMARY KEY,name VARCHAR(100))");
        jdbc.execute("CREATE TABLE order_pickup_request(id BIGINT PRIMARY KEY,order_id BIGINT,store_id BIGINT,product_id BIGINT,status VARCHAR(20),created_at TIMESTAMP,fulfilled_at TIMESTAMP)");
        jdbc.execute("INSERT INTO `order` VALUES(50,3,7,'O50',0),(51,4,7,'O51',0),(52,3,7,'O52',1)");jdbc.execute("INSERT INTO customer VALUES(7,'虚构客户')");jdbc.execute("INSERT INTO product VALUES(9,'测试商品')");
        for(int n=1;n<=25;n++)jdbc.update("INSERT INTO order_pickup_request VALUES(?,50,3,9,?,CURRENT_TIMESTAMP,NULL)",n,n==25?"FULFILLED":"PENDING");
        jdbc.execute("INSERT INTO order_pickup_request VALUES(90,51,4,9,'PENDING',CURRENT_TIMESTAMP,NULL),(91,52,3,9,'PENDING',CURRENT_TIMESTAMP,NULL),(92,50,4,9,'PENDING',CURRENT_TIMESTAMP,NULL)");
    }
    @AfterEach void close(){releaseDataSource();}
    @Test void wholeOrderReturnsEveryRequestIncludingCompletedAndNeverForeignOrDeleted(){
        var rows=query.findOrderLinesForOrder(3L,50L);assertEquals(25,rows.size());assertEquals(1L,rows.get(0).requestId());assertEquals("FULFILLED",rows.get(24).status());
        assertTrue(query.findOrderLinesForOrder(3L,51L).isEmpty());assertTrue(query.findOrderLinesForOrder(3L,52L).isEmpty());assertTrue(query.findOrderLinesForOrder(4L,50L).isEmpty());
    }
    @Test void missingStoreOrOrderIsRejectedRatherThanListingAll(){
        assertThrows(IllegalArgumentException.class,()->query.findOrderLinesForOrder(null,50L));assertThrows(IllegalArgumentException.class,()->query.findOrderLinesForOrder(3L,null));
    }
}
