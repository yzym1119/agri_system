package com.agri_system.application.order.service;

import com.agri_system.application.order.dto.command.PickupFulfillmentCommand;
import com.agri_system.domain.after_sales.repository.AfterSalesRepository;
import com.agri_system.domain.inventory.repository.InventoryLocationRepository;
import com.agri_system.domain.inventory.repository.InventoryReceiptBatchRepository;
import com.agri_system.domain.order.model.*;
import com.agri_system.domain.order.repository.*;
import com.agri_system.domain.product.repository.ProductRepository;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 实际SQL证据回归：早晚两批已出库，退回早批后只交晚批，不写第二次钱货。 */
class PickupRemainingEvidenceTest {
    private JdbcTemplate jdbc;
    private PickupFulfillmentService service;
    private OrderPickupRequest request;
    private Order order;
    private OrderItemBatchAllocation early;
    private OrderItemBatchAllocation late;
    private OrderRepository orders;
    private OrderPickupRequestRepository requests;
    private OrderItemBatchAllocationRepository allocations;
    private AfterSalesRepository afterSales;

    @BeforeEach
    void setUp() {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:pickup_" + java.util.UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE inventory_movement(id BIGINT PRIMARY KEY,store_id BIGINT,product_id BIGINT,batch_id BIGINT,from_location_id BIGINT,base_quantity DECIMAL(20,4),movement_type VARCHAR(20),reference_type VARCHAR(30),reference_id BIGINT)");
        jdbc.execute("CREATE TABLE after_sales(id BIGINT PRIMARY KEY,order_id BIGINT,status INT)");
        orders = mock(OrderRepository.class); requests = mock(OrderPickupRequestRepository.class);
        allocations = mock(OrderItemBatchAllocationRepository.class); afterSales = mock(AfterSalesRepository.class);
        var staff=mock(com.agri_system.domain.store.repository.StaffRepository.class);var actor=new com.agri_system.domain.store.model.Staff();actor.setId(7L);actor.setStoreId(3L);actor.setStatus(1);
        lenient().when(staff.findIdentityById(7L)).thenReturn(Optional.of(actor));
        var colleague = new com.agri_system.domain.store.model.Staff();
        colleague.setId(8L); colleague.setStoreId(3L); colleague.setStatus(1);
        lenient().when(staff.findIdentityById(8L)).thenReturn(Optional.of(colleague));
        service = new PickupFulfillmentService(allocations, requests, mock(InventoryLocationRepository.class),
                mock(InventoryReceiptBatchRepository.class), orders, mock(OrderItemRepository.class),
                mock(ProductRepository.class), afterSales, jdbc,staff);
        request = new OrderPickupRequest(); request.setId(60L);request.setOrderId(50L);request.setOrderItemId(90L);
        request.setStoreId(3L);request.setProductId(99L);request.setRequestedBaseQuantity(new BigDecimal("2"));request.setStatus("PENDING");
        order = new Order();order.setId(50L);order.setStoreId(3L);order.setOrderStatus(9);
        OrderItem item = new OrderItem();item.setId(90L);item.setOrderId(50L);item.setProductId(99L);
        // 两种入口供修复前和修复后的同一回归调用，测试不依赖读取实现。
        lenient().when(orders.findById(50L)).thenReturn(Optional.of(order));
        lenient().when(orders.findByIdForUpdate(50L)).thenReturn(Optional.of(order));
        when(requests.findByIdForUpdate(60L)).thenReturn(Optional.of(request));
        lenient().when(requests.findById(60L)).thenReturn(Optional.of(request));
        org.springframework.test.util.ReflectionTestUtils.setField(service, "itemRepository", itemRepository(item));
        early = allocation(1L, 101L, 1001L);late = allocation(2L, 102L, 1002L);
        when(allocations.findByOrderItemId(90L)).thenReturn(List.of(early,late));
        sale(1001L,101L);sale(1002L,102L);
    }
    private OrderItemRepository itemRepository(OrderItem item) {
        OrderItemRepository repository=mock(OrderItemRepository.class);
        when(repository.findByIdForUpdate(90L)).thenReturn(Optional.of(item));return repository;
    }
    private OrderItemBatchAllocation allocation(Long id, Long batch, Long movement) {
        OrderItemBatchAllocation a=new OrderItemBatchAllocation();a.setId(id);a.setBatchId(batch);a.setSourceMovementId(movement);
        a.setStoreId(3L);a.setProductId(99L);a.setLocationId(12L);a.setOrderItemId(90L);a.setPickupRequestId(60L);
        a.setAllocatedBaseQuantity(BigDecimal.ONE);a.setEvidenceLevel("RULE_ALLOCATED");return a;
    }
    private void sale(Long id, Long batch) { jdbc.update("INSERT INTO inventory_movement VALUES(?,3,99,?,12,1,'SALE','ORDER',50)",id,batch); }
    private void returned(Long id, Long allocation, Long batch, Long store, String quantity) {
        jdbc.update("INSERT INTO inventory_movement VALUES(?,?,99,?,NULL,?,'RETURN','SALE_ALLOCATION',?)",id,store,batch,new BigDecimal(quantity),allocation);
    }
    private PickupFulfillmentCommand confirm(Long... ids) {PickupFulfillmentCommand c=new PickupFulfillmentCommand();c.setAllocationIds(List.of(ids));c.setConfirmationKey(service.previewPickupRequest(60L,7L).getConfirmationKey());return c;}

    @Test
    void pendingHandoverMustRejectMissingQuantityPreviewBeforeMarkingAnyGoodsConfirmed() {
        var command=new PickupFulfillmentCommand();command.setAllocationIds(List.of(1L,2L));
        assertThatThrownBy(()->service.fulfill(60L,command,7L)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("核对");
        assertThat(request.getStatus()).isEqualTo("PENDING");verify(allocations,never()).save(any());
    }

    @Test void sameAllocationIdsWithReducedQuantityRejectOldPreviewAndFreshPreviewCanComplete() {
        var before=confirm(1L,2L);returned(2001L,1L,101L,3L,"0.5");
        assertThatThrownBy(()->service.fulfill(60L,before,7L)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("数量");
        assertThat(request.getStatus()).isEqualTo("PENDING");verify(allocations,never()).save(any());
        var fresh=confirm(1L,2L);assertThat(fresh.getConfirmationKey()).isNotEqualTo(before.getConfirmationKey());
        assertThat(service.fulfill(60L,fresh,7L)).containsExactly(early,late);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inventory_movement",Integer.class)).isEqualTo(3);
    }

    @Test
    void returnsEarlyBatchAndHandsOverOnlyLateBatchWithoutRewritingOriginalQuantity() {
        returned(2001L,1L,101L,3L,"1");
        assertThat(service.fulfill(60L,confirm(2L),7L)).containsExactly(late);
        assertThat(early.getEvidenceLevel()).isEqualTo("RULE_ALLOCATED");
        assertThat(late.getEvidenceLevel()).isEqualTo("FULFILLMENT_CONFIRMED");
        assertThat(request.getRequestedBaseQuantity()).isEqualByComparingTo("2");
        assertThat(early.getAllocatedBaseQuantity()).isEqualByComparingTo("1");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inventory_movement",Integer.class)).isEqualTo(3);
        assertThat(request.getStatus()).isEqualTo("FULFILLED");
    }

    @Test
    void fullReturnLeavesPendingRequestWithoutInventingHandover() {
        returned(2001L,1L,101L,3L,"1");returned(2002L,2L,102L,3L,"1");
        assertThatThrownBy(()->service.fulfill(60L,confirm(1L,2L),7L)).isInstanceOf(IllegalStateException.class);
        assertThat(request.getStatus()).isEqualTo("PENDING");assertThat(request.getFulfilledAt()).isNull();
        verify(allocations,never()).save(any());
    }

    @Test
    void positiveReturnOnlyCountsWhenSameAllocationStoreProductAndBatch() {
        returned(2001L,1L,101L,3L,"-1");returned(2002L,1L,102L,3L,"1");returned(2003L,1L,101L,4L,"1");
        assertThat(service.fulfill(60L,confirm(1L,2L),7L)).containsExactly(early,late);
    }

    @Test
    void missingOrMismatchedActualSaleFailsEvenWhenAllocationHasMovementId() {
        order.setOrderStatus(6);
        jdbc.update("UPDATE inventory_movement SET reference_id=999 WHERE id=1001");
        assertThatThrownBy(()->service.fulfill(60L,confirm(1L,2L),7L)).isInstanceOf(IllegalStateException.class).hasMessageContaining("出库");
        assertThat(request.getStatus()).isEqualTo("PENDING");
    }

    @Test
    void appliedAndAwaitingRefundPauseButFinishedAfterSalesAllowsRemaining() {
        for(int status:List.of(10,40)) {
            com.agri_system.domain.after_sales.model.AfterSales a=new com.agri_system.domain.after_sales.model.AfterSales();a.setStatus(status);
            when(afterSales.findByOrderId(50L)).thenReturn(List.of(a));
            assertThatThrownBy(()->service.fulfill(60L,confirm(1L,2L),7L)).isInstanceOf(IllegalStateException.class).hasMessageContaining("售后");
        }
        com.agri_system.domain.after_sales.model.AfterSales a=new com.agri_system.domain.after_sales.model.AfterSales();a.setStatus(50);
        when(afterSales.findByOrderId(50L)).thenReturn(List.of(a));
        assertThat(service.fulfill(60L,confirm(1L,2L),7L)).hasSize(2);
    }

    @Test
    void parentOrderLockPrecedesRequestAndItemLocksAndUsesReadCommitted() throws Exception {
        order.setOrderStatus(6);
        service.fulfill(60L,confirm(1L,2L),7L);
        org.mockito.InOrder locks=inOrder(orders,requests);
        locks.verify(orders).findByIdForUpdate(50L);
        locks.verify(requests).findByIdForUpdate(60L);
        var transaction=PickupFulfillmentService.class.getMethod("fulfill",Long.class,PickupFulfillmentCommand.class,Long.class)
                .getAnnotation(org.springframework.transaction.annotation.Transactional.class);
        assertThat(transaction.isolation()).isEqualTo(org.springframework.transaction.annotation.Isolation.READ_COMMITTED);
    }

    @Test
    void confirmedReplayReturnsOriginalResultWithoutChangingOperatorTimeOrEvidence() {
        order.setOrderStatus(6);
        service.fulfill(60L,confirm(1L,2L),7L);
        java.time.LocalDateTime at=request.getFulfilledAt();clearInvocations(allocations,requests);
        assertThat(service.fulfill(60L,confirm(1L,2L),8L)).containsExactly(early,late);
        assertThat(request.getFulfilledAt()).isEqualTo(at);assertThat(request.getFulfilledBy()).isEqualTo(7L);
        verify(allocations,never()).save(any());verify(requests,never()).save(any());
    }
}
