package com.agri_system.application.order.service;

import com.agri_system.application.credit.service.CreditApplicationService;
import com.agri_system.application.inventory.service.InventoryApplicationService;
import com.agri_system.application.order.dto.command.OrderItemDTO;
import com.agri_system.application.order.dto.command.POSOrderCreateCommand;
import com.agri_system.application.order.dto.command.PickupFulfillmentCommand;
import com.agri_system.application.order.dto.vo.PickupWorkItemVO;
import com.agri_system.application.product.service.ProductPricingService;
import com.agri_system.application.product.service.ProductUnitService;
import com.agri_system.application.product.service.UnitConversionSnapshot;
import com.agri_system.application.wallet.service.WalletApplicationService;
import com.agri_system.domain.after_sales.repository.AfterSalesItemRepository;
import com.agri_system.domain.after_sales.repository.AfterSalesRepository;
import com.agri_system.domain.customer.repository.CustomerRepository;
import com.agri_system.domain.inventory.model.InventoryBatchBalance;
import com.agri_system.domain.inventory.model.InventoryLocation;
import com.agri_system.domain.inventory.model.InventoryMovement;
import com.agri_system.domain.inventory.model.InventoryReceiptBatch;
import com.agri_system.domain.inventory.repository.InventoryBatchBalanceRepository;
import com.agri_system.domain.inventory.repository.InventoryLocationRepository;
import com.agri_system.domain.inventory.repository.InventoryReceiptBatchRepository;
import com.agri_system.domain.inventory.service.BatchBalanceService;
import com.agri_system.domain.order.model.Order;
import com.agri_system.domain.order.model.OrderItem;
import com.agri_system.domain.order.model.OrderItemBatchAllocation;
import com.agri_system.domain.order.model.OrderPickupRequest;
import com.agri_system.domain.order.repository.OrderDeliveryRepository;
import com.agri_system.domain.order.repository.OrderItemBatchAllocationRepository;
import com.agri_system.domain.order.repository.OrderItemRepository;
import com.agri_system.domain.order.repository.OrderPaymentIntentRepository;
import com.agri_system.domain.order.repository.OrderPaymentRepository;
import com.agri_system.domain.order.repository.OrderPickupRequestRepository;
import com.agri_system.domain.order.repository.OrderRepository;
import com.agri_system.domain.order.service.OrderDomainService;
import com.agri_system.domain.product.model.Product;
import com.agri_system.domain.product.repository.ProductRepository;
import com.agri_system.domain.store.model.Staff;
import com.agri_system.domain.store.repository.StaffRepository;
import com.agri_system.domain.store.repository.StoreRepository;
import com.agri_system.domain.wallet.repository.WalletAuthCodeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static com.agri_system.domain.order.enums.BatchEvidenceLevel.FULFILLMENT_CONFIRMED;
import static com.agri_system.domain.order.enums.BatchEvidenceLevel.RULE_ALLOCATED;
import static com.agri_system.domain.order.enums.BatchEvidenceLevel.SCANNED;
import static com.agri_system.domain.order.enums.BatchEvidenceLevel.UNKNOWN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 统一库存下库房履约确认已有销售出库证据，并保留扫码事实，不重复扣减库存。
 * SKU 条码无法证明具体批次，跨门店、数量不匹配或缺失出库记录必须拒绝。
 */
@ExtendWith(MockitoExtension.class)
class PickupFulfillmentServiceTest {

    private static final Long STORE_ID = 3L;
    private static final Long STAFF_ID = 7L;
    private static final Long PRODUCT_ID = 99L;
    private static final Long ORDER_ID = 500L;
    private static final Long ITEM_ID = 900L;
    private static final Long FLOOR_LOCATION_ID = 11L;
    private static final Long BACKROOM_LOCATION_ID = 12L;
    private static final Long REQUEST_ID = 601L;
    private static final Long BATCH_ID = 61L;
    private static final Long MOVEMENT_ID = 7000L;
    private static final String INTERNAL_BATCH_NO = "INTERNAL-BATCH-20260701-1";
    private static final String SKU_ONLY_EAN = "6901234567890";

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderItemRepository itemRepository;
    @Mock
    private OrderPaymentRepository paymentRepository;
    @Mock
    private OrderPaymentIntentRepository paymentIntentRepository;
    @Mock
    private OrderDeliveryRepository deliveryRepository;
    @Mock
    private OrderDomainService orderDomainService;
    @Mock
    private OrderPaymentService paymentService;
    @Mock
    private InventoryApplicationService inventoryService;
    @Mock
    private WalletApplicationService walletService;
    @Mock
    private CreditApplicationService creditService;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private ProductPricingService productPricingService;
    @Mock
    private StaffRepository staffRepository;
    @Mock
    private StoreRepository storeRepository;
    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private AfterSalesRepository afterSalesRepository;
    @Mock
    private org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Mock
    private AfterSalesItemRepository afterSalesItemRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private WalletAuthCodeRepository walletAuthCodeRepository;
    @Mock
    private BatchBalanceService batchBalanceService;
    @Mock
    private ProductUnitService productUnitService;
    @Mock
    private InventoryLocationRepository locationRepository;
    @Mock
    private InventoryBatchBalanceRepository batchBalanceRepository;
    @Mock
    private InventoryReceiptBatchRepository receiptBatchRepository;
    @Mock
    private OrderCostAllocationService orderCostAllocationService;
    @Mock
    private ManualPriceAuditService manualPriceAuditService;

    private InMemoryAllocationRepository allocationRepository;
    private InMemoryPickupRequestRepository pickupRequestRepository;
    private PickupFulfillmentService fulfillmentService;
    private OrderApplicationService orderService;

