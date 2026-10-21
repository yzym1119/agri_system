package com.agri_system.infrastructure.order.repository;

import com.agri_system.infrastructure.order.mapper.OrderMapper;
import com.agri_system.domain.order.model.Order;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.paginate.Page;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

/** 执行仓储生成SQL，验证配送读取真实关系和库存事实。 */
class OrderDeliveryRepositoryQueryTest {
    private OrderRepositoryImpl repository;
    private JdbcDataSource source;
    @BeforeEach void setup() throws Exception {
        source = new JdbcDataSource();source.setURL("jdbc:h2:mem:delivery_"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1");
        execute("CREATE TABLE `order`(id BIGINT PRIMARY KEY,store_id BIGINT,customer_id BIGINT,order_type INT,order_status INT,created_at TIMESTAMP,updated_at TIMESTAMP)");
        execute("CREATE TABLE store(id BIGINT PRIMARY KEY)");execute("CREATE TABLE customer(id BIGINT PRIMARY KEY)");
        execute("CREATE TABLE order_delivery(id BIGINT PRIMARY KEY,order_id BIGINT,delivery_type INT,delivery_status INT,delivery_at TIMESTAMP)");
        execute("CREATE TABLE order_item(id BIGINT PRIMARY KEY,order_id BIGINT,product_name VARCHAR(100) DEFAULT '测试商品')");
        execute("CREATE TABLE order_item_batch_allocation(id BIGINT PRIMARY KEY,order_item_id BIGINT,store_id BIGINT,product_id BIGINT,batch_id BIGINT,location_id BIGINT,source_movement_id BIGINT,allocated_base_quantity DECIMAL(18,8))");
        execute("CREATE TABLE inventory_movement(id BIGINT PRIMARY KEY,store_id BIGINT,product_id BIGINT,batch_id BIGINT,from_location_id BIGINT,movement_type VARCHAR(30),base_quantity DECIMAL(18,8),reference_type VARCHAR(40),reference_id BIGINT)");
        OrderMapper mapper=mock(OrderMapper.class);
        when(mapper.selectCountByQuery(any(QueryWrapper.class))).thenAnswer(c -> {
            try(var connection=source.getConnection();var st=connection.createStatement();var rs=st.executeQuery(c.<QueryWrapper>getArgument(0).toSQL())) {rs.next();return rs.getLong(1);}
        });
        when(mapper.selectListByQuery(any(QueryWrapper.class))).thenAnswer(c -> query(c.getArgument(0)));
        when(mapper.paginate(any(Page.class),any(QueryWrapper.class))).thenAnswer(c -> {
            Page<Order> page=c.getArgument(0);page.setRecords(query(c.getArgument(1)));return page;
        });
        when(mapper.selectListByQueryAs(any(QueryWrapper.class),any(Class.class))).thenAnswer(c -> {
            List<Object> rows=new ArrayList<>();Class<?> type=c.getArgument(1);
            try(var connection=source.getConnection();var st=connection.createStatement();var rs=st.executeQuery(c.<QueryWrapper>getArgument(0).toSQL())) {
                while(rs.next()) {var row=type.getConstructor().newInstance();
                    type.getMethod("setOrderItemId",Long.class).invoke(row,rs.getLong("order_item_id"));
                    type.getMethod("setProductName",String.class).invoke(row,rs.getString("product_name"));
                    type.getMethod("setQuantity",java.math.BigDecimal.class).invoke(row,rs.getBigDecimal("quantity"));
                    type.getMethod("setUnit",String.class).invoke(row,rs.getString("unit"));rows.add(row);}
            }return rows;
        });
        execute("CREATE TABLE inventory_receipt_batch(id BIGINT PRIMARY KEY,product_id BIGINT,store_id BIGINT,base_unit_id BIGINT)");
        execute("CREATE TABLE product_unit(id BIGINT PRIMARY KEY,product_id BIGINT,unit_name VARCHAR(30))");
        repository=new OrderRepositoryImpl(mapper);
    }
    @Test void localFilterIncludesPosAndNeedsRealDeliveryRelation() throws Exception {
        order(1,1,4);order(2,3,4);order(3,3,4);
        execute("INSERT INTO order_delivery VALUES(1,1,2,2,CURRENT_TIMESTAMP),(2,2,3,2,CURRENT_TIMESTAMP)");
        assertEquals(List.of(1L),repository.findPage(1,10,null,null,null,2,null,null,null,null).getRecords().stream().map(Order::getId).toList());
        assertEquals(List.of(2L),repository.findPage(1,10,null,null,null,3,null,null,null,null).getRecords().stream().map(Order::getId).toList());
    }
    @Test void autoFinishUsesExpressDeliveryTimeIncludingPartialReturn() throws Exception {
        order(1,1,5);order(2,3,9);order(3,3,5);order(4,3,5);
        execute("INSERT INTO order_delivery VALUES(1,1,2,3,DATEADD('DAY',-20,CURRENT_TIMESTAMP)),(2,2,3,3,DATEADD('DAY',-20,CURRENT_TIMESTAMP)),(3,3,3,3,DATEADD('DAY',-2,CURRENT_TIMESTAMP))");
        assertEquals(List.of(2L),repository.findShippedOrdersBefore(LocalDateTime.now().minusDays(10)).stream().map(Order::getId).toList());
    }
    @Test void remainingGoodsRequireActualSaleAndOnlyPositiveAllocationReturnsReduceQuantity() throws Exception {
        order(1,1,4);execute("INSERT INTO order_item(id,order_id) VALUES(11,1)");
        execute("INSERT INTO order_item_batch_allocation VALUES(20,11,3,5,7,8,30,10)");
        assertFalse(remaining());
        execute("INSERT INTO inventory_movement VALUES(30,3,5,7,8,'SALE',10,'ORDER',1)");assertTrue(remaining());
        execute("INSERT INTO inventory_movement VALUES(31,3,5,7,NULL,'RETURN',9,'SALE_ALLOCATION',20),(32,3,5,7,NULL,'RETURN',100,'ORDER',1)");assertTrue(remaining());
        execute("INSERT INTO inventory_movement VALUES(33,3,5,7,NULL,'RETURN',1,'SALE_ALLOCATION',20),(34,3,5,7,NULL,'RETURN',-100,'SALE_ALLOCATION',20)");assertFalse(remaining());
        execute("DELETE FROM inventory_movement WHERE id IN (31,33,34)");
        execute("UPDATE inventory_movement SET base_quantity=-10 WHERE id=30");assertFalse(remaining());
    }
    @Test void deliveryStatusFilterReadsRelationForPosOrders() throws Exception {
        order(1,1,9);order(2,1,9);
        execute("INSERT INTO order_delivery VALUES(1,1,2,2,CURRENT_TIMESTAMP),(2,2,2,3,CURRENT_TIMESTAMP)");
        var result=repository.findPage(1,10,null,null,null,2,null,null,null,null,2);
        assertEquals(List.of(1L),result.getRecords().stream().map(Order::getId).toList());
    }
    @Test void mismatchedReturnReferenceDoesNotConsumeRemainingGoods() throws Exception {
        order(1,1,4);execute("INSERT INTO order_item(id,order_id) VALUES(11,1)");
        execute("INSERT INTO order_item_batch_allocation VALUES(20,11,3,5,7,8,30,10)");
        execute("INSERT INTO inventory_movement VALUES(30,3,5,7,8,'SALE',10,'ORDER',1),(31,4,5,7,NULL,'RETURN',10,'SALE_ALLOCATION',20)");
        assertTrue(remaining());
    }
    @Test void deliveryPreviewReadsActualRemainingAllocationsAndPersistedUnits() throws Exception {
        order(1,1,9);execute("INSERT INTO order_item(id,order_id) VALUES(11,1)");
        execute("INSERT INTO order_item_batch_allocation VALUES(20,11,3,5,7,8,30,6),(21,11,3,5,9,8,31,4)");
        execute("INSERT INTO inventory_movement VALUES(30,3,5,7,8,'SALE',6,'ORDER',1),(31,3,5,9,8,'SALE',4,'ORDER',1),(32,3,5,7,NULL,'RETURN',1,'SALE_ALLOCATION',20),(33,3,5,9,NULL,'RETURN',4,'SALE_ALLOCATION',21),(34,4,5,7,NULL,'RETURN',100,'SALE_ALLOCATION',20),(35,3,5,7,NULL,'RETURN',-50,'SALE_ALLOCATION',20)");
        execute("INSERT INTO product_unit VALUES(100,5,'袋')");execute("INSERT INTO inventory_receipt_batch VALUES(7,5,3,100),(9,5,3,100)");
        var method=repository.getClass().getMethod("findRemainingDeliveryItems",Long.class);
        var rows=(List<?>)method.invoke(repository,1L);assertEquals(1,rows.size());var row=rows.get(0);
        assertEquals(0,new java.math.BigDecimal("5").compareTo((java.math.BigDecimal)row.getClass().getMethod("getQuantity").invoke(row)));
        assertEquals("袋",row.getClass().getMethod("getUnit").invoke(row));
        execute("DELETE FROM product_unit");rows=(List<?>)method.invoke(repository,1L);
        assertNull(rows.get(0).getClass().getMethod("getUnit").invoke(rows.get(0)),"未知原单位不得猜当前商品单位");
        execute("UPDATE inventory_movement SET base_quantity=3 WHERE id=30");
        assertTrue(((List<?>)method.invoke(repository,1L)).isEmpty(),"非法原出库不能列为可签收商品");
    }
    private boolean remaining() throws Exception {
        try {return (boolean)repository.getClass().getMethod("hasRemainingDeliveryQuantity",Long.class).invoke(repository,1L);}
        catch(NoSuchMethodException missing) {return false;}
    }
    private void order(int id,int type,int status) throws Exception {execute("INSERT INTO `order` VALUES("+id+",3,NULL,"+type+","+status+",CURRENT_TIMESTAMP,DATEADD('DAY',-50,CURRENT_TIMESTAMP))");}
    private void execute(String sql) throws Exception {try(var c=source.getConnection();var s=c.createStatement()){s.execute(sql);}}
    private List<Order> query(QueryWrapper wrapper) throws Exception {
        String sql = wrapper.toSQL().toUpperCase().replaceAll("\\s+", " ");
        if (sql.contains("FROM WHERE") || !sql.contains(" FROM ")) wrapper.from("order");
        var result=new ArrayList<Order>();try(var c=source.getConnection();var s=c.createStatement();var rs=s.executeQuery(wrapper.toSQL())) {while(rs.next()){var order=new Order();order.setId(rs.getLong("id"));result.add(order);}}return result;
    }
}
