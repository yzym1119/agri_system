package com.agri_system.application.order.service;

import com.agri_system.application.credit.service.CreditApplicationService;
import com.agri_system.application.inventory.service.InventoryApplicationService;
import com.agri_system.application.order.dto.command.OrderItemDTO;
import com.agri_system.application.order.dto.command.POSOrderCreateCommand;
import com.agri_system.application.order.dto.command.PickupFulfillmentCommand;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Task5 零售事实链：POS 销售批次证据与库房取货两阶段履约。
 *
 * <p>链路处于休眠状态（未激活 SKU 行为与现状保持一致），激活 SKU 的行为由本测试用
 * {@code isBatchLocationEnabled=true} 的桩验证。混批货架销售必须允许结账（UNKNOWN），
 * 后仓扫码取货必须产生 SCANNED 与真实批次成本；仅表示 SKU 的 EAN/GS1 条码绝不能产生 SCANNED。</p>
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

    private InMemoryAllocationRepository allocationRepository;
    private InMemoryPickupRequestRepository pickupRequestRepository;
    private PickupFulfillmentService fulfillmentService;
    private OrderApplicationService orderService;

    @BeforeEach
    void setUp() {
        allocationRepository = new InMemoryAllocationRepository();
        pickupRequestRepository = new InMemoryPickupRequestRepository();
        fulfillmentService = new PickupFulfillmentService(
                allocationRepository, pickupRequestRepository, batchBalanceService, productUnitService,
                locationRepository, batchBalanceRepository, receiptBatchRepository, orderRepository, itemRepository);
        orderService = new OrderApplicationService(
                orderRepository, itemRepository, paymentRepository, paymentIntentRepository, deliveryRepository,
                orderDomainService, paymentService, inventoryService, walletService, creditService,
                productRepository, productPricingService, staffRepository, storeRepository, customerRepository,
                afterSalesRepository, afterSalesItemRepository, eventPublisher, walletAuthCodeRepository,
                fulfillmentService);
    }

    @Test
    void directMixedShelfSaleCanRemainUnknownWithoutBlockingCheckout() {
        stubPosOrderThenSave();
        stubUnitConversion(new BigDecimal("5"));
        when(batchBalanceService.isBatchLocationEnabled(STORE_ID, PRODUCT_ID)).thenReturn(true);
        when(locationRepository.findEnabledByStoreAndType(STORE_ID, InventoryLocation.LocationType.SALES_FLOOR.name()))
                .thenReturn(Optional.of(location(FLOOR_LOCATION_ID, InventoryLocation.LocationType.SALES_FLOOR.name())));
        when(batchBalanceRepository.findByLocationAndProduct(FLOOR_LOCATION_ID, PRODUCT_ID))
                .thenReturn(List.of(
                        balance(61L, FLOOR_LOCATION_ID, "5"),
                        balance(62L, FLOOR_LOCATION_ID, "5")));

        Long orderId = orderService.createPOSOrder(commandForSkuOnly(), 7L);

        assertThat(orderId).isEqualTo(ORDER_ID);
        List<OrderItemBatchAllocation> allocations = allocationRepository.findByOrderId(orderId);
        assertThat(allocations).hasSize(1);
        assertThat(allocations.get(0).getEvidenceLevel()).isEqualTo(UNKNOWN.name());
        assertThat(allocations.get(0).getBatchId()).isNull();
        assertThat(allocations.get(0).getAllocatedBaseQuantity()).isEqualByComparingTo("5");
        assertThat(pickupRequestRepository.all()).isEmpty();
        verify(inventoryService, never()).outbound(any(), any());
    }

    @Test
    void backroomPickByBatchLabelCreatesScannedEvidenceAndActualBatchCost() {
        stubPosOrderThenSave();
        stubUnitConversion(new BigDecimal("5"));
        when(batchBalanceService.isBatchLocationEnabled(STORE_ID, PRODUCT_ID)).thenReturn(true);
        when(locationRepository.findEnabledByStoreAndType(STORE_ID, InventoryLocation.LocationType.SALES_FLOOR.name()))
                .thenReturn(Optional.of(location(FLOOR_LOCATION_ID, InventoryLocation.LocationType.SALES_FLOOR.name())));
        when(locationRepository.findEnabledByStoreAndType(STORE_ID, InventoryLocation.LocationType.STORE_BACKROOM.name()))
                .thenReturn(Optional.of(location(BACKROOM_LOCATION_ID, InventoryLocation.LocationType.STORE_BACKROOM.name())));
        when(batchBalanceRepository.findByLocationAndProduct(FLOOR_LOCATION_ID, PRODUCT_ID))
                .thenReturn(Collections.emptyList());
        String batchBarcode = INTERNAL_BATCH_NO;
        when(receiptBatchRepository.findByInternalReceiptBatchNo(batchBarcode))
                .thenReturn(Optional.of(receiptBatch(BATCH_ID, INTERNAL_BATCH_NO, "40.00")));
        when(batchBalanceService.move(any(BatchBalanceService.MoveRequest.class)))
                .thenReturn(movement(MOVEMENT_ID));

        // 货架无货：POS 结账不阻断，落 UNKNOWN 证据并生成后仓取货请求
        Long orderId = orderService.createPOSOrder(commandForSkuOnly(), 7L);

        List<OrderItemBatchAllocation> beforePick = allocationRepository.findByOrderId(orderId);
        assertThat(beforePick).hasSize(1);
        assertThat(beforePick.get(0).getEvidenceLevel()).isEqualTo(UNKNOWN.name());
        assertThat(pickupRequestRepository.all()).hasSize(1);
        Long requestId = pickupRequestRepository.all().get(0).getId();
        assertThat(requestId).isEqualTo(REQUEST_ID);

        // 拣货员扫描内部批次标签
        List<OrderItemBatchAllocation> fulfilled = fulfillmentService.fulfill(requestId, scannedBatch(batchBarcode), 8L);
        assertThat(fulfilled).hasSize(1);

        assertThat(allocationRepository.findByRequestId(requestId).get(0).getEvidenceLevel())
                .isEqualTo(SCANNED.name());
        OrderItemBatchAllocation allocation = allocationRepository.findByRequestId(requestId).get(0);
        assertThat(allocation.getBatchUnitCost()).isEqualByComparingTo("40.00");
        assertThat(allocation.getAllocatedCost()).isEqualByComparingTo("200.00");
        assertThat(allocation.getBatchId()).isEqualTo(BATCH_ID);
        assertThat(allocation.getSourceMovementId()).isEqualTo(MOVEMENT_ID);
        assertThat(allocationRepository.findByOrderId(orderId)).hasSize(1);
        assertThat(pickupRequestRepository.findById(requestId).orElseThrow().getStatus())
                .isEqualTo(OrderPickupRequest.Status.FULFILLED.name());
        assertThat(pickupRequestRepository.findById(requestId).orElseThrow().getFulfilledBy()).isEqualTo(8L);

        ArgumentCaptor<BatchBalanceService.MoveRequest> moveCaptor =
                ArgumentCaptor.forClass(BatchBalanceService.MoveRequest.class);
        verify(batchBalanceService).move(moveCaptor.capture());
        BatchBalanceService.MoveRequest move = moveCaptor.getValue();
        assertThat(move.purpose()).isEqualTo(BatchBalanceService.MovementPurpose.PICK);
        assertThat(move.fromLocationId()).isEqualTo(BACKROOM_LOCATION_ID);
        assertThat(move.toLocationId()).isEqualTo(FLOOR_LOCATION_ID);
        assertThat(move.batchId()).isEqualTo(BATCH_ID);
        assertThat(move.baseQuantity()).isEqualByComparingTo("5");
        assertThat(move.unitCost()).isEqualByComparingTo("40.00");
        assertThat(move.referenceType()).isEqualTo("PICKUP_REQUEST");
        assertThat(move.referenceId()).isEqualTo(REQUEST_ID);
        assertThat(move.idempotencyKey())
                .isEqualTo("PICK:" + REQUEST_ID + ":" + BATCH_ID + ":" + BACKROOM_LOCATION_ID);
    }

    @Test
    void floorSingleBatchCreatesRuleAllocatedEvidenceAndDoesNotBlockCheckout() {
        stubPosOrderThenSave();
        stubUnitConversion(new BigDecimal("5"));
        when(batchBalanceService.isBatchLocationEnabled(STORE_ID, PRODUCT_ID)).thenReturn(true);
        when(locationRepository.findEnabledByStoreAndType(STORE_ID, InventoryLocation.LocationType.SALES_FLOOR.name()))
                .thenReturn(Optional.of(location(FLOOR_LOCATION_ID, InventoryLocation.LocationType.SALES_FLOOR.name())));
        when(batchBalanceRepository.findByLocationAndProduct(FLOOR_LOCATION_ID, PRODUCT_ID))
                .thenReturn(List.of(balance(BATCH_ID, FLOOR_LOCATION_ID, "10")));
        when(receiptBatchRepository.findById(BATCH_ID))
                .thenReturn(Optional.of(receiptBatch(BATCH_ID, INTERNAL_BATCH_NO, "40.00")));

        Long orderId = orderService.createPOSOrder(commandForSkuOnly(), 7L);

        List<OrderItemBatchAllocation> allocations = allocationRepository.findByOrderId(orderId);
        assertThat(allocations).hasSize(1);
        OrderItemBatchAllocation allocation = allocations.get(0);
        assertThat(allocation.getEvidenceLevel()).isEqualTo(RULE_ALLOCATED.name());
        assertThat(allocation.getBatchId()).isEqualTo(BATCH_ID);
        assertThat(allocation.getLocationId()).isEqualTo(FLOOR_LOCATION_ID);
        assertThat(allocation.getBatchUnitCost()).isEqualByComparingTo("40.00");
        assertThat(allocation.getAllocatedCost()).isEqualByComparingTo("200.00");
        assertThat(allocation.getAllocationRule()).isEqualTo("FLOOR_SINGLE_BATCH");
        assertThat(allocation.getAllocationRuleVersion()).isNotBlank();
        assertThat(pickupRequestRepository.all()).isEmpty();
        verify(inventoryService, never()).outbound(any(), any());
    }

    @Test
    void skuOnlyBarcodeMustNeverBecomeScannedEvidence() {
        Long requestId = seedPendingRequest(null);
        when(receiptBatchRepository.findByInternalReceiptBatchNo(SKU_ONLY_EAN)).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> fulfillmentService.fulfill(requestId, scannedBatch(SKU_ONLY_EAN), 8L));

        assertThat(ex.getMessage()).contains("批次");
        verify(batchBalanceService, never()).move(any(BatchBalanceService.MoveRequest.class));
        assertThat(allocationRepository.findByRequestId(requestId)).isEmpty();
        assertThat(pickupRequestRepository.findById(requestId).orElseThrow().getStatus())
                .isEqualTo(OrderPickupRequest.Status.PENDING.name());
    }

    @Test
    void batchLabelForDifferentProductIsRejected() {
        Long requestId = seedPendingRequest(null);
        InventoryReceiptBatch foreignBatch = receiptBatch(BATCH_ID, INTERNAL_BATCH_NO, "40.00");
        foreignBatch.setProductId(PRODUCT_ID + 1);
        when(receiptBatchRepository.findByInternalReceiptBatchNo(INTERNAL_BATCH_NO))
                .thenReturn(Optional.of(foreignBatch));

        assertThrows(IllegalArgumentException.class,
                () -> fulfillmentService.fulfill(requestId, scannedBatch(INTERNAL_BATCH_NO), 8L));

        verify(batchBalanceService, never()).move(any(BatchBalanceService.MoveRequest.class));
        assertThat(allocationRepository.findByRequestId(requestId)).isEmpty();
    }

    @Test
    void explicitBatchAndLocationConfirmationCreatesFulfillmentConfirmedEvidence() {
        Long requestId = seedPendingRequest(BACKROOM_LOCATION_ID);
        when(receiptBatchRepository.findById(BATCH_ID))
                .thenReturn(Optional.of(receiptBatch(BATCH_ID, INTERNAL_BATCH_NO, "40.00")));
        when(locationRepository.findById(BACKROOM_LOCATION_ID))
                .thenReturn(Optional.of(location(BACKROOM_LOCATION_ID, InventoryLocation.LocationType.STORE_BACKROOM.name())));
        when(locationRepository.findEnabledByStoreAndType(STORE_ID, InventoryLocation.LocationType.SALES_FLOOR.name()))
                .thenReturn(Optional.of(location(FLOOR_LOCATION_ID, InventoryLocation.LocationType.SALES_FLOOR.name())));
        when(batchBalanceService.move(any(BatchBalanceService.MoveRequest.class)))
                .thenReturn(movement(MOVEMENT_ID));

        List<OrderItemBatchAllocation> fulfilled =
                fulfillmentService.fulfill(requestId, confirmedBatch(BATCH_ID, BACKROOM_LOCATION_ID), 8L);

        assertThat(fulfilled).hasSize(1);
        OrderItemBatchAllocation allocation = allocationRepository.findByRequestId(requestId).get(0);
        assertThat(allocation.getEvidenceLevel()).isEqualTo(FULFILLMENT_CONFIRMED.name());
        assertThat(allocation.getBatchId()).isEqualTo(BATCH_ID);
        assertThat(allocation.getBatchUnitCost()).isEqualByComparingTo("40.00");
        assertThat(allocation.getAllocatedCost()).isEqualByComparingTo("200.00");
        assertThat(allocation.getSourceMovementId()).isEqualTo(MOVEMENT_ID);
        assertThat(pickupRequestRepository.findById(requestId).orElseThrow().getStatus())
                .isEqualTo(OrderPickupRequest.Status.FULFILLED.name());
    }

    @Test
    void fulfillRejectsAlreadyProcessedRequest() {
        Long requestId = seedPendingRequest(BACKROOM_LOCATION_ID);
        pickupRequestRepository.findById(requestId).orElseThrow()
                .setStatus(OrderPickupRequest.Status.FULFILLED.name());

        assertThrows(IllegalStateException.class,
                () -> fulfillmentService.fulfill(requestId, scannedBatch(INTERNAL_BATCH_NO), 8L));

        verify(batchBalanceService, never()).move(any(BatchBalanceService.MoveRequest.class));
        assertThat(allocationRepository.findByRequestId(requestId)).isEmpty();
    }

    @Test
    void createRequestPersistsPendingPickupWithConfiguredSource() {
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order()));
        when(itemRepository.findById(ITEM_ID)).thenReturn(Optional.of(orderItem()));
        when(batchBalanceService.isBatchLocationEnabled(STORE_ID, PRODUCT_ID)).thenReturn(true);
        when(locationRepository.findById(BACKROOM_LOCATION_ID))
                .thenReturn(Optional.of(location(BACKROOM_LOCATION_ID, InventoryLocation.LocationType.STORE_BACKROOM.name())));
        stubUnitConversion(new BigDecimal("5"));

        Long requestId = fulfillmentService.createRequest(ORDER_ID, ITEM_ID, BACKROOM_LOCATION_ID);

        assertThat(requestId).isEqualTo(REQUEST_ID);
        OrderPickupRequest saved = pickupRequestRepository.findById(requestId).orElseThrow();
        assertThat(saved.getOrderId()).isEqualTo(ORDER_ID);
        assertThat(saved.getOrderItemId()).isEqualTo(ITEM_ID);
        assertThat(saved.getProductId()).isEqualTo(PRODUCT_ID);
        assertThat(saved.getStoreId()).isEqualTo(STORE_ID);
        assertThat(saved.getRequestedBaseQuantity()).isEqualByComparingTo("5");
        assertThat(saved.getSourceLocationId()).isEqualTo(BACKROOM_LOCATION_ID);
        assertThat(saved.getStatus()).isEqualTo(OrderPickupRequest.Status.PENDING.name());
    }

    @Test
    void createRequestRejectsNonActivatedSku() {
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order()));
        when(itemRepository.findById(ITEM_ID)).thenReturn(Optional.of(orderItem()));

        assertThrows(IllegalStateException.class,
                () -> fulfillmentService.createRequest(ORDER_ID, ITEM_ID, BACKROOM_LOCATION_ID));

        assertThat(pickupRequestRepository.all()).isEmpty();
        verify(locationRepository, never()).save(any(InventoryLocation.class));
        verify(batchBalanceService, never()).move(any(BatchBalanceService.MoveRequest.class));
    }

    @Test
    void nonActivatedSkuSaleKeepsLegacyOutboundAndWritesNoEvidence() {
        stubPosOrderThenSave();

        Long orderId = orderService.createPOSOrder(commandForSkuOnly(), 7L);

        assertThat(orderId).isEqualTo(ORDER_ID);
        verify(inventoryService).outbound(any(), eq(7L));
        assertThat(allocationRepository.findByOrderId(orderId)).isEmpty();
        assertThat(pickupRequestRepository.all()).isEmpty();
        verifyNoInteractions(productUnitService);
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
        return command;
    }

    private PickupFulfillmentCommand confirmedBatch(Long batchId, Long locationId) {
        PickupFulfillmentCommand command = new PickupFulfillmentCommand();
        command.setBatchId(batchId);
        command.setLocationId(locationId);
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
        OrderPickupRequest request = new OrderPickupRequest();
        request.setOrderId(ORDER_ID);
        request.setOrderItemId(ITEM_ID);
        request.setProductId(PRODUCT_ID);
        request.setStoreId(STORE_ID);
        request.setRequestedBaseQuantity(new BigDecimal("5"));
        request.setSourceLocationId(sourceLocationId);
        request.setStatus(OrderPickupRequest.Status.PENDING.name());
        return pickupRequestRepository.save(request).getId();
    }

    private Order order() {
        Order order = new Order();
        order.setId(ORDER_ID);
        order.setOrderNo("SO202609120001");
        order.setStoreId(STORE_ID);
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
        public List<OrderItemBatchAllocation> findByOrderItemId(Long orderItemId) {
            return rows.stream()
                    .filter(row -> orderItemId.equals(row.getOrderItemId()))
                    .toList();
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

        List<OrderPickupRequest> all() {
            return rows;
        }
    }
}
