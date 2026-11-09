package com.agri_system.application.order.service;
import com.agri_system.application.order.dto.vo.PickupWorkItemVO;
import com.agri_system.domain.order.query.PickupTaskQuery;
import com.agri_system.domain.store.model.Staff;
import com.agri_system.domain.store.repository.StaffRepository;
import com.agri_system.domain.after_sales.repository.AfterSalesRepository;
import com.agri_system.domain.after_sales.model.AfterSales;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MobilePickupPreparationTest {
    StaffRepository staff=mock(StaffRepository.class);PickupTaskQuery query=mock(PickupTaskQuery.class);
    PickupFulfillmentService pickup=mock(PickupFulfillmentService.class);AfterSalesRepository history=mock(AfterSalesRepository.class);
    MobilePickupTaskService tasks=new MobilePickupTaskService(query,staff,pickup);
    MobilePickupPreparationService service=new MobilePickupPreparationService(query,staff,pickup,tasks,history);
    Staff actor;List<PickupTaskQuery.OrderLine> raw;List<PickupWorkItemVO> sources;
    @BeforeEach void setup(){actor=new Staff();actor.setId(8L);actor.setStoreId(3L);actor.setStatus(1);actor.setDeleted(0);when(staff.findIdentityById(8L)).thenReturn(Optional.of(actor));
        raw=new ArrayList<>();sources=new ArrayList<>();for(long id=1;id<=25;id++){raw.add(new PickupTaskQuery.OrderLine(id,50L,"O50","张三","肥料","PENDING",null,null));sources.add(source(id));}
        when(query.findOrderLinesForOrder(3L,50L)).thenReturn(raw);when(pickup.listPickupRequestsForOrders(3L,List.of(50L))).thenReturn(sources);
    }
    PickupWorkItemVO source(long id){var p=new PickupWorkItemVO();p.setId(id);p.setStoreId(3L);p.setOrderId(50L);p.setOrderNo("O50");p.setProductName("肥料");p.setProductCode("P1");p.setStatus("PENDING");p.setCanFulfill(true);p.setConfirmationKey("a".repeat(64));
        var a=new PickupWorkItemVO.AllocationVO();a.setId(id);a.setBatchId(101L);a.setInternalReceiptBatchNo("B101");a.setLocationId(12L);a.setLocationName("后仓");a.setBaseUnitName("袋");a.setRemainingBaseQuantity(BigDecimal.ONE);p.setAllocations(List.of(a));return p;}
    @Test void twentyFiveRequestsUseOriginalWorkItemsWithoutWriteTokenOrInventingCompletedQuantities(){
        sources.get(24).setStatus("FULFILLED");sources.get(24).setCanFulfill(false);
        var view=service.read(50L,8L);assertEquals(25,view.lines().size());assertEquals(24,view.pendingCount());assertEquals(1,view.fulfilledCount());assertEquals("张**",view.customerLabel());
        assertTrue(view.lines().get(0).canPrepare());assertEquals("1",view.lines().get(0).allocations().get(0).quantity());assertNotEquals("a".repeat(64),view.lines().get(0).revision());assertTrue(view.lines().get(0).revision().matches("[a-f0-9]{64}"));
        assertTrue(view.lines().get(24).allocations().isEmpty());assertNull(view.lines().get(24).revision());assertFalse(view.toString().contains("confirmationKey"));
    }
    @Test void revisionChangesForQuantityAndAfterSalesEvenWhenPausedCaseIsRejectedLater(){
        var original=service.read(50L,8L).lines().get(0).revision();assertEquals(original,service.read(50L,8L).lines().get(0).revision());
        sources.get(0).getAllocations().get(0).setRemainingBaseQuantity(new BigDecimal("2"));assertNotEquals(original,service.read(50L,8L).lines().get(0).revision());sources.get(0).getAllocations().get(0).setRemainingBaseQuantity(BigDecimal.ONE);
        var after=new AfterSales();after.setId(7L);after.setOrderId(50L);after.setStoreId(3L);after.setStatus(90);when(history.findByOrderId(50L)).thenReturn(List.of(after));assertNotEquals(original,service.read(50L,8L).lines().get(0).revision());
        sources.get(0).setCanFulfill(false);sources.get(0).setFulfillmentBlockReason("处理中售后");var blocked=service.read(50L,8L).lines().get(0);assertFalse(blocked.canPrepare());assertNull(blocked.revision());assertTrue(blocked.allocations().isEmpty());
    }
    @Test void rejectsForeignMissingOrChangedTaskSetAndInvalidActorBeforeReturningPartialPreparation(){
        assertThrows(IllegalArgumentException.class,()->service.read(51L,8L));
        sources.remove(24);assertThrows(IllegalStateException.class,()->service.read(50L,8L));sources.add(source(25));sources.get(0).setStoreId(4L);assertThrows(IllegalStateException.class,()->service.read(50L,8L));
        actor.setStatus(0);assertThrows(IllegalArgumentException.class,()->service.read(50L,8L));
    }
}
