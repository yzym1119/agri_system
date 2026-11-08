package com.agri_system.application.order.service;

import com.agri_system.application.order.dto.vo.PickupWorkItemVO;
import com.agri_system.domain.store.model.Staff;
import com.agri_system.domain.store.repository.StaffRepository;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MobilePickupTaskTest {
    JdbcTemplate jdbc; Staff actor; PickupFulfillmentService pickup; MobilePickupTaskService service;
    @BeforeEach void setup() {
        jdbc=new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:pickup_mobile_"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa",""));
        jdbc.execute("CREATE TABLE `order`(id BIGINT PRIMARY KEY,order_no VARCHAR(50),store_id BIGINT,customer_id BIGINT,deleted INT)");
        jdbc.execute("CREATE TABLE customer(id BIGINT PRIMARY KEY,name VARCHAR(100))");
        jdbc.execute("CREATE TABLE order_pickup_request(id BIGINT PRIMARY KEY,order_id BIGINT,store_id BIGINT,product_id BIGINT,status VARCHAR(20),created_at TIMESTAMP,fulfilled_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE product(id BIGINT PRIMARY KEY,name VARCHAR(100))");
        var staff=mock(StaffRepository.class);actor=new Staff();actor.setId(8L);actor.setStoreId(3L);actor.setStatus(1);
        when(staff.findIdentityById(8L)).thenReturn(Optional.of(actor));pickup=mock(PickupFulfillmentService.class);
        service=new MobilePickupTaskService(new com.agri_system.infrastructure.order.query.PickupTaskQueryImpl(jdbc),staff,pickup);
    }
    @AfterEach void cleanup(){jdbc.execute("DROP ALL OBJECTS");}
    void task(long id,long store,String status) {
        jdbc.update("INSERT INTO `order` VALUES(?,?,?,NULL,0)",id,"ORDER"+id,store);
        jdbc.update("INSERT INTO order_pickup_request VALUES(?,?,?,?,?,CURRENT_TIMESTAMP,NULL)",id,id,store,99,status);
    }
    @Test void warehouseListsOwnStoreAcrossRequestCreatorsAndPaginatesByOriginalOrder() {
        for(int i=1;i<=23;i++)task(i,3,"PENDING");task(100,4,"PENDING");task(101,3,"FULFILLED");
        var page=service.listOrders(8L,"PENDING",null);
        assertEquals("3",page.storeId());assertEquals(20,page.orders().size());assertEquals("23",page.orders().get(0).orderId());assertEquals("4",page.nextCursor());
        assertEquals(3,service.listOrders(8L,"PENDING",4L).orders().size());assertEquals(1,service.listOrders(8L,"FULFILLED",null).orders().size());
        // 一页只装配这一页的原订单，不按整店拉取；余量仍来自同一工作项事实。
        verify(pickup,atLeastOnce()).listPickupRequestsForOrders(eq(3L),anyList());
        actor.setStatus(2);assertThrows(IllegalArgumentException.class,()->service.listOrders(8L,"PENDING",null));
    }
    @Test void listPreservesExactIdsTimesAndRejectsDeletedOrMismatchedOrderStore() {
        task(Long.MAX_VALUE,3,"PENDING");task(1,3,"PENDING");task(2,3,"PENDING");
        jdbc.update("UPDATE `order` SET deleted=1 WHERE id=1");
        jdbc.update("UPDATE `order` SET store_id=4 WHERE id=2");
        jdbc.update("UPDATE order_pickup_request SET created_at='2026-11-01 10:20:30',fulfilled_at=NULL WHERE id=?",Long.MAX_VALUE);
        var rows=service.listOrders(8L,"PENDING",null).orders();assertEquals(1,rows.size());
        assertEquals("9223372036854775807",rows.get(0).orderId());
        var line=rows.get(0).lines().get(0);assertEquals("9223372036854775807",line.requestId());
        assertNull(line.productName());assertNull(line.unit());assertNull(line.remainingQuantity());
        assertFalse(line.canConfirm());assertEquals("原出库记录待核对，请联系店长",line.blockReason());
        assertEquals(1,rows.get(0).pendingCount());assertEquals(0,rows.get(0).fulfilledCount());
        actor.setStoreId(4L);assertTrue(service.listOrders(8L,"PENDING",null).orders().isEmpty());
        actor.setDeleted(1);assertThrows(IllegalArgumentException.class,()->service.listOrders(8L,"PENDING",null));
    }
    @Test void oneOrderKeepsPendingAndFulfilledItemsAndMasksCustomerWithoutAmounts() {
        jdbc.update("INSERT INTO `order` VALUES(50,'ORDER50',3,7,0)");
        jdbc.update("INSERT INTO customer VALUES(7,'张三')");
        jdbc.update("INSERT INTO order_pickup_request VALUES(60,50,3,99,'PENDING',CURRENT_TIMESTAMP,NULL)");
        jdbc.update("INSERT INTO order_pickup_request VALUES(61,50,3,98,'FULFILLED',CURRENT_TIMESTAMP,'2026-11-01 09:00:00')");
        when(pickup.listPickupRequestsForOrders(eq(3L),anyList())).thenReturn(List.of(ready()));
        var view=service.listOrders(8L,"PENDING",null).orders().get(0);
        // 待交列表里的订单必须同时报出整单已交项数，不能第一项成功就显示整单完成。
        assertEquals(1,view.pendingCount());assertEquals(1,view.fulfilledCount());assertEquals("张**",view.customerLabel());
        assertEquals("1.5",view.lines().get(0).remainingQuantity());assertEquals("公斤",view.lines().get(0).unit());assertTrue(view.lines().get(0).canConfirm());
        assertNull(view.lines().get(1).remainingQuantity());assertNull(view.lines().get(1).unit());
        assertFalse(view.lines().get(1).canConfirm());assertEquals("2026-11-01T09:00",view.lines().get(1).fulfilledAt());
    }
    PickupWorkItemVO ready(){
        var p=new PickupWorkItemVO();p.setStoreId(3L);p.setId(60L);p.setOrderId(50L);p.setOrderNo("ORDER50");p.setProductName("测试肥料");p.setStatus("PENDING");p.setCanFulfill(true);p.setConfirmationKey("a".repeat(64));
        p.setBaseUnitName("公斤");p.setRemainingBaseQuantity(new BigDecimal("1.5"));
        var zero=line(1,"0");var remaining=line(2,"1.5");p.setAllocations(List.of(zero,remaining));return p;
    }
    PickupWorkItemVO.AllocationVO line(long id,String quantity){
        var a=new PickupWorkItemVO.AllocationVO();a.setId(id);a.setBatchId(101L);a.setInternalReceiptBatchNo("BATCH101");a.setLocationId(12L);a.setLocationName("后仓");a.setBaseUnitName("公斤");a.setRemainingBaseQuantity(new BigDecimal(quantity));return a;
    }
    @Test void previewUsesLockedOriginalEvidenceAndOmitsReturnedOrHistoricalQuantities() {
        var p=ready();when(pickup.previewPickupRequest(60L,8L)).thenReturn(p);
        var view=service.preview(60L,8L);assertTrue(view.canConfirm());assertEquals("2",view.allocations().get(0).id());assertEquals("1.5",view.allocations().get(0).quantity());
        p.setStatus("FULFILLED");p.setCanFulfill(false);p.setConfirmationKey(null);p.setFulfilledAt(java.time.LocalDateTime.of(2026,10,30,12,0));
        var done=service.preview(60L,8L);assertTrue(done.allocations().isEmpty());assertNull(done.confirmationKey());assertEquals("2026-10-30T12:00",done.fulfilledAt());
    }
    @Test void missingOriginalUnitOrCrossStoreIsNeverGuessedIntoReadyPickup() {
        var p=ready();when(pickup.previewPickupRequest(60L,8L)).thenReturn(p);
        p.getAllocations().get(1).setBaseUnitName(null);
        var blocked=service.preview(60L,8L);assertFalse(blocked.canConfirm());assertNotNull(blocked.blockReason());assertNull(blocked.confirmationKey());assertTrue(blocked.allocations().isEmpty());
        p.setStoreId(4L);assertThrows(IllegalArgumentException.class,()->service.preview(60L,8L));
        assertThrows(IllegalArgumentException.class,()->service.listOrders(8L,"ALL",null));assertThrows(IllegalArgumentException.class,()->service.listOrders(8L,"PENDING",0L));
    }
}