    @BeforeEach
    void setUp() {
        allocationRepository = new InMemoryAllocationRepository();
        pickupRequestRepository = new InMemoryPickupRequestRepository();
        fulfillmentService = new PickupFulfillmentService(
                allocationRepository, pickupRequestRepository, locationRepository, receiptBatchRepository,
                orderRepository, itemRepository, productRepository, afterSalesRepository, jdbc,staffRepository);
        var actor=new Staff();actor.setId(STAFF_ID);actor.setStoreId(STORE_ID);actor.setStatus(1);
        org.mockito.Mockito.lenient().when(staffRepository.findIdentityById(STAFF_ID)).thenReturn(Optional.of(actor));
        org.mockito.Mockito.lenient().when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order()));
        org.mockito.Mockito.lenient().when(jdbc.queryForList(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(Object[].class))).thenAnswer(invocation -> saleMovementRows());
        orderService = new OrderApplicationService(
                orderRepository, itemRepository, paymentRepository, paymentIntentRepository, deliveryRepository,
                orderDomainService, paymentService, inventoryService, walletService, creditService,
                productRepository, productPricingService, org.mockito.Mockito.mock(com.agri_system.application.productpackage.service.PackagePricingService.class), staffRepository, storeRepository, customerRepository,
                afterSalesRepository, afterSalesItemRepository, eventPublisher, walletAuthCodeRepository,
                fulfillmentService, manualPriceAuditService, org.mockito.Mockito.mock(com.agri_system.domain.system.log.repository.SysOperationLogRepository.class));
    }

    @Test
    void partReturnedTwoBatchOrderHandsOverOnlyRemainingBatchAndKeepsOriginalQuantities() {
        Long requestId = prepareMultiBatchRequest();
        List<OrderItemBatchAllocation> allocations = allocationRepository.findByOrderItemId(ITEM_ID);
        allocations.forEach(a -> a.setAllocatedBaseQuantity(BigDecimal.ONE));
        pickupRequestRepository.findById(requestId).orElseThrow().setRequestedBaseQuantity(new BigDecimal("2"));
        Order partial = order(); partial.setOrderStatus(9);
        when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(partial));
        com.agri_system.domain.after_sales.model.AfterSales finished = new com.agri_system.domain.after_sales.model.AfterSales();
        finished.setStatus(50);
        org.mockito.Mockito.lenient().when(afterSalesRepository.findByOrderId(ORDER_ID)).thenReturn(List.of(finished));
        org.mockito.Mockito.lenient().when(jdbc.queryForList(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(Object[].class))).thenReturn(List.of(
                saleMovement(allocations.get(0)), saleMovement(allocations.get(1)),
                returnMovement(allocations.get(0), BigDecimal.ONE)));
        List<OrderItemBatchAllocation> result = fulfillmentService.fulfill(requestId,
                confirmedAllocations(List.of(allocations.get(1).getId())), STAFF_ID);
        assertThat(result).extracting(OrderItemBatchAllocation::getId).containsExactly(allocations.get(1).getId());
        assertThat(allocations.get(0).getEvidenceLevel()).isEqualTo("RULE_ALLOCATED");
        assertThat(allocations.get(1).getEvidenceLevel()).isEqualTo("FULFILLMENT_CONFIRMED");
        assertThat(allocations).allMatch(a -> a.getAllocatedBaseQuantity().compareTo(BigDecimal.ONE) == 0);
        assertThat(pickupRequestRepository.findById(requestId).orElseThrow().getRequestedBaseQuantity()).isEqualByComparingTo("2");
        assertThat(pickupRequestRepository.findById(requestId).orElseThrow().getStatus()).isEqualTo("FULFILLED");
        verifyNoInteractions(batchBalanceService, inventoryService, paymentService, eventPublisher);
    }

    @Test
    void confirmedPickupPreservesActualSaleMovementAndDoesNotDeductAgain() {
        Long requestId = prepareSoldRequest();
        List<OrderItemBatchAllocation> result = fulfillmentService.fulfill(requestId,
                confirmedBatch(BATCH_ID, BACKROOM_LOCATION_ID), STAFF_ID);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getEvidenceLevel()).isEqualTo("FULFILLMENT_CONFIRMED");
        assertThat(result.get(0).getSourceMovementId()).isEqualTo(MOVEMENT_ID);
        assertThat(result.get(0).getAllocatedBaseQuantity()).isEqualByComparingTo("5");
        assertThat(result.get(0).getPickupRequestId()).isEqualTo(requestId);
        OrderPickupRequest request = pickupRequestRepository.findById(requestId).orElseThrow();
        assertThat(request.getStatus()).isEqualTo("FULFILLED");
        assertThat(request.getFulfilledBy()).isEqualTo(STAFF_ID);
        assertThat(request.getFulfilledAt()).isNotNull();
        verifyNoInteractions(batchBalanceService, orderCostAllocationService);
    }

    @Test
    void scannedPickupVerifiesAlreadySoldBatchWithoutAnotherStockMovement() {
        Long requestId = prepareSoldRequest();
        when(receiptBatchRepository.findByInternalReceiptBatchNo(INTERNAL_BATCH_NO))
                .thenReturn(Optional.of(receiptBatch(BATCH_ID, INTERNAL_BATCH_NO, "40")));
        List<OrderItemBatchAllocation> result = fulfillmentService.fulfill(requestId, scannedBatch(INTERNAL_BATCH_NO), STAFF_ID);
        assertThat(result.get(0).getEvidenceLevel()).isEqualTo("SCANNED");
        assertThat(result.get(0).getSourceMovementId()).isEqualTo(MOVEMENT_ID);
        verifyNoInteractions(batchBalanceService, orderCostAllocationService);
    }

    @Test
    void skuOnlyBarcodeCannotConfirmBatch() {
        Long requestId = prepareSoldRequest();
        assertThatThrownBy(() -> fulfillmentService.fulfill(requestId, scannedBatch(SKU_ONLY_EAN), STAFF_ID))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("不能解析为内部批次标签");
        assertPending(requestId);
    }

    @Test
    void wrongBatchCannotReplaceActualSaleBatch() {
        Long requestId = prepareSoldRequest();
        assertThatThrownBy(() -> fulfillmentService.fulfill(requestId, confirmedBatch(62L, BACKROOM_LOCATION_ID), STAFF_ID))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("与已出库记录不一致");
        assertPending(requestId);
    }

    @Test
    void wrongLocationCannotConfirmPickup() {
        Long requestId = prepareSoldRequest();
        assertThatThrownBy(() -> fulfillmentService.fulfill(requestId, confirmedBatch(BATCH_ID, FLOOR_LOCATION_ID), STAFF_ID))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("与已出库记录不一致");
        assertPending(requestId);
    }

    @Test
    void scannedDifferentBatchCannotConfirmPickup() {
        Long requestId = prepareSoldRequest();
        when(receiptBatchRepository.findByInternalReceiptBatchNo(INTERNAL_BATCH_NO))
                .thenReturn(Optional.of(receiptBatch(62L, INTERNAL_BATCH_NO, "40")));
        assertThatThrownBy(() -> fulfillmentService.fulfill(requestId, scannedBatch(INTERNAL_BATCH_NO), STAFF_ID))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("与已出库记录不一致");
        assertPending(requestId);
    }

    @Test
    void missingBatchCannotConfirmPickup() {
        Long requestId = prepareSoldRequest();
        assertThatThrownBy(() -> fulfillmentService.fulfill(requestId, new PickupFulfillmentCommand(), STAFF_ID))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("必须核对出库批次");
        assertPending(requestId);
    }

    @Test
    void nullCommandCannotConfirmPickup() {
        Long requestId = prepareSoldRequest();
        assertThatThrownBy(() -> fulfillmentService.fulfill(requestId, null, STAFF_ID))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("取货确认不能为空");
        assertPending(requestId);
    }

    @Test
    void noSaleAllocationCannotConfirmPickup() {
        Long requestId = seedPendingRequest(BACKROOM_LOCATION_ID);
        when(itemRepository.findByIdForUpdate(ITEM_ID)).thenReturn(Optional.of(orderItem()));
        assertThatThrownBy(() -> fulfillmentService.fulfill(requestId, confirmedBatch(BATCH_ID, BACKROOM_LOCATION_ID), STAFF_ID))
                .isInstanceOf(IllegalStateException.class).hasMessage("订单缺少实际出库记录");
        assertPending(requestId);
    }

    @Test
    void allocationWithoutSaleMovementCannotConfirmPickup() {
        Long requestId = seedPendingRequest(BACKROOM_LOCATION_ID);
        when(itemRepository.findByIdForUpdate(ITEM_ID)).thenReturn(Optional.of(orderItem()));
        seedAllocation("RULE_ALLOCATED");
        assertThatThrownBy(() -> fulfillmentService.fulfill(requestId, confirmedBatch(BATCH_ID, BACKROOM_LOCATION_ID), STAFF_ID))
                .isInstanceOf(IllegalStateException.class).hasMessage("订单缺少实际出库记录");
        assertPending(requestId);
    }

    @Test
    void completedRequestReplayReturnsOriginalConfirmation() {
        Long requestId = prepareSoldRequest();
        fulfillmentService.fulfill(requestId, confirmedBatch(BATCH_ID, BACKROOM_LOCATION_ID), STAFF_ID);
        java.time.LocalDateTime at = pickupRequestRepository.findById(requestId).orElseThrow().getFulfilledAt();
        assertThat(fulfillmentService.fulfill(requestId, confirmedBatch(BATCH_ID, BACKROOM_LOCATION_ID), STAFF_ID)).hasSize(1);
        assertThat(pickupRequestRepository.findById(requestId).orElseThrow().getFulfilledAt()).isEqualTo(at);
        verifyNoInteractions(batchBalanceService);
    }

    @Test
    void createRequestRequiresActualSaleAllocation() {
        when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order()));
        when(itemRepository.findByIdForUpdate(ITEM_ID)).thenReturn(Optional.of(orderItem()));
        assertThatThrownBy(() -> fulfillmentService.createRequest(ORDER_ID, ITEM_ID, BACKROOM_LOCATION_ID))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("尚未实际出库");
        assertThat(pickupRequestRepository.all()).isEmpty();
    }

    @Test
    void createRequestRejectsDuplicatePendingRequest() {
        seedPendingRequest(BACKROOM_LOCATION_ID);
        when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order()));
        when(itemRepository.findByIdForUpdate(ITEM_ID)).thenReturn(Optional.of(orderItem()));
        assertThatThrownBy(() -> fulfillmentService.createRequest(ORDER_ID, ITEM_ID, BACKROOM_LOCATION_ID))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("已有未完成");
        assertThat(pickupRequestRepository.all()).hasSize(1);
    }

    @Test
    void createRequestKeepsOriginalSoldQuantityWithoutCurrentUnitConversion() {
        when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order()));
        when(itemRepository.findByIdForUpdate(ITEM_ID)).thenReturn(Optional.of(orderItem()));
        when(locationRepository.findById(BACKROOM_LOCATION_ID)).thenReturn(Optional.of(location(BACKROOM_LOCATION_ID, "STORE_BACKROOM")));
        seedSoldAllocation();
        Long id = fulfillmentService.createRequest(ORDER_ID, ITEM_ID, BACKROOM_LOCATION_ID);
        OrderPickupRequest request = pickupRequestRepository.findById(id).orElseThrow();
        assertThat(request.getRequestedBaseQuantity()).isEqualByComparingTo("5");
        verifyNoInteractions(productUnitService);
        assertThat(request.getSourceLocationId()).isEqualTo(BACKROOM_LOCATION_ID);
        assertThat(request.getStatus()).isEqualTo("PENDING");
        verifyNoInteractions(batchBalanceService);
    }

    @Test
    void createRequestRejectsAllocationWithoutActualMovement() {
        when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order()));
        when(itemRepository.findByIdForUpdate(ITEM_ID)).thenReturn(Optional.of(orderItem()));
        seedAllocation("RULE_ALLOCATED");
        assertThatThrownBy(() -> fulfillmentService.createRequest(ORDER_ID, ITEM_ID, BACKROOM_LOCATION_ID))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("尚未实际出库");
        assertThat(pickupRequestRepository.all()).isEmpty();
        verifyNoInteractions(productUnitService, batchBalanceService);
    }

    @Test
    void createRequestSumsAllActualSaleAllocations() {
        when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order()));
        when(itemRepository.findByIdForUpdate(ITEM_ID)).thenReturn(Optional.of(orderItem()));
        when(locationRepository.findById(BACKROOM_LOCATION_ID)).thenReturn(Optional.of(location(BACKROOM_LOCATION_ID, "STORE_BACKROOM")));
        seedSoldAllocation();
        OrderItemBatchAllocation second = new OrderItemBatchAllocation();
        second.setOrderItemId(ITEM_ID); second.setProductId(PRODUCT_ID); second.setStoreId(STORE_ID);
        second.setBatchId(62L); second.setLocationId(BACKROOM_LOCATION_ID); second.setSourceMovementId(MOVEMENT_ID + 1);
        second.setAllocatedBaseQuantity(new BigDecimal("7"));
        allocationRepository.save(second);
        Long id = fulfillmentService.createRequest(ORDER_ID, ITEM_ID, BACKROOM_LOCATION_ID);
        assertThat(pickupRequestRepository.findById(id).orElseThrow().getRequestedBaseQuantity()).isEqualByComparingTo("12");
        verifyNoInteractions(productUnitService, batchBalanceService);
    }

    @Test
    void multiBatchPickupRequiresCompleteAllocationConfirmation() {
        Long requestId = prepareMultiBatchRequest();
        List<OrderItemBatchAllocation> assigned = allocationRepository.findByOrderItemId(ITEM_ID);
        List<OrderItemBatchAllocation> result = fulfillmentService.fulfill(requestId,
                confirmedAllocations(assigned.stream().map(OrderItemBatchAllocation::getId).toList()), STAFF_ID);
        assertThat(result).hasSize(2).allMatch(a -> "FULFILLMENT_CONFIRMED".equals(a.getEvidenceLevel()));
        assertThat(pickupRequestRepository.findById(requestId).orElseThrow().getStatus()).isEqualTo("FULFILLED");
        verifyNoInteractions(batchBalanceService, batchBalanceRepository, orderCostAllocationService);
    }

    @Test
    void incompleteDuplicateAndForeignAllocationsCannotConfirmPickup() {
        Long requestId = prepareMultiBatchRequest();
        List<Long> ids = allocationRepository.findByOrderItemId(ITEM_ID).stream().map(OrderItemBatchAllocation::getId).toList();
        for (List<Long> submitted : List.of(List.of(ids.get(0)), List.of(ids.get(0), ids.get(0)), List.of(ids.get(0), 99999L))) {
            assertThatThrownBy(() -> fulfillmentService.fulfill(requestId, confirmedAllocations(submitted), STAFF_ID))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("完整核对");
            assertPending(requestId);
        }
        assertThat(allocationRepository.findByOrderItemId(ITEM_ID)).allMatch(a -> "RULE_ALLOCATED".equals(a.getEvidenceLevel()));
    }

    @Test
    void allocationLinkedToAnotherRequestCannotBeConfirmed() {
        Long requestId = prepareMultiBatchRequest();
        List<OrderItemBatchAllocation> assigned = allocationRepository.findByOrderItemId(ITEM_ID);
        assigned.get(1).setPickupRequestId(999L);
        assertThatThrownBy(() -> fulfillmentService.fulfill(requestId,
                confirmedAllocations(assigned.stream().map(OrderItemBatchAllocation::getId).toList()), STAFF_ID))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("请求归属");
        assertPending(requestId);
    }

    @Test
    void cancelledAndFullyRefundedOrdersCannotBeHandedOver() {
        Long requestId = prepareSoldRequest();
        for (int status : List.of(7, 8)) {
            Order blocked = order(); blocked.setOrderStatus(status);
            when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(blocked));
            assertThatThrownBy(() -> fulfillmentService.fulfill(requestId, confirmedBatch(BATCH_ID, BACKROOM_LOCATION_ID), STAFF_ID))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("取消或退款");
            assertPending(requestId);
        }
    }

    @Test
    void zeroAmountReturnBlocksPickupEvenIfOrderStillFinished() {
        Long requestId = prepareSoldRequest();
        com.agri_system.domain.after_sales.model.AfterSales returned = new com.agri_system.domain.after_sales.model.AfterSales();
        returned.setOrderId(ORDER_ID);
        returned.setStatus(com.agri_system.domain.after_sales.model.AfterSales.STATUS_WAIT_REFUND);
        when(afterSalesRepository.findByOrderId(ORDER_ID)).thenReturn(List.of(returned));
        assertThatThrownBy(() -> fulfillmentService.fulfill(requestId, confirmedBatch(BATCH_ID, BACKROOM_LOCATION_ID), STAFF_ID))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("售后");
        assertPending(requestId);
    }

    @Test
    void rejectedAfterSalesDoesNotBlockLegitimatePickup() {
        Long requestId = prepareSoldRequest();
        com.agri_system.domain.after_sales.model.AfterSales rejected = new com.agri_system.domain.after_sales.model.AfterSales();
        rejected.setOrderId(ORDER_ID);
        rejected.setStatus(com.agri_system.domain.after_sales.model.AfterSales.STATUS_REJECTED);
        when(afterSalesRepository.findByOrderId(ORDER_ID)).thenReturn(List.of(rejected));
        fulfillmentService.fulfill(requestId, confirmedBatch(BATCH_ID, BACKROOM_LOCATION_ID), STAFF_ID);
        assertThat(pickupRequestRepository.findById(requestId).orElseThrow().getStatus()).isEqualTo("FULFILLED");
        verifyNoInteractions(batchBalanceService, batchBalanceRepository);
    }

    @Test
    void requestQuantityMismatchCannotConfirmPickup() {
        Long requestId = prepareMultiBatchRequest();
        pickupRequestRepository.findById(requestId).orElseThrow().setRequestedBaseQuantity(new BigDecimal("5"));
        assertThatThrownBy(() -> fulfillmentService.fulfill(requestId,
                confirmedAllocations(allocationRepository.findByOrderItemId(ITEM_ID).stream().map(OrderItemBatchAllocation::getId).toList()), STAFF_ID))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("数量与实际出库记录不一致");
        assertPending(requestId);
    }

    @Test
    void arbitraryRealInternalBatchLabelCanConfirmPickup() {
        Long requestId = prepareSoldRequest();
        when(receiptBatchRepository.findByInternalReceiptBatchNo("B-actual-uuid"))
                .thenReturn(Optional.of(receiptBatch(BATCH_ID, "B-actual-uuid", "40")));
        List<OrderItemBatchAllocation> result = fulfillmentService.fulfill(requestId, scannedBatch(" B-actual-uuid "), STAFF_ID);
        assertThat(result.get(0).getEvidenceLevel()).isEqualTo("SCANNED");
        verifyNoInteractions(batchBalanceService, batchBalanceRepository);
    }

    @Test
    void createRequestLinksEverySoldAllocationAndListShowsTheirActualLocations() {
        when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order()));
        when(itemRepository.findByIdForUpdate(ITEM_ID)).thenReturn(Optional.of(orderItem()));
        when(locationRepository.findById(BACKROOM_LOCATION_ID)).thenReturn(Optional.of(location(BACKROOM_LOCATION_ID, "STORE_BACKROOM")));
        seedSoldAllocation();
        when(receiptBatchRepository.findByStore(STORE_ID)).thenReturn(List.of(receiptBatch(BATCH_ID, "B-actual-uuid", "40")));
        Long requestId = fulfillmentService.createRequest(ORDER_ID, ITEM_ID, BACKROOM_LOCATION_ID);
        assertThat(allocationRepository.findByRequestId(requestId)).hasSize(1);
        when(orderRepository.findByIds(Collections.singleton(ORDER_ID))).thenReturn(List.of(order()));
        when(productRepository.findByIds(Collections.singleton(PRODUCT_ID))).thenReturn(List.of());
        InventoryLocation actual = location(BACKROOM_LOCATION_ID, "STORE_BACKROOM"); actual.setName("后仓实际货位");
        when(locationRepository.findByStoreId(STORE_ID)).thenReturn(List.of(actual));
        PickupWorkItemVO vo = fulfillmentService.listPickupRequests(STORE_ID, "PENDING").get(0);
        assertThat(vo.getAllocations()).hasSize(1);
        assertThat(vo.getAllocations().get(0).getInternalReceiptBatchNo()).isEqualTo("B-actual-uuid");
        assertThat(vo.getAllocations().get(0).getLocationName()).isEqualTo("后仓实际货位");
        assertThat(vo.getAllocations().get(0).getAllocatedBaseQuantity()).isEqualByComparingTo("5");
    }

    @Test
    void listShowsOriginalAndRemainingQuantityWithoutNPlusOneAfterSalesReads() {
        Long request = prepareMultiBatchRequest();
        List<OrderItemBatchAllocation> assigned = allocationRepository.findByOrderItemId(ITEM_ID);
        // 准备数据时的锁入口不属于本次只读列表。
        org.mockito.Mockito.lenient().when(itemRepository.findByIdForUpdate(ITEM_ID)).thenReturn(Optional.of(orderItem()));
        org.mockito.Mockito.lenient().when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order()));
        when(orderRepository.findByIds(Collections.singleton(ORDER_ID))).thenReturn(List.of(order()));
        when(jdbc.queryForList(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(Object[].class)))
                .thenReturn(List.of(saleMovement(assigned.get(0)), saleMovement(assigned.get(1)),
                        returnMovement(assigned.get(0), new BigDecimal("5"))));
        PickupWorkItemVO item = fulfillmentService.listPickupRequests(STORE_ID,"PENDING").get(0);
        assertThat(item.isCanFulfill()).isTrue();
        assertThat(item.getRequestedBaseQuantity()).isEqualByComparingTo("12");
        assertThat(item.getRemainingBaseQuantity()).isEqualByComparingTo("7");
        assertThat(item.getAllocations()).extracting(a -> a.getRemainingBaseQuantity().signum()).containsExactly(0,1);
        verifyNoInteractions(afterSalesRepository);
        pickupRequestRepository.findById(request).orElseThrow().setStatus("FULFILLED");
        PickupWorkItemVO history=fulfillmentService.listPickupRequests(STORE_ID,"FULFILLED").get(0);
        assertThat(history.getRemainingBaseQuantity()).isNull();assertThat(history.isCanFulfill()).isFalse();
        assertThat(history.getAllocations()).allMatch(a -> a.getRemainingBaseQuantity() == null);
    }

    private PickupFulfillmentCommand confirmedAllocations(List<Long> ids) {
        PickupFulfillmentCommand command = new PickupFulfillmentCommand();
        command.setAllocationIds(ids);
        return withKey(command);
    }

    private Long prepareMultiBatchRequest() {
        Long requestId = prepareSoldRequest();
        OrderItemBatchAllocation second = new OrderItemBatchAllocation();
        second.setOrderItemId(ITEM_ID); second.setProductId(PRODUCT_ID); second.setStoreId(STORE_ID);
        second.setBatchId(62L); second.setLocationId(FLOOR_LOCATION_ID); second.setSourceMovementId(MOVEMENT_ID + 1);
        second.setAllocatedBaseQuantity(new BigDecimal("7")); second.setEvidenceLevel("RULE_ALLOCATED");
        second.setPickupRequestId(requestId); allocationRepository.save(second);
        pickupRequestRepository.findById(requestId).orElseThrow().setRequestedBaseQuantity(new BigDecimal("12"));
        return requestId;
    }

    @Test
    void returnedSaleCannotBeHandedOverEvenWhenOrderAndAfterSalesReadShowOldState() {
        Long request = prepareSoldRequest();
        OrderItemBatchAllocation a = allocationRepository.findByOrderItemId(ITEM_ID).get(0);
        when(jdbc.queryForList(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(Object[].class)))
                .thenReturn(List.of(saleMovement(a), returnMovement(a, a.getAllocatedBaseQuantity())));
        assertThrows(IllegalStateException.class, () -> fulfillmentService.fulfill(request, confirmedBatch(BATCH_ID, BACKROOM_LOCATION_ID), STAFF_ID));
        assertPending(request);
    }

    private List<Map<String, Object>> saleMovementRows() {
        return allocationRepository.findByOrderItemId(ITEM_ID).stream().filter(a -> a.getSourceMovementId() != null)
                .map(this::saleMovement).toList();
    }

    private Map<String, Object> saleMovement(OrderItemBatchAllocation a) {
        return Map.of("id", a.getSourceMovementId(), "store_id", a.getStoreId(), "product_id", a.getProductId(),
                "batch_id", a.getBatchId(), "from_location_id", a.getLocationId(), "base_quantity", a.getAllocatedBaseQuantity(),
                "movement_type", "SALE", "reference_type", "ORDER", "reference_id", ORDER_ID);
    }

    private Map<String, Object> returnMovement(OrderItemBatchAllocation a, BigDecimal quantity) {
        return Map.of("id", 88888L, "store_id", a.getStoreId(), "product_id", a.getProductId(),
                "batch_id", a.getBatchId(), "base_quantity", quantity,
                "movement_type", "RETURN", "reference_type", "SALE_ALLOCATION", "reference_id", a.getId());
    }

    private Long prepareSoldRequest() {
        Long id = seedPendingRequest(BACKROOM_LOCATION_ID);
        org.mockito.Mockito.lenient().when(itemRepository.findByIdForUpdate(ITEM_ID)).thenReturn(Optional.of(orderItem()));
        org.mockito.Mockito.lenient().when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order()));
        seedSoldAllocation();
        allocationRepository.findByOrderItemId(ITEM_ID).get(0).setPickupRequestId(id);
        return id;
    }

    private void seedSoldAllocation() {
        seedAllocation("RULE_ALLOCATED");
        OrderItemBatchAllocation allocation = allocationRepository.findByOrderItemId(ITEM_ID).get(0);
        allocation.setBatchId(BATCH_ID);
        allocation.setLocationId(BACKROOM_LOCATION_ID);
        allocation.setSourceMovementId(MOVEMENT_ID);
    }

    private void assertPending(Long id) {
        assertThat(pickupRequestRepository.findById(id).orElseThrow().getStatus()).isEqualTo("PENDING");
        verifyNoInteractions(batchBalanceService);
    }

    @Test
    void listPickupRequestsDefaultsToPendingAndCarriesOrderAndProductLabels() {
        Long pendingId = seedPendingRequest(BACKROOM_LOCATION_ID);
        OrderPickupRequest fulfilledRequest = pendingRequest(BACKROOM_LOCATION_ID);
        fulfilledRequest.setStatus(OrderPickupRequest.Status.FULFILLED.name());
        Long fulfilledId = pickupRequestRepository.save(fulfilledRequest).getId();
        InventoryLocation backroom = location(BACKROOM_LOCATION_ID, InventoryLocation.LocationType.STORE_BACKROOM.name());
        backroom.setName("后仓01");
        Product product = new Product();
        product.setId(PRODUCT_ID);
        product.setCode("P-99");
        product.setName("复合肥");
        when(orderRepository.findByIds(Collections.singleton(ORDER_ID))).thenReturn(List.of(order()));
        when(productRepository.findByIds(Collections.singleton(PRODUCT_ID))).thenReturn(List.of(product));
        when(locationRepository.findByStoreId(STORE_ID)).thenReturn(List.of(backroom));

        List<PickupWorkItemVO> pendingItems = fulfillmentService.listPickupRequests(STORE_ID, null);

        assertThat(pendingItems).hasSize(1);
        PickupWorkItemVO item = pendingItems.get(0);
        assertThat(item.getId()).isEqualTo(pendingId);
        assertThat(item.getStatus()).isEqualTo(OrderPickupRequest.Status.PENDING.name());
        assertThat(item.getOrderId()).isEqualTo(ORDER_ID);
        assertThat(item.getOrderNo()).isEqualTo("SO202609120001");
        assertThat(item.getOrderItemId()).isEqualTo(ITEM_ID);
        assertThat(item.getProductId()).isEqualTo(PRODUCT_ID);
        assertThat(item.getProductCode()).isEqualTo("P-99");
        assertThat(item.getProductName()).isEqualTo("复合肥");
        assertThat(item.getRequestedBaseQuantity()).isEqualByComparingTo("5");
        assertThat(item.getSourceLocationId()).isEqualTo(BACKROOM_LOCATION_ID);
        assertThat(item.getSourceLocationName()).isEqualTo("后仓01");

        List<PickupWorkItemVO> fulfilledItems = fulfillmentService.listPickupRequests(STORE_ID, "FULFILLED");

        assertThat(fulfilledItems).hasSize(1);
        assertThat(fulfilledItems.get(0).getId()).isEqualTo(fulfilledId);
        assertThat(fulfilledItems.get(0).getStatus())
                .isEqualTo(OrderPickupRequest.Status.FULFILLED.name());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> fulfillmentService.listPickupRequests(STORE_ID, "DONE"));

        assertThat(ex.getMessage()).contains("状态");
    }

    private POSOrderCreateCommand commandForSkuOnly() {
        POSOrderCreateCommand command = new POSOrderCreateCommand();
        command.setStaffId(STAFF_ID);
        command.setPaymentType(1);
        OrderItemDTO item = new OrderItemDTO();
        item.setProductId(PRODUCT_ID);
        item.setQuantity(new BigDecimal("5"));
        item.setUnit("袋");
        item.setUnitPrice(new BigDecimal("100.00"));
        command.setItems(Collections.singletonList(item));
        return command;
    }

    private PickupFulfillmentCommand scannedBatch(String barcode) {
        PickupFulfillmentCommand command = new PickupFulfillmentCommand();
        command.setScannedCode(barcode);
        return withKey(command);
    }

    private PickupFulfillmentCommand confirmedBatch(Long batchId, Long locationId) {
        PickupFulfillmentCommand command = new PickupFulfillmentCommand();
        command.setBatchId(batchId);
        command.setLocationId(locationId);
        return withKey(command);
    }

    private PickupFulfillmentCommand withKey(PickupFulfillmentCommand command) {
        var pending=pickupRequestRepository.findByStoreIdAndStatus(STORE_ID,"PENDING");
        if(!pending.isEmpty())command.setConfirmationKey(fulfillmentService.previewPickupRequest(pending.get(0).getId(),STAFF_ID).getConfirmationKey());
        return command;
    }

    private void stubPosOrderThenSave() {
        Staff staff = new Staff();
        staff.setId(STAFF_ID);
        staff.setStoreId(STORE_ID);
        Product product = new Product();
        product.setId(PRODUCT_ID);
        product.setName("复合肥");
        product.setMainUnit("袋");
        product.setAvgCostPrice(new BigDecimal("40.00"));
        product.setAllowSpecialFund(0);

        when(staffRepository.findById(STAFF_ID)).thenReturn(Optional.of(staff));
        when(orderDomainService.generateOrderNo()).thenReturn("SO202609120001");
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(ORDER_ID);
            }
            return saved;
        });
        when(itemRepository.save(any(OrderItem.class))).thenAnswer(invocation -> {
            OrderItem saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(ITEM_ID);
            }
            allocationRepository.linkItemToOrder(saved.getId(), saved.getOrderId());
            return saved;
        });
        when(paymentRepository.findByOrderId(ORDER_ID)).thenReturn(Collections.emptyList());
    }

    private void stubUnitConversion(BigDecimal quantity) {
        when(productUnitService.toBaseQuantity(PRODUCT_ID, "袋", quantity))
                .thenReturn(new UnitConversionSnapshot("袋", quantity, BigDecimal.ONE, quantity));
    }

    private Long seedPendingRequest(Long sourceLocationId) {
        return pickupRequestRepository.save(pendingRequest(sourceLocationId)).getId();
    }

    private OrderPickupRequest pendingRequest(Long sourceLocationId) {
        OrderPickupRequest request = new OrderPickupRequest();
        request.setOrderId(ORDER_ID);
        request.setOrderItemId(ITEM_ID);
        request.setProductId(PRODUCT_ID);
        request.setStoreId(STORE_ID);
        request.setRequestedBaseQuantity(new BigDecimal("5"));
        request.setSourceLocationId(sourceLocationId);
        request.setStatus(OrderPickupRequest.Status.PENDING.name());
        return request;
    }

    private void seedAllocation(String evidenceLevel) {
        OrderItemBatchAllocation allocation = new OrderItemBatchAllocation();
        allocation.setOrderItemId(ITEM_ID);
        allocation.setProductId(PRODUCT_ID);
        allocation.setStoreId(STORE_ID);
        allocation.setAllocatedBaseQuantity(new BigDecimal("5"));
        allocation.setEvidenceLevel(evidenceLevel);
        allocationRepository.save(allocation);
    }

    private Order order() {
        Order order = new Order();
        order.setId(ORDER_ID);
        order.setOrderNo("SO202609120001");
        order.setStoreId(STORE_ID);
        order.setOrderStatus(6);
        return order;
    }

    private OrderItem orderItem() {
        OrderItem item = new OrderItem();
        item.setId(ITEM_ID);
        item.setOrderId(ORDER_ID);
        item.setProductId(PRODUCT_ID);
        item.setQuantity(new BigDecimal("5"));
        item.setUnit("袋");
        return item;
    }

    private InventoryLocation location(Long id, String locationType) {
        InventoryLocation location = new InventoryLocation();
        location.setId(id);
        location.setStoreId(STORE_ID);
        location.setLocationType(locationType);
        location.setStatus(1);
        return location;
    }

    private InventoryBatchBalance balance(Long batchId, Long locationId, String available) {
        InventoryBatchBalance balance = new InventoryBatchBalance();
        balance.setBatchId(batchId);
        balance.setProductId(PRODUCT_ID);
        balance.setStoreId(STORE_ID);
        balance.setLocationId(locationId);
        balance.setBaseQuantity(new BigDecimal(available));
        balance.setReservedBaseQuantity(BigDecimal.ZERO);
        return balance;
    }

    private InventoryReceiptBatch receiptBatch(Long id, String internalBatchNo, String baseUnitCost) {
        InventoryReceiptBatch batch = new InventoryReceiptBatch();
        batch.setId(id);
        batch.setStoreId(STORE_ID);
        batch.setProductId(PRODUCT_ID);
        batch.setInternalReceiptBatchNo(internalBatchNo);
        batch.setOriginalUnitId(1L);
        batch.setOriginalUnitName("袋");
        batch.setOriginalQuantity(new BigDecimal("10"));
        batch.setBaseQuantity(new BigDecimal("10"));
        batch.setBaseUnitCost(new BigDecimal(baseUnitCost));
        return batch;
    }

    private InventoryMovement movement(Long id) {
        InventoryMovement movement = new InventoryMovement();
        movement.setId(id);
        return movement;
    }

    /** 以 order_item JOIN 语义模拟 findByOrderId，其余方法按字段过滤。 */
    private static class InMemoryAllocationRepository implements OrderItemBatchAllocationRepository {
        private final AtomicLong sequence = new AtomicLong(1000L);
        private final List<OrderItemBatchAllocation> rows = new ArrayList<>();
        private final Map<Long, Long> itemToOrder = new HashMap<>();

        void linkItemToOrder(Long itemId, Long orderId) {
            itemToOrder.put(itemId, orderId);
        }

        @Override
        public OrderItemBatchAllocation save(OrderItemBatchAllocation allocation) {
            if (allocation.getId() == null) {
                allocation.setId(sequence.incrementAndGet());
            }
            rows.removeIf(row -> row.getId().equals(allocation.getId()));
            rows.add(allocation);
            return allocation;
        }

        @Override
        public List<OrderItemBatchAllocation> findByOrderId(Long orderId) {
            return rows.stream()
                    .filter(row -> orderId.equals(itemToOrder.get(row.getOrderItemId())))
                    .toList();
        }

        @Override
        public List<OrderItemBatchAllocation> findByRequestId(Long pickupRequestId) {
            return rows.stream()
                    .filter(row -> pickupRequestId.equals(row.getPickupRequestId()))
                    .toList();
        }

        @Override
        public List<OrderItemBatchAllocation> findByRequestIds(java.util.Collection<Long> requestIds) {
            return rows.stream().filter(row -> requestIds.contains(row.getPickupRequestId())).toList();
        }

        @Override
        public List<OrderItemBatchAllocation> findByOrderItemId(Long orderItemId) {
            return rows.stream()
                    .filter(row -> orderItemId.equals(row.getOrderItemId()))
                    .toList();
        }

        @Override
        public List<WindowMargin> sumWindowMarginByProduct(int days, Long storeId) {
            // 毛利窗口聚合与提货履约无关，测试替身返回空集
            return List.of();
        }
    }

    private static class InMemoryPickupRequestRepository implements OrderPickupRequestRepository {
        private final AtomicLong sequence = new AtomicLong(600L);
        private final List<OrderPickupRequest> rows = new ArrayList<>();

        @Override
        public OrderPickupRequest save(OrderPickupRequest request) {
            if (request.getId() == null) {
                request.setId(sequence.incrementAndGet());
            }
            rows.removeIf(row -> row.getId().equals(request.getId()));
            rows.add(request);
            return request;
        }

        @Override
        public Optional<OrderPickupRequest> findById(Long id) {
            return rows.stream().filter(row -> row.getId().equals(id)).findFirst();
        }

        @Override
        public Optional<OrderPickupRequest> findByIdForUpdate(Long id) {
            return findById(id);
        }

        @Override
        public Optional<OrderPickupRequest> findPendingByOrderItemId(Long orderItemId) {
            return rows.stream()
                    .filter(row -> orderItemId.equals(row.getOrderItemId()))
                    .filter(row -> OrderPickupRequest.Status.PENDING.name().equals(row.getStatus()))
                    .findFirst();
        }

        @Override
        public List<OrderPickupRequest> findByStoreIdAndStatus(Long storeId, String status) {
            return rows.stream()
                    .filter(row -> storeId.equals(row.getStoreId()))
                    .filter(row -> status.equals(row.getStatus()))
                    .toList();
        }

        List<OrderPickupRequest> all() {
            return rows;
        }
    }
}
