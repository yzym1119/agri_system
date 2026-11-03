package com.agri_system.application.order.service;

import com.agri_system.domain.order.model.*;
import com.agri_system.domain.order.repository.*;
import com.agri_system.domain.store.model.Staff;
import com.agri_system.domain.store.repository.StaffRepository;
import com.agri_system.domain.after_sales.repository.AfterSalesRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AssignedDeliveryReceiptTest {
    OrderRepository orders;OrderDeliveryRepository deliveries;StaffRepository staff;AfterSalesRepository afterSales;
    ApplicationEventPublisher events;OrderApplicationService service;Order order;OrderDelivery delivery;DeliveryItemQuantity item;Staff actor;
    @BeforeEach void setup(){
        orders=mock(OrderRepository.class);deliveries=mock(OrderDeliveryRepository.class);staff=mock(StaffRepository.class);afterSales=mock(AfterSalesRepository.class);events=mock(ApplicationEventPublisher.class);
        service=mock(OrderApplicationService.class,CALLS_REAL_METHODS);
        for(var entry:Map.of("orderRepository",orders,"deliveryRepository",deliveries,"staffRepository",staff,"afterSalesRepository",afterSales,"eventPublisher",events).entrySet())ReflectionTestUtils.setField(service,entry.getKey(),entry.getValue());
        order=new Order();order.setId(42L);order.setStoreId(3L);order.setOrderType(1);order.setOrderStatus(5);order.setOrderNo("TEST42");
        order.setReceiverName("虚构收货人");order.setReceiverPhone("13800000000");order.setReceiverAddress("本地测试地址");
        delivery=new OrderDelivery();delivery.setId(50L);delivery.setOrderId(42L);delivery.setDeliveryStaffId(8L);delivery.setDeliveryType(2);delivery.setDeliveryStatus(3);
        item=new DeliveryItemQuantity();item.setOrderItemId(1L);item.setProductName("肥料");item.setQuantity(new BigDecimal("2"));item.setUnit("袋");
        actor=new Staff();actor.setId(8L);actor.setStatus(1);actor.setStoreId(3L);
        lenient().when(orders.findByIdForUpdate(42L)).thenReturn(Optional.of(order));lenient().when(deliveries.findByOrderId(42L)).thenReturn(Optional.of(delivery));
        lenient().when(staff.findIdentityById(8L)).thenReturn(Optional.of(actor));lenient().when(orders.hasRemainingDeliveryQuantity(42L)).thenReturn(true);
        lenient().when(orders.findRemainingDeliveryItems(42L)).thenReturn(List.of(item));
    }
    @Test void ownPreviewUsesOriginalOrderLockAndReturnsOnlyNeededExactGoods(){
        var p=OrderFulfillmentTestFixture.withFulfillment(service).previewAssignedReceipt(42L,8L);assertTrue(p.canConfirm());assertEquals("3",p.storeId());assertEquals("42",p.orderId());
        assertEquals("2",p.items().get(0).quantity());assertEquals("袋",p.items().get(0).unit());assertNotNull(p.confirmationKey());
        var sequence=inOrder(orders,deliveries,staff);sequence.verify(orders).findByIdForUpdate(42L);sequence.verify(deliveries).findByOrderId(42L);sequence.verify(staff).findIdentityById(8L);
        verify(orders,never()).save(any());verify(deliveries,never()).save(any());verifyNoInteractions(events);
    }
    @Test void differentDriverForeignStoreInactiveAndExpressAreRejectedBeforePrivateData(){
        delivery.setDeliveryStaffId(9L);assertThrows(IllegalArgumentException.class,()->OrderFulfillmentTestFixture.withFulfillment(service).previewAssignedReceipt(42L,8L));
        delivery.setDeliveryStaffId(8L);actor.setStoreId(4L);assertThrows(IllegalArgumentException.class,()->OrderFulfillmentTestFixture.withFulfillment(service).previewAssignedReceipt(42L,8L));
        actor.setStoreId(3L);actor.setStatus(2);assertThrows(IllegalArgumentException.class,()->OrderFulfillmentTestFixture.withFulfillment(service).previewAssignedReceipt(42L,8L));
        actor.setStatus(1);delivery.setDeliveryType(3);assertThrows(IllegalArgumentException.class,()->OrderFulfillmentTestFixture.withFulfillment(service).previewAssignedReceipt(42L,8L));
        verify(orders,never()).findRemainingDeliveryItems(any());verifyNoInteractions(events);
    }
    @Test void reassignmentIsCheckedAgainUnderOriginalLockBeforeAnyCompletion(){
        var old=OrderFulfillmentTestFixture.withFulfillment(service).previewAssignedReceipt(42L,8L);delivery.setDeliveryStaffId(9L);
        assertThrows(IllegalArgumentException.class,()->OrderFulfillmentTestFixture.withFulfillment(service).confirmAssignedReceipt(42L,old.confirmationKey(),8L));
        verify(deliveries,never()).save(any());verify(orders,never()).save(any());verifyNoInteractions(events);
    }
    @Test void explicitReassignmentUsesRevisionAndOldDriverImmediatelyLosesTask() {
        var next=new Staff();next.setId(9L);next.setStoreId(3L);next.setStatus(1);
        when(staff.findIdentityById(9L)).thenReturn(Optional.of(next));
        var logs=mock(com.agri_system.domain.system.log.repository.SysOperationLogRepository.class);
        ReflectionTestUtils.setField(service,"operationLogRepository",logs);
        var old=OrderFulfillmentTestFixture.withFulfillment(service).previewAssignedReceipt(42L,8L);
        OrderFulfillmentTestFixture.withFulfillment(service).reassignDelivery(42L,0L,9L,"原配送员临时请假",8L);
        assertEquals(9L,delivery.getDeliveryStaffId());assertEquals(1L,delivery.getAssignmentVersion());
        assertThrows(IllegalArgumentException.class,()->OrderFulfillmentTestFixture.withFulfillment(service).confirmAssignedReceipt(42L,old.confirmationKey(),8L));
        OrderFulfillmentTestFixture.withFulfillment(service).reassignDelivery(42L,1L,8L,"恢复原配送员负责",8L);
        assertEquals(2L,delivery.getAssignmentVersion());
        assertThrows(IllegalArgumentException.class,()->OrderFulfillmentTestFixture.withFulfillment(service).reassignDelivery(42L,0L,9L,"迟到旧请求",8L));
        assertThrows(IllegalArgumentException.class,()->OrderFulfillmentTestFixture.withFulfillment(service).confirmAssignedReceipt(42L,old.confirmationKey(),8L));
        verify(orders,never()).save(any());verifyNoInteractions(events);verify(logs,times(2)).save(any());
    }
    @Test void managementPreviewUsesCurrentOrderLockAndExactVersionWithoutWriting() {
        var p=OrderFulfillmentTestFixture.withFulfillment(service).previewDeliveryReassignment(42L,8L);
        assertEquals("42",p.orderId());assertEquals("8",p.currentStaffId());assertEquals("0",p.version());assertTrue(p.canReassign());
        verify(deliveries,never()).save(any());
        delivery.setDeliveryStatus(4);var done=OrderFulfillmentTestFixture.withFulfillment(service).previewDeliveryReassignment(42L,8L);assertFalse(done.canReassign());assertNotNull(done.blockReason());
        actor.setStoreId(4L);assertThrows(IllegalArgumentException.class,()->OrderFulfillmentTestFixture.withFulfillment(service).previewDeliveryReassignment(42L,8L));
    }
    @Test void reassignmentRejectsCompletedExpressAfterSalesForeignStaffAndMissingReason() {
        var target=new Staff();target.setId(9L);target.setStoreId(4L);target.setStatus(1);
        lenient().when(staff.findIdentityById(9L)).thenReturn(Optional.of(target));
        assertThrows(IllegalArgumentException.class,()->OrderFulfillmentTestFixture.withFulfillment(service).reassignDelivery(42L,0L,9L,"",8L));
        delivery.setDeliveryStatus(4);assertThrows(IllegalArgumentException.class,()->OrderFulfillmentTestFixture.withFulfillment(service).reassignDelivery(42L,0L,9L,"改派",8L));
        delivery.setDeliveryStatus(3);delivery.setDeliveryType(3);assertThrows(IllegalArgumentException.class,()->OrderFulfillmentTestFixture.withFulfillment(service).reassignDelivery(42L,0L,9L,"改派",8L));
        delivery.setDeliveryType(2);assertThrows(IllegalArgumentException.class,()->OrderFulfillmentTestFixture.withFulfillment(service).reassignDelivery(42L,0L,9L,"改派",8L));
        target.setStoreId(3L);var fact=new com.agri_system.domain.after_sales.model.AfterSales();fact.setStatus(10);when(afterSales.findByOrderId(42L)).thenReturn(List.of(fact));
        assertThrows(IllegalArgumentException.class,()->OrderFulfillmentTestFixture.withFulfillment(service).reassignDelivery(42L,0L,9L,"改派",8L));
        verify(deliveries,never()).save(any());verify(orders,never()).save(any());verifyNoInteractions(events);
    }
    @Test void laterReturnInvalidatesOldKeyAndFreshPreviewCanConfirmRemainingPartialOrder(){
        var old=OrderFulfillmentTestFixture.withFulfillment(service).previewAssignedReceipt(42L,8L);order.setOrderStatus(9);item.setQuantity(BigDecimal.ONE);
        assertThrows(IllegalArgumentException.class,()->OrderFulfillmentTestFixture.withFulfillment(service).confirmAssignedReceipt(42L,old.confirmationKey(),8L));
        var fresh=OrderFulfillmentTestFixture.withFulfillment(service).previewAssignedReceipt(42L,8L);assertEquals("1",fresh.items().get(0).quantity());
        var completed=OrderFulfillmentTestFixture.withFulfillment(service).confirmAssignedReceipt(42L,fresh.confirmationKey(),8L);assertEquals("COMPLETED",completed.status());assertEquals(9,order.getOrderStatus());assertEquals(4,delivery.getDeliveryStatus());
        verifyNoInteractions(events);
    }
    @Test void pendingAndRefundingAfterSalesPausePreviewAndRejectFinalAction(){
        var old=OrderFulfillmentTestFixture.withFulfillment(service).previewAssignedReceipt(42L,8L);
        for(int status:List.of(10,40)){
            var fact=new com.agri_system.domain.after_sales.model.AfterSales();fact.setStatus(status);when(afterSales.findByOrderId(42L)).thenReturn(List.of(fact));
            var blocked=OrderFulfillmentTestFixture.withFulfillment(service).previewAssignedReceipt(42L,8L);assertFalse(blocked.canConfirm());assertNull(blocked.confirmationKey());assertNotNull(blocked.blockReason());
            assertThrows(IllegalArgumentException.class,()->OrderFulfillmentTestFixture.withFulfillment(service).confirmAssignedReceipt(42L,old.confirmationKey(),8L));
        }
        verify(deliveries,never()).save(any());verifyNoInteractions(events);
    }
    @Test void fullReturnCancellationMissingEvidenceAndMissingAddressBlockDelivery(){
        var old=OrderFulfillmentTestFixture.withFulfillment(service).previewAssignedReceipt(42L,8L);
        for(int status:List.of(7,8)){order.setOrderStatus(status);assertFalse(OrderFulfillmentTestFixture.withFulfillment(service).previewAssignedReceipt(42L,8L).canConfirm());assertThrows(IllegalArgumentException.class,()->OrderFulfillmentTestFixture.withFulfillment(service).confirmAssignedReceipt(42L,old.confirmationKey(),8L));}
        order.setOrderStatus(5);when(orders.findRemainingDeliveryItems(42L)).thenReturn(List.of());assertFalse(OrderFulfillmentTestFixture.withFulfillment(service).previewAssignedReceipt(42L,8L).canConfirm());
        when(orders.findRemainingDeliveryItems(42L)).thenReturn(List.of(item));order.setReceiverAddress(null);assertFalse(OrderFulfillmentTestFixture.withFulfillment(service).previewAssignedReceipt(42L,8L).canConfirm());
        assertThrows(IllegalArgumentException.class,()->OrderFulfillmentTestFixture.withFulfillment(service).confirmAssignedReceipt(42L,old.confirmationKey(),8L));verifyNoInteractions(events);
    }
    @Test void completionReplayDoesNotRebuildHistoricalDeliveredQuantityFromLaterReturns(){
        var p=OrderFulfillmentTestFixture.withFulfillment(service).previewAssignedReceipt(42L,8L);OrderFulfillmentTestFixture.withFulfillment(service).confirmAssignedReceipt(42L,p.confirmationKey(),8L);order.setOrderStatus(8);clearInvocations(orders,deliveries);
        var history=OrderFulfillmentTestFixture.withFulfillment(service).previewAssignedReceipt(42L,8L);assertEquals("COMPLETED",history.status());assertTrue(history.items().isEmpty());assertNull(history.receiverPhone());assertNull(history.receiverAddress());
        OrderFulfillmentTestFixture.withFulfillment(service).confirmAssignedReceipt(42L,p.confirmationKey(),8L);verify(orders,never()).findRemainingDeliveryItems(any());verify(deliveries,never()).save(any());verifyNoInteractions(events);
    }
    @Test void onlineCompletionKeepsOriginalFirstSettlementEventAndNeverPublishesItTwice(){
        order.setOrderType(3);var p=OrderFulfillmentTestFixture.withFulfillment(service).previewAssignedReceipt(42L,8L);OrderFulfillmentTestFixture.withFulfillment(service).confirmAssignedReceipt(42L,p.confirmationKey(),8L);OrderFulfillmentTestFixture.withFulfillment(service).confirmAssignedReceipt(42L,p.confirmationKey(),8L);
        verify(events,times(1)).publishEvent(any(com.agri_system.application.performance.event.OrderFinishedEvent.class));
    }
    @Test void mobileConfirmationAlwaysRequiresNonBlankOriginalPreviewKey(){
        for(String key:List.of(""," "))assertThrows(IllegalArgumentException.class,()->OrderFulfillmentTestFixture.withFulfillment(service).confirmAssignedReceipt(42L,key,8L));
        assertThrows(IllegalArgumentException.class,()->OrderFulfillmentTestFixture.withFulfillment(service).confirmAssignedReceipt(42L,null,8L));
    }
}
