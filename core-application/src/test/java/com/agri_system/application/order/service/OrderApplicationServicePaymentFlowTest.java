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
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderApplicationServicePaymentFlowTest {

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
    private PickupFulfillmentService pickupFulfillmentService;
    @Mock
    private ManualPriceAuditService manualPriceAuditService;

    @InjectMocks
    private OrderApplicationService orderService;

    @Test
    void payOrderReturnsPreviewWhenExternalPaymentStillRequired() {
        Order order = mockPendingClientOrder();
        OrderPayPreviewVO preview = preview(new BigDecimal("30.00"));
        OrderPayCommand command = new OrderPayCommand();
        command.setOrderId(order.getId());

        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(paymentService.previewPayment(order, null, null, null)).thenReturn(preview);

        OrderPayPreviewVO result = orderService.payOrder(command, order.getCustomerId());

        assertSame(preview, result);
        verify(paymentService, never()).executePayment(any(), any(), any(), any(), any(), anyBoolean(), any(), any(), any());
        verify(inventoryService, never()).outbound(any(), any());
    }

    @Test
    void createExternalPaymentIntentReturnsIntentSnapshot() {
        Order order = mockPendingClientOrder();
        OrderPayPreviewVO preview = preview(new BigDecimal("18.80"));
        OrderPayCommand command = new OrderPayCommand();
        command.setOrderId(order.getId());
        command.setExternalPaymentType(2);

        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(paymentService.previewPayment(order, null, null, null)).thenReturn(preview);
        when(paymentIntentRepository.save(any(OrderPaymentIntent.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        OrderExternalPaymentIntentVO intent = orderService.createExternalPaymentIntent(command, order.getCustomerId());

        assertEquals(order.getId(), intent.getOrderId());
        assertEquals(order.getOrderNo(), intent.getOrderNo());
        assertEquals(new BigDecimal("18.80"), intent.getExternalAmount());
        assertSame(preview, intent.getPreview());
        verify(paymentIntentRepository).expirePendingByOrderId(order.getId());
        verify(paymentIntentRepository).save(any(OrderPaymentIntent.class));
    }

    @Test
    void confirmExternalPaymentFinalizesOrderWhenPreviewMatches() {
        ReflectionTestUtils.setField(orderService, "clientPaymentConfirmEnabled", true);
        Order order = mockPendingClientOrder();
        OrderPaymentIntent intent = mockPendingIntent(order);
        OrderItem item = new OrderItem();
        item.setProductId(9L);
        item.setQuantity(new BigDecimal("2"));
        item.setUnit("袋");

        OrderPayPreviewVO preview = preview(new BigDecimal("50.00"));
        OrderExternalPaymentConfirmCommand command = new OrderExternalPaymentConfirmCommand();
        command.setIntentNo(intent.getIntentNo());
        command.setExternalTransactionNo("wx202604110001");
        command.setExternalPaidAt(LocalDateTime.of(2026, 4, 11, 10, 30));

        when(paymentIntentRepository.findByIntentNo(intent.getIntentNo())).thenReturn(Optional.of(intent));
        when(paymentService.previewPayment(order, null, null, null)).thenReturn(preview);
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(itemRepository.findByOrderId(order.getId())).thenReturn(Collections.singletonList(item));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentIntentRepository.save(any(OrderPaymentIntent.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        orderService.confirmExternalPayment(command, order.getCustomerId());

        verify(paymentService).executePayment(
                eq(order),
                isNull(),
                isNull(),
                isNull(),
                eq(order.getCustomerId()),
                eq(true),
                eq(2),
                eq("wx202604110001"),
                eq(LocalDateTime.of(2026, 4, 11, 10, 30))
        );
        verify(inventoryService).outbound(any(), eq(order.getCustomerId()));
        verify(paymentIntentRepository).save(any(OrderPaymentIntent.class));

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());
        assertEquals(3, orderCaptor.getValue().getOrderStatus());

        verify(eventPublisher, never()).publishEvent(any(OrderFinishedEvent.class));
    }

    @Test
    void confirmExternalPaymentRejectsWhenExternalAmountChanged() {
        ReflectionTestUtils.setField(orderService, "clientPaymentConfirmEnabled", true);
        Order order = mockPendingClientOrder();
        OrderPaymentIntent intent = mockPendingIntent(order);
        intent.setExternalAmount(new BigDecimal("39.99"));
        OrderPayPreviewVO preview = preview(new BigDecimal("40.00"));
        OrderExternalPaymentConfirmCommand command = new OrderExternalPaymentConfirmCommand();
        command.setIntentNo(intent.getIntentNo());
        command.setExternalTransactionNo("wx202604110002");

        when(paymentIntentRepository.findByIntentNo(intent.getIntentNo())).thenReturn(Optional.of(intent));
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(paymentService.previewPayment(order, null, null, null)).thenReturn(preview);

        assertThrows(IllegalArgumentException.class,
                () -> orderService.confirmExternalPayment(command, order.getCustomerId()));
        verify(paymentService, never()).executePayment(any(), any(), any(), any(), any(), anyBoolean(), any(), any(), any());
        verify(inventoryService, never()).outbound(any(), any());
    }

    @Test
    void confirmExternalPaymentReturnsWhenIntentAlreadyConfirmedWithSameTransaction() {
        ReflectionTestUtils.setField(orderService, "clientPaymentConfirmEnabled", true);
        Order order = mockPendingClientOrder();
        order.setOrderStatus(3);
        OrderPaymentIntent intent = mockPendingIntent(order);
        intent.setStatus(2);
        intent.setExternalTransactionNo("wx202604110003");
        OrderExternalPaymentConfirmCommand command = new OrderExternalPaymentConfirmCommand();
        command.setIntentNo(intent.getIntentNo());
        command.setExternalTransactionNo("wx202604110003");

        when(paymentIntentRepository.findByIntentNo(intent.getIntentNo())).thenReturn(Optional.of(intent));

        orderService.confirmExternalPayment(command, order.getCustomerId());

        verify(paymentService, never()).executePayment(any(), any(), any(), any(), any(), anyBoolean(), any(), any(), any());
        verify(orderRepository, never()).findById(any());
    }

    @Test
    void confirmExternalPaymentRejectedWhenClientConfirmDisabled() {
        OrderExternalPaymentConfirmCommand command = new OrderExternalPaymentConfirmCommand();
        command.setIntentNo("PI-ANY");
        command.setExternalTransactionNo("forged-transaction");

        assertThrows(IllegalStateException.class,
                () -> orderService.confirmExternalPayment(command, 1L));
        verify(paymentIntentRepository, never()).findByIntentNo(any());
        verify(paymentService, never()).executePayment(any(), any(), any(), any(), any(), anyBoolean(), any(), any(), any());
        verify(inventoryService, never()).outbound(any(), any());
    }

    @Test
    void createPosOrderRejectsMixedWalletAmountWhenWalletCoverageInsufficient() {
        POSOrderCreateCommand command = new POSOrderCreateCommand();
        command.setStaffId(8L);
        command.setCustomerId(12L);
        command.setPaymentType(10);

        OrderItemDTO item = new OrderItemDTO();
        item.setProductId(99L);
        item.setQuantity(BigDecimal.ONE);
        item.setUnit("袋");
        item.setUnitPrice(new BigDecimal("100.00"));
        command.setItems(Collections.singletonList(item));

        PaymentDetailDTO payment = new PaymentDetailDTO();
        payment.setPaymentType(4);
        payment.setAmount(new BigDecimal("100.00"));
        command.setPayments(Collections.singletonList(payment));

        Staff staff = new Staff();
        staff.setId(8L);
        staff.setStoreId(3L);

        Product product = new Product();
        product.setId(99L);
        product.setName("复合肥");
        product.setMainUnit("袋");
        product.setAvgCostPrice(new BigDecimal("40.00"));
        product.setAllowSpecialFund(1);

        OrderItem orderItem = new OrderItem();
        orderItem.setAllowSpecialFund(1);
        orderItem.setTotalPrice(new BigDecimal("100.00"));

        when(staffRepository.findById(8L)).thenReturn(Optional.of(staff));
        when(orderDomainService.generateOrderNo()).thenReturn("SO202604120001");
        when(productRepository.findById(99L)).thenReturn(Optional.of(product));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            if (order.getId() == null) {
                order.setId(100L);
            }
            return order;
        });
        when(itemRepository.findByOrderId(100L)).thenReturn(Collections.singletonList(orderItem));
        when(orderDomainService.calculateMaxSpecialFundAmount(any())).thenReturn(new BigDecimal("20.00"));
        when(walletService.getSpecialFundBalance(12L)).thenReturn(new BigDecimal("20.00"));
        when(walletService.getBalance(12L)).thenReturn(new BigDecimal("30.00"));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> orderService.createPOSOrder(command, 8L));

        assertEquals("ORDER_AMOUNT_INVALID: 钱包可支付金额不足，当前最多可支付 ¥50.00", ex.getMessage());
        verify(walletService, never()).deductSpecialFund(any(), any(), any(), any(), any(), any());
        verify(walletService, never()).deductBalance(any(), any(), any(), any(), any(), any());
        verify(inventoryService, never()).outbound(any(), any());
    }

    @Test
    void createPosOrderDelegatesSaleEvidenceToNewChainWhenSkuActivated() {
        POSOrderCreateCommand command = cashPosCommand(false);
        stubCashPosOrderPersistence();
        when(pickupFulfillmentService.recordDirectSaleEvidence(
                any(Order.class), any(OrderItem.class), eq(false), eq(8L))).thenReturn(true);

        Long orderId = orderService.createPOSOrder(command, 8L);

        assertEquals(500L, orderId);
        verify(pickupFulfillmentService).recordDirectSaleEvidence(
                any(Order.class), any(OrderItem.class), eq(false), eq(8L));
        verify(inventoryService, never()).outbound(any(), any());
    }

    @Test
    void createPosOrderKeepsLegacyOutboundWhenSkuNotActivated() {
        POSOrderCreateCommand command = cashPosCommand(false);
        stubCashPosOrderPersistence();

        Long orderId = orderService.createPOSOrder(command, 8L);

        assertEquals(500L, orderId);
        verify(pickupFulfillmentService).recordDirectSaleEvidence(
                any(Order.class), any(OrderItem.class), eq(false), eq(8L));
        verify(inventoryService).outbound(any(), eq(8L));
    }

    @Test
    void createPosOrderForwardsWarehousePickupChoiceToNewChain() {
        POSOrderCreateCommand command = cashPosCommand(true);
        stubCashPosOrderPersistence();
        when(pickupFulfillmentService.recordDirectSaleEvidence(
                any(Order.class), any(OrderItem.class), eq(true), eq(8L))).thenReturn(true);

        Long orderId = orderService.createPOSOrder(command, 8L);

        assertEquals(500L, orderId);
        verify(pickupFulfillmentService).recordDirectSaleEvidence(
                any(Order.class), any(OrderItem.class), eq(true), eq(8L));
        verify(inventoryService, never()).outbound(any(), any());
    }

    @Test
    void createPosOrderRecordsManualPriceChangeAfterItemSaveAndKeepsSubmittedPrice() {
        POSOrderCreateCommand command = cashPosCommand(false);
        stubCashPosOrderPersistence();
        PriceQuote quote = new PriceQuote("quote-pos-1", null, 99L, 3L, "POS", null, null, "袋", 11L,
                LocalDate.now(), new BigDecimal("90.00"), "v1", new BigDecimal("80.00"), false, false);
        when(productPricingService.quote(any(PriceContext.class))).thenReturn(quote);

        Long orderId = orderService.createPOSOrder(command, 8L);

        assertEquals(500L, orderId);
        ArgumentCaptor<PriceQuote> quoteCaptor = ArgumentCaptor.forClass(PriceQuote.class);
        ArgumentCaptor<BigDecimal> priceCaptor = ArgumentCaptor.forClass(BigDecimal.class);
        ArgumentCaptor<String> reasonCaptor = ArgumentCaptor.forClass(String.class);
        InOrder inOrder = inOrder(itemRepository, manualPriceAuditService);
        inOrder.verify(itemRepository).save(any(OrderItem.class));
        inOrder.verify(manualPriceAuditService).record(quoteCaptor.capture(), priceCaptor.capture(),
                reasonCaptor.capture(), eq(8L), eq(8L));

        assertEquals(900L, quoteCaptor.getValue().orderItemId());
        assertEquals("quote-pos-1", quoteCaptor.getValue().quoteId());
        assertEquals(0, priceCaptor.getValue().compareTo(new BigDecimal("100.00")));
        assertEquals("POS手动改价", reasonCaptor.getValue());

        ArgumentCaptor<OrderItem> itemCaptor = ArgumentCaptor.forClass(OrderItem.class);
        verify(itemRepository).save(itemCaptor.capture());
        assertEquals(0, itemCaptor.getValue().getUnitPrice().compareTo(new BigDecimal("100.00")));
    }

    private POSOrderCreateCommand cashPosCommand(boolean warehousePickup) {
        POSOrderCreateCommand command = new POSOrderCreateCommand();
        command.setStaffId(8L);
        command.setPaymentType(1);
        OrderItemDTO item = new OrderItemDTO();
        item.setProductId(99L);
        item.setQuantity(BigDecimal.ONE);
        item.setUnit("袋");
        item.setUnitPrice(new BigDecimal("100.00"));
        item.setWarehousePickup(warehousePickup);
        command.setItems(Collections.singletonList(item));
        return command;
    }

    private void stubCashPosOrderPersistence() {
        Staff staff = new Staff();
        staff.setId(8L);
        staff.setStoreId(3L);
        Product product = new Product();
        product.setId(99L);
        product.setName("复合肥");
        product.setMainUnit("袋");
        product.setAvgCostPrice(new BigDecimal("40.00"));
        product.setAllowSpecialFund(0);

        when(staffRepository.findById(8L)).thenReturn(Optional.of(staff));
        when(orderDomainService.generateOrderNo()).thenReturn("SO202609120001");
        when(productRepository.findById(99L)).thenReturn(Optional.of(product));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(500L);
            }
            return saved;
        });
        when(itemRepository.save(any(OrderItem.class))).thenAnswer(invocation -> {
            OrderItem saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(900L);
            }
            return saved;
        });
        when(paymentRepository.findByOrderId(500L)).thenReturn(Collections.emptyList());
    }

    private Order mockPendingClientOrder() {
        Order order = new Order();
        order.setId(1L);
        order.setOrderNo("SO202604110001");
        order.setStoreId(3L);
        order.setCustomerId(12L);
        order.setOrderType(2);
        order.setOrderStatus(1);
        order.setPayAmount(new BigDecimal("88.50"));
        return order;
    }

    private OrderPayPreviewVO preview(BigDecimal externalAmount) {
        OrderPayPreviewVO preview = new OrderPayPreviewVO();
        preview.setExternalAmount(externalAmount);
        return preview;
    }

    private OrderPaymentIntent mockPendingIntent(Order order) {
        OrderPaymentIntent intent = new OrderPaymentIntent();
        intent.setIntentNo("PI202604111030001");
        intent.setOrderId(order.getId());
        intent.setCustomerId(order.getCustomerId());
        intent.setExternalPaymentType(2);
        intent.setExternalAmount(new BigDecimal("50.00"));
        intent.setStatus(1);
        intent.setExpireAt(LocalDateTime.now().plusMinutes(10));
        return intent;
    }
}
