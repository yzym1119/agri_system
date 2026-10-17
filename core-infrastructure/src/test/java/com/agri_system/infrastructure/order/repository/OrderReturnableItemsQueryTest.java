package com.agri_system.infrastructure.order.repository;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrderReturnableItemsQueryTest {
    @Test void 整单剩余量包含第四行零元物料并扣除未拒绝售后占用() throws Exception {
        var jdbc=new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:returnable_"+java.util.UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa",""));
        jdbc.execute("CREATE TABLE order_item(id BIGINT,order_id BIGINT,quantity DECIMAL(18,4),total_price DECIMAL(18,2))");
        jdbc.execute("CREATE TABLE after_sales(id BIGINT,order_id BIGINT,status INT)");
        jdbc.execute("CREATE TABLE after_sales_item(after_sales_id BIGINT,order_item_id BIGINT,quantity DECIMAL(18,4))");
        // 1全部退完；2前三行已退、第四行仍有零元物料；3被进行中售后占用；4拒绝售后不占用；5半袋剩余。
        jdbc.execute("INSERT INTO order_item VALUES(11,1,1,10),(21,2,1,10),(22,2,1,10),(23,2,1,10),(24,2,1,0),(31,3,1,0),(41,4,1,0),(51,5,1,10),(61,6,1,10)");
        jdbc.execute("INSERT INTO after_sales VALUES(1,1,50),(2,2,50),(3,3,40),(4,4,90),(5,5,50),(6,6,10),(7,99,50)");
        jdbc.execute("INSERT INTO after_sales_item VALUES(1,11,1),(2,21,1),(2,22,1),(2,23,1),(3,31,1),(4,41,1),(5,51,0.5),(6,61,1),(7,24,1)");
        var repository=mock(OrderItemRepositoryImpl.class,CALLS_REAL_METHODS);
        Set<Long> actual;
        try {
            var method=repository.getClass().getMethod("findOrderIdsWithReturnableItems",List.class);
            var field=OrderItemRepositoryImpl.class.getDeclaredField("jdbc");field.setAccessible(true);field.set(repository,jdbc);
            actual=(Set<Long>)method.invoke(repository,List.of(1L,2L,3L,4L,5L,6L));
        } catch(NoSuchMethodException missing) {actual=Set.of();}
        assertEquals(Set.of(2L,4L,5L),actual);
    }
}
