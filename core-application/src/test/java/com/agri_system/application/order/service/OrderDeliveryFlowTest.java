package com.agri_system.application.order.service;

import com.agri_system.application.credit.service.CreditApplicationService;
import com.agri_system.application.inventory.service.InventoryApplicationService;
import com.agri_system.application.order.dto.command.OrderItemDTO;
import com.agri_system.application.order.dto.command.OrderExternalPaymentConfirmCommand;
import com.agri_system.application.order.dto.command.OrderPayCommand;
import com.agri_system.application.order.dto.command.POSOrderCreateCommand;
import com.agri_system.application.order.dto.command.PaymentDetailDTO;
import com.agri_system.application.order.dto.vo.OrderExternalPaymentIntentVO;
import com.agri_system.application.order.dto.vo.OrderPayPreviewVO;
import com.agri_system.application.performance.event.OrderFinishedEvent;
import com.agri_system.application.product.dto.vo.PriceContext;
import com.agri_system.application.product.dto.vo.PriceQuote;
import com.agri_system.application.product.service.ProductPricingService;
import com.agri_system.application.wallet.service.WalletApplicationService;
import com.agri_system.domain.after_sales.repository.AfterSalesItemRepository;
import com.agri_system.domain.after_sales.repository.AfterSalesRepository;
import com.agri_system.domain.customer.repository.CustomerRepository;
import com.agri_system.domain.order.model.Order;
import com.agri_system.domain.order.model.OrderItem;
import com.agri_system.domain.order.model.OrderPaymentIntent;
import com.agri_system.domain.order.repository.OrderDeliveryRepository;
import com.agri_system.domain.order.repository.OrderItemRepository;
import com.agri_system.domain.order.repository.OrderPaymentIntentRepository;
import com.agri_system.domain.order.repository.OrderPaymentRepository;
import com.agri_system.domain.order.repository.OrderRepository;
import com.agri_system.domain.order.service.OrderDomainService;
import com.agri_system.domain.product.model.Product;
import com.agri_system.domain.product.repository.ProductRepository;
import com.agri_system.domain.store.model.Staff;
import com.agri_system.domain.store.repository.StaffRepository;
import com.agri_system.domain.store.repository.StoreRepository;
import com.agri_system.domain.wallet.repository.WalletAuthCodeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderDeliveryFlowTest {

    @Mock
    private com.agri_system.domain.coupon.repository.CustomerCouponRepository couponRepository;
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
    private com.agri_system.application.productpackage.service.PackagePricingService packagePricingService;
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
    private PickupFulfillmentService pickupFulfillmentService;
    @Mock
    private ManualPriceAuditService manualPriceAuditService;

    @InjectMocks
    private OrderApplicationService orderService;

    private Order order;
    private com.agri_system.domain.order.model.OrderDelivery delivery;
    private final java.util.concurrent.atomic.AtomicBoolean remaining=new java.util.concurrent.atomic.AtomicBoolean(true);

    @org.junit.jupiter.api.BeforeEach void deliveryFixture() {
        order=new Order();order.setId(42L);order.setStoreId(3L);order.setOrderType(1);order.setOrderStatus(4);
        delivery=new com.agri_system.domain.order.model.OrderDelivery();delivery.setId(50L);delivery.setOrderId(42L);
        delivery.setDeliveryType(2);delivery.setDeliveryStatus(2);
        orderRepository=org.mockito.Mockito.mock(OrderRepository.class,invocation -> {
            if(invocation.getMethod().getName().equals("hasRemainingDeliveryQuantity")) return remaining.get();
            return org.mockito.Mockito.RETURNS_DEFAULTS.answer(invocation);
        });
        ReflectionTestUtils.setField(orderService,"orderRepository",orderRepository);
        org.mockito.Mockito.lenient().when(orderRepository.findById(42L)).thenReturn(Optional.of(order));
        org.mockito.Mockito.lenient().when(orderRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(order));
        org.mockito.Mockito.lenient().when(deliveryRepository.findByOrderId(42L)).thenReturn(Optional.of(delivery));
        var staff=new Staff();staff.setId(8L);staff.setStatus(1);staff.setStoreId(3L);
        org.mockito.Mockito.lenient().when(staffRepository.findById(8L)).thenReturn(Optional.of(staff));
    }
    private com.agri_system.application.order.dto.command.OrderPickupVerifyCommand onlinePickup() {
        order.setOrderType(2);order.setOrderStatus(3);order.setPickupCode("123456");
        org.mockito.Mockito.lenient().when(orderRepository.findByPickupCode("123456")).thenReturn(Optional.of(order));
        var command=new com.agri_system.application.order.dto.command.OrderPickupVerifyCommand();command.setPickupCode("123456");return command;
    }
    @Test void onlinePickupPausesPendingAfterSalesBeforeAnyCompletion() {
        var command=onlinePickup();pendingAfterSales(10);
        assertThrows(IllegalArgumentException.class,()->orderService.verifyPickup(command,8L));
        verify(orderRepository,never()).save(any());assertNoSettlement();
    }
    @Test void onlinePickupPausesRefundingAfterSalesBeforeAnyCompletion() {
        var command=onlinePickup();pendingAfterSales(40);
        assertThrows(IllegalArgumentException.class,()->orderService.verifyPickup(command,8L));
        verify(orderRepository,never()).save(any());assertNoSettlement();
    }
    @Test void onlinePickupRequiresActualRemainingSaleBeforeCompletion() {
        var command=onlinePickup();remaining.set(false);
        assertThrows(IllegalArgumentException.class,()->orderService.verifyPickup(command,8L));
        verify(orderRepository,never()).save(any());assertNoSettlement();
    }
    @Test void onlinePickupRechecksParentAfterLocatorInsteadOfOverwritingRefund() {
        var command=onlinePickup();Order refunded=new Order();refunded.setId(42L);refunded.setOrderType(2);refunded.setOrderStatus(8);refunded.setPickupCode("123456");
        when(orderRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(refunded));
        assertThrows(IllegalArgumentException.class,()->orderService.verifyPickup(command,8L));
        verify(orderRepository,never()).save(any());assertNoSettlement();
    }
    @Test void onlinePickupFinishedReplayDoesNotPublishCompletionAgain() {
        var command=onlinePickup();
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(()->orderService.verifyPickup(command,8L));
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(()->orderService.verifyPickup(command,8L));
        verify(orderRepository,org.mockito.Mockito.times(2)).findByIdForUpdate(42L);
        verify(orderRepository,org.mockito.Mockito.times(1)).save(order);
        verify(eventPublisher,org.mockito.Mockito.times(1)).publishEvent(any(OrderFinishedEvent.class));
    }
    private com.agri_system.application.order.dto.command.OrderShipCommand shipment() {
        var command=new com.agri_system.application.order.dto.command.OrderShipCommand();command.setOrderId(42L);command.setDeliveryStaffId(8L);return command;
    }
    private void pendingAfterSales(int status) {
        var fact=new com.agri_system.domain.after_sales.model.AfterSales();fact.setStatus(status);
        org.mockito.Mockito.lenient().when(afterSalesRepository.findByOrderId(42L)).thenReturn(java.util.List.of(fact));
    }
    @Test void partialReturnedOrderShipsRemainingGoodsAndPreservesRefundStatus() {
        order.setOrderStatus(9);pendingAfterSales(50);orderService.shipOrder(shipment(),8L);
        assertEquals(9,order.getOrderStatus());assertEquals(3,delivery.getDeliveryStatus());
        org.junit.jupiter.api.Assertions.assertNotNull(delivery.getDeliveryAt());verify(orderRepository).findByIdForUpdate(42L);
        assertNoSettlement();
    }
    @Test void partialReturnedOrderReceivesRemainingGoodsAndPreservesRefundStatus() {
        order.setOrderStatus(9);delivery.setDeliveryStatus(3);pendingAfterSales(50);
        orderService.confirmReceipt(42L,8L);assertEquals(9,order.getOrderStatus());assertEquals(4,delivery.getDeliveryStatus());
        org.junit.jupiter.api.Assertions.assertNotNull(delivery.getReceivedAt());assertNoSettlement();
    }
    @Test void receiptConfirmationRejectsChangedDisplayedGoodsUnderOriginalOrderLock() throws Exception {
        order.setOrderStatus(5);delivery.setDeliveryStatus(3);
        var row=new com.agri_system.domain.order.model.DeliveryItemQuantity();row.setOrderItemId(1L);row.setProductName("肥料");row.setQuantity(new BigDecimal("3"));row.setUnit("袋");
        var rows=java.util.List.of(row);when(orderRepository.findRemainingDeliveryItems(42L)).thenReturn(rows);
        String key=ReflectionTestUtils.invokeMethod(orderService,"deliveryConfirmationKey",order,delivery,rows);
        var method=orderService.getClass().getMethod("confirmReceipt",Long.class,Long.class,String.class);
        row.setQuantity(new BigDecimal("2"));
        var failure=assertThrows(java.lang.reflect.InvocationTargetException.class,()->method.invoke(orderService,42L,8L,key));
        org.junit.jupiter.api.Assertions.assertInstanceOf(IllegalArgumentException.class,failure.getCause());assertNoDeliveryWrite();
        row.setQuantity(new BigDecimal("3"));method.invoke(orderService,42L,8L,key);
        assertEquals(4,delivery.getDeliveryStatus());assertNoSettlement();
    }
    @Test void posReceiptAndReplayDoNotSettleOrMoveInventoryAgain() {
        order.setOrderStatus(5);delivery.setDeliveryStatus(3);orderService.confirmReceipt(42L,8L);
        assertEquals(6,order.getOrderStatus());assertEquals(4,delivery.getDeliveryStatus());
        var received=delivery.getReceivedAt();orderService.confirmReceipt(42L,8L);
        assertEquals(received,delivery.getReceivedAt());assertNoSettlement();
    }
    @Test void firstOnlineReceiptSettlesOnceAndReplayDoesNotRepeat() {
        order.setOrderType(3);order.setOrderStatus(5);delivery.setDeliveryStatus(3);
        orderService.confirmReceipt(42L,8L);orderService.confirmReceipt(42L,8L);
        verify(eventPublisher).publishEvent(any(OrderFinishedEvent.class));
        verify(inventoryService,never()).outbound(any(),any());
    }
    @Test void appliedAfterSalesPausesShipmentWithoutChangingDelivery() {
        pendingAfterSales(10);assertThrows(IllegalArgumentException.class,()->orderService.shipOrder(shipment(),8L));
        assertEquals(4,order.getOrderStatus());assertEquals(2,delivery.getDeliveryStatus());assertNoDeliveryWrite();
    }
    @Test void approvedAfterSalesPausesReceiptWithoutChangingDelivery() {
        order.setOrderStatus(5);delivery.setDeliveryStatus(3);pendingAfterSales(40);
        assertThrows(IllegalArgumentException.class,()->orderService.confirmReceipt(42L,8L));
        assertEquals(5,order.getOrderStatus());assertEquals(3,delivery.getDeliveryStatus());assertNoDeliveryWrite();
    }
    @Test void absentOrFullyReturnedOutboundEvidenceBlocksBothActions() {
        remaining.set(false);assertThrows(IllegalArgumentException.class,()->orderService.shipOrder(shipment(),8L));
        order.setOrderStatus(5);delivery.setDeliveryStatus(3);
        assertThrows(IllegalArgumentException.class,()->orderService.confirmReceipt(42L,8L));assertNoDeliveryWrite();
    }
    @Test void cancelledOrFullyRefundedOrderCannotDeliver() {
        for(int status:java.util.List.of(7,8)) {
            order.setOrderStatus(status);assertThrows(IllegalArgumentException.class,()->orderService.shipOrder(shipment(),8L));
            delivery.setDeliveryStatus(3);assertThrows(IllegalArgumentException.class,()->orderService.confirmReceipt(42L,8L));
            delivery.setDeliveryStatus(2);
        }
        assertNoDeliveryWrite();
    }
    @Test void shipmentReplayKeepsOriginalDeliveryTimestamp() {
        orderService.shipOrder(shipment(),8L);var sent=delivery.getDeliveryAt();orderService.shipOrder(shipment(),8L);
        assertEquals(sent,delivery.getDeliveryAt());assertEquals(3,delivery.getDeliveryStatus());assertNoSettlement();
    }
    @Test void localDeliveryRequiresDriverAndCannotBecomeExpress() {
        var command=shipment();command.setDeliveryStaffId(null);command.setLogisticsCompany("顺丰");command.setTrackingNo("123");
        assertThrows(IllegalArgumentException.class,()->orderService.shipOrder(command,8L));
        assertEquals(2,delivery.getDeliveryType());assertEquals(2,delivery.getDeliveryStatus());assertNoDeliveryWrite();
    }
    @Test void 全退订单分页剩余物料标记来自整单且不受前三条概览限制() throws Exception {
        order.setOrderStatus(8);
        when(paymentService.financialSummary(order)).thenReturn(new OrderPaymentService.FinancialSummary(
                BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO));
        var page = new com.mybatisflex.core.paginate.Page<Order>();page.setRecords(java.util.List.of(order));
        when(orderRepository.findPage(any(),any(),any(),any(),any(),any(),any(),any(),any(),any())).thenReturn(page);
        var items=java.util.stream.LongStream.rangeClosed(1,4).mapToObj(id->{
            var item=new OrderItem();item.setId(id);item.setOrderId(order.getId());item.setQuantity(BigDecimal.ONE);return item;
        }).toList();
        var remaining=new java.util.concurrent.atomic.AtomicBoolean(true);
        var repository=org.mockito.Mockito.mock(OrderItemRepository.class, invocation -> {
            if (invocation.getMethod().getName().equals("findByOrderId")) return items;
            if (invocation.getMethod().getName().equals("findOrderIdsWithReturnableItems")) return remaining.get()?java.util.Set.of(order.getId()):java.util.Set.of();
            return org.mockito.Answers.RETURNS_DEFAULTS.answer(invocation);
        });
        ReflectionTestUtils.setField(orderService,"itemRepository",repository);
        var query=new com.agri_system.application.order.dto.query.OrderPageQuery();query.setPageNum(1);query.setPageSize(10);
        var row=orderService.queryOrderPage(query).getRecords().get(0);
        assertEquals(3,row.getItems().size());
        var json=new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();
        assertEquals(Boolean.TRUE,json.convertValue(row,java.util.Map.class).get("hasReturnableItems"));
        remaining.set(false);
        assertEquals(Boolean.FALSE,json.convertValue(orderService.queryOrderPage(query).getRecords().get(0),java.util.Map.class).get("hasReturnableItems"));
    }
    @Test void deliveryActionsAreReadOnlyDerivedCapabilitiesInPageAndDetail() {
        when(paymentService.financialSummary(order)).thenReturn(new OrderPaymentService.FinancialSummary(
                BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO));
        var page = new com.mybatisflex.core.paginate.Page<Order>();page.setRecords(java.util.List.of(order));
        when(orderRepository.findPage(any(),any(),any(),any(),any(),any(),any(),any(),any(),any())).thenReturn(page);
        var query = new com.agri_system.application.order.dto.query.OrderPageQuery();
        var row = orderService.queryOrderPage(query).getRecords().get(0);
        org.junit.jupiter.api.Assertions.assertTrue(row.isCanShip());
        org.junit.jupiter.api.Assertions.assertFalse(row.isCanConfirmReceipt());assertEquals(2,row.getDeliveryStatus());
        order.setOrderStatus(9);delivery.setDeliveryStatus(3);delivery.setReceivedAt(LocalDateTime.now().minusDays(1));
        var detail = orderService.getOrderDetail(42L);
        org.junit.jupiter.api.Assertions.assertFalse(detail.isCanShip());
        org.junit.jupiter.api.Assertions.assertTrue(detail.isCanConfirmReceipt());
        assertEquals(delivery.getReceivedAt(),detail.getDelivery().getReceivedAt());
        delivery.setDeliveryType(null);
        org.junit.jupiter.api.Assertions.assertFalse(orderService.getOrderDetail(42L).isCanConfirmReceipt());
        org.junit.jupiter.api.Assertions.assertFalse(orderService.queryOrderPage(query).getRecords().get(0).isCanConfirmReceipt());
        delivery.setDeliveryType(2);
        pendingAfterSales(10);
        org.junit.jupiter.api.Assertions.assertFalse(orderService.getOrderDetail(42L).isCanConfirmReceipt());
        assertNoDeliveryWrite();
    }
    @Test void unknownDeliveryTypeDoesNotGuessExpressOrOfferActions() {
        delivery.setDeliveryType(null);var command=shipment();command.setDeliveryStaffId(null);
        command.setLogisticsCompany("快递");command.setTrackingNo("123");
        assertThrows(IllegalArgumentException.class,()->orderService.shipOrder(command,8L));assertNoDeliveryWrite();
    }
    private void assertNoDeliveryWrite() {verify(deliveryRepository,never()).save(any());verify(orderRepository,never()).save(any());assertNoSettlement();}
    private void assertNoSettlement() {
        verify(paymentRepository,never()).save(any());verify(inventoryService,never()).outbound(any(),any());
        verify(creditService,never()).addDebt(any(),any(),any());verify(eventPublisher,never()).publishEvent(any(Object.class));
    }

    @Test void pageReusesRepeatedPartyReadsOnlyWithinOneQueryAndRefreshesNextQuery() {
        order.setCustomerId(7L);
        var second=new Order();second.setId(43L);second.setStoreId(3L);second.setCustomerId(7L);second.setOrderType(1);second.setOrderStatus(6);
        var page=new com.mybatisflex.core.paginate.Page<Order>();page.setRecords(java.util.List.of(order,second));page.setTotalRow(2);
        when(orderRepository.findPage(any(),any(),any(),any(),any(),any(),any(),any(),any(),any())).thenReturn(page);
        org.mockito.Mockito.lenient().when(paymentService.financialSummary(any())).thenReturn(new OrderPaymentService.FinancialSummary(BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO));
        var store=new com.agri_system.domain.store.model.Store();store.setId(3L);store.setName("原门店");
        var customer=new com.agri_system.domain.customer.model.Customer();customer.setId(7L);customer.setName("原客户");customer.setPhone("13800000007");
        when(storeRepository.findByIdIncludingDeleted(3L)).thenReturn(Optional.of(store));
        when(customerRepository.findByIdIncludingDeleted(7L)).thenReturn(Optional.of(customer));
        var query=new com.agri_system.application.order.dto.query.OrderPageQuery();var first=orderService.queryOrderPage(query);
        assertEquals(2,first.getRecords().size());assertEquals("原门店",first.getRecords().get(0).getStoreName());assertEquals("原客户",first.getRecords().get(1).getCustomerName());assertEquals("13800000007",first.getRecords().get(0).getCustomerPhone());
        verify(storeRepository,org.mockito.Mockito.times(1)).findByIdIncludingDeleted(3L);
        verify(customerRepository,org.mockito.Mockito.times(1)).findByIdIncludingDeleted(7L);
        store.setName("更新门店");customer.setName("更新客户");
        var refreshed=orderService.queryOrderPage(query);assertEquals("更新门店",refreshed.getRecords().get(1).getStoreName());assertEquals("更新客户",refreshed.getRecords().get(0).getCustomerName());
        verify(storeRepository,org.mockito.Mockito.times(2)).findByIdIncludingDeleted(3L);
        verify(customerRepository,org.mockito.Mockito.times(2)).findByIdIncludingDeleted(7L);
    }

    @Test void pageKeepsMissingPartiesEmptyWithoutRepeatingMissingReads() {
        order.setCustomerId(7L);
        var second=new Order();second.setId(43L);second.setStoreId(3L);second.setCustomerId(7L);
        var third=new Order();third.setId(44L);third.setStoreId(4L);third.setCustomerId(8L);
        var page=new com.mybatisflex.core.paginate.Page<Order>();page.setRecords(java.util.List.of(order,second,third));
        when(orderRepository.findPage(any(),any(),any(),any(),any(),any(),any(),any(),any(),any())).thenReturn(page);
        org.mockito.Mockito.lenient().when(paymentService.financialSummary(any())).thenReturn(new OrderPaymentService.FinancialSummary(BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO));
        var other=new com.agri_system.domain.store.model.Store();other.setId(4L);other.setName("其他门店");
        var customer=new com.agri_system.domain.customer.model.Customer();customer.setId(8L);customer.setName("其他客户");
        when(storeRepository.findByIdIncludingDeleted(3L)).thenReturn(Optional.empty());when(customerRepository.findByIdIncludingDeleted(7L)).thenReturn(Optional.empty());
        when(storeRepository.findByIdIncludingDeleted(4L)).thenReturn(Optional.of(other));when(customerRepository.findByIdIncludingDeleted(8L)).thenReturn(Optional.of(customer));
        var result=orderService.queryOrderPage(new com.agri_system.application.order.dto.query.OrderPageQuery());
        org.junit.jupiter.api.Assertions.assertNull(result.getRecords().get(0).getStoreName());org.junit.jupiter.api.Assertions.assertNull(result.getRecords().get(1).getCustomerName());
        assertEquals("其他门店",result.getRecords().get(2).getStoreName());assertEquals("其他客户",result.getRecords().get(2).getCustomerName());
        verify(storeRepository,org.mockito.Mockito.times(1)).findByIdIncludingDeleted(3L);verify(customerRepository,org.mockito.Mockito.times(1)).findByIdIncludingDeleted(7L);
    }
}
