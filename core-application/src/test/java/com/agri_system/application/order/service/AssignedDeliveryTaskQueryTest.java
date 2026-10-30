package com.agri_system.application.order.service;

import com.agri_system.domain.store.model.Staff;
import com.agri_system.domain.store.repository.StaffRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AssignedDeliveryTaskQueryTest {
    JdbcTemplate jdbc;Staff actor;DeliveryTaskService service;
    @BeforeEach void setup(){
        jdbc=new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:delivery_"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa",""));
        jdbc.execute("CREATE TABLE `order`(id BIGINT PRIMARY KEY,order_no VARCHAR(50),store_id BIGINT,deleted INT)");
        jdbc.execute("CREATE TABLE order_delivery(order_id BIGINT PRIMARY KEY,delivery_type INT,delivery_status INT,delivery_staff_id BIGINT,delivery_at TIMESTAMP,received_at TIMESTAMP)");
        var staff=mock(StaffRepository.class);actor=new Staff();actor.setId(8L);actor.setStoreId(3L);actor.setStatus(1);
        when(staff.findIdentityById(8L)).thenReturn(Optional.of(actor));service=new DeliveryTaskService(jdbc,staff);
    }
    @AfterEach void cleanup(){jdbc.execute("DROP ALL OBJECTS");}
    void task(long id,long store,long owner,int type,int state){jdbc.update("INSERT INTO `order` VALUES(?,?,?,0)",id,"TASK"+id,store);
        jdbc.update("INSERT INTO order_delivery VALUES(?,?,?,?,CURRENT_TIMESTAMP,NULL)",id,type,state,owner);}
    @Test void taskQueryFiltersOwnerStoreTypeAndStateBeforeCursorPagination(){
        for(int i=1;i<=23;i++)task(i,3,8,2,3);
        task(100,3,9,2,3);task(101,4,8,2,3);task(102,3,8,3,3);task(103,3,8,2,4);
        var first=service.listAssignedTasks(8L,"PENDING",null);assertEquals("3",first.storeId());assertEquals(20,first.tasks().size());
        assertEquals("23",first.tasks().get(0).orderId());assertEquals("4",first.nextCursor());
        var next=service.listAssignedTasks(8L,"PENDING",4L);assertEquals(3,next.tasks().size());assertNull(next.nextCursor());
        var history=service.listAssignedTasks(8L,"COMPLETED",null);assertEquals(1,history.tasks().size());assertEquals("103",history.tasks().get(0).orderId());
    }
    @Test void unsafeCursorInactiveStaffAndMissingStoreNeverReadTaskData(){
        assertThrows(IllegalArgumentException.class,()->service.listAssignedTasks(8L,"ALL",null));
        assertThrows(IllegalArgumentException.class,()->service.listAssignedTasks(8L,"PENDING",0L));
        actor.setStoreId(null);assertThrows(IllegalArgumentException.class,()->service.listAssignedTasks(8L,"PENDING",null));
        actor.setStoreId(3L);actor.setStatus(2);assertThrows(IllegalArgumentException.class,()->service.listAssignedTasks(8L,"PENDING",null));
    }
    @Test void longIdsStayExactAndDeletedOrdersAreExcluded(){
        task(Long.MAX_VALUE,3,8,2,3);task(1,3,8,2,3);jdbc.update("UPDATE `order` SET deleted=1 WHERE id=1");
        var result=service.listAssignedTasks(8L,"PENDING",null);assertEquals(1,result.tasks().size());assertEquals("9223372036854775807",result.tasks().get(0).orderId());
    }
    @Test void permissionMigrationAddsOnlyOneIndependentOperationWithoutRoleGrants() throws Exception {
        jdbc.execute("CREATE TABLE sys_menu(id BIGINT AUTO_INCREMENT PRIMARY KEY,parent_id BIGINT,name VARCHAR(50),permission VARCHAR(100),type INT,path VARCHAR(255),icon VARCHAR(50),sort_order INT,visible INT)");
        jdbc.execute("CREATE TABLE sys_role_menu(role_id BIGINT,menu_id BIGINT)");
        jdbc.update("INSERT INTO sys_menu(id,name,permission,type) VALUES(1300,'订单','order',1)");jdbc.update("INSERT INTO sys_role_menu VALUES(4,1300)");
        var script=new org.springframework.core.io.ClassPathResource("db/migration/V134__assigned_delivery_permission.sql");
        try(var c=jdbc.getDataSource().getConnection()) {org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(c,script);org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(c,script);}
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM sys_menu WHERE permission='order:delivery:assigned' AND parent_id=1300 AND type=2",Integer.class));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM sys_role_menu",Integer.class));
    }
}
