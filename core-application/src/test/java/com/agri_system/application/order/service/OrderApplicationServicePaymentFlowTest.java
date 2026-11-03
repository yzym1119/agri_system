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
class OrderApplicationServicePaymentFlowTest {

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

    @org.junit.jupiter.api.BeforeEach
    void existingCustomerForPosWithWalletOrCredit() {
        var customer=new com.agri_system.domain.customer.model.Customer();customer.setId(12L);customer.setStatus(1);
        org.mockito.Mockito.lenient().when(customerRepository.findByIdWithLock(any())).thenAnswer(i->{var current=new com.agri_system.domain.customer.model.Customer();current.setId(i.getArgument(0));current.setStatus(1);return Optional.of(current);});
    }

    @Test
    void payOrderReturnsPreviewWhenExternalPaymentStillRequired() {
        Order order = mockPendingClientOrder();
        OrderPayPreviewVO preview = preview(new BigDecimal("30.00"));
        OrderPayCommand command = new OrderPayCommand();
        command.setOrderId(order.getId());

        when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
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
        item.setId(77L);
        item.setProductId(9L);
        item.setQuantity(new BigDecimal("2"));
        item.setUnit("袋");

        OrderPayPreviewVO preview = preview(new BigDecimal("50.00"));
        OrderExternalPaymentConfirmCommand command = new OrderExternalPaymentConfirmCommand();
        command.setOrderId(order.getId());
        command.setIntentNo(intent.getIntentNo());
        command.setExternalTransactionNo("wx202604110001");
        command.setExternalPaidAt(LocalDateTime.of(2026, 4, 11, 10, 30));

        when(paymentIntentRepository.findByIntentNo(intent.getIntentNo())).thenReturn(Optional.of(intent));
        when(paymentService.previewPayment(order, null, null, null)).thenReturn(preview);
        when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
        when(itemRepository.findByOrderId(order.getId())).thenReturn(Collections.singletonList(item));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentIntentRepository.save(any(OrderPaymentIntent.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var actualExternal=new com.agri_system.domain.order.model.OrderPayment();actualExternal.setPaymentType(2);actualExternal.setAmount(new BigDecimal("50.00"));
        when(paymentService.executePayment(any(),any(),any(),any(),any(),anyBoolean(),any(),any(),any())).thenReturn(java.util.List.of(actualExternal));
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
        verify(inventoryService).outbound(argThat(stock -> stock.getReferenceLineId().equals(77L)
                && stock.getReferenceId().equals(order.getId())
                && stock.getStoreId().equals(order.getStoreId())
                && stock.getUnit().equals("袋")
                && stock.getQuantity().compareTo(new BigDecimal("2")) == 0), isNull());
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
        command.setOrderId(order.getId());
        command.setIntentNo(intent.getIntentNo());
        command.setExternalTransactionNo("wx202604110002");

        when(paymentIntentRepository.findByIntentNo(intent.getIntentNo())).thenReturn(Optional.of(intent));
        when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
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
        command.setOrderId(order.getId());
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
    void confirmExternalPaymentRejectsAnotherPathOrderBeforePaymentOrReplay() {
        ReflectionTestUtils.setField(orderService, "clientPaymentConfirmEnabled", true);
        Order order = mockPendingClientOrder();
        OrderPaymentIntent intent = mockPendingIntent(order);
        intent.setStatus(2);
        intent.setExternalTransactionNo("already-confirmed");
        when(paymentIntentRepository.findByIntentNo(intent.getIntentNo())).thenReturn(Optional.of(intent));
        OrderExternalPaymentConfirmCommand command = new OrderExternalPaymentConfirmCommand();
        command.setOrderId(order.getId() + 1);
        command.setIntentNo(intent.getIntentNo());
        command.setExternalTransactionNo("already-confirmed");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> orderService.confirmExternalPayment(command, order.getCustomerId()));
        org.junit.jupiter.api.Assertions.assertTrue(error.getMessage().contains("订单"));
        org.mockito.Mockito.verifyNoInteractions(orderRepository, paymentService, inventoryService);
    }

    @Test
    void confirmExternalPaymentRequiresPathOrderEvenForAlreadyConfirmedIntent() {
        ReflectionTestUtils.setField(orderService, "clientPaymentConfirmEnabled", true);
        Order order = mockPendingClientOrder();
        OrderPaymentIntent intent = mockPendingIntent(order);
        intent.setStatus(2);
        intent.setExternalTransactionNo("already-confirmed");
        when(paymentIntentRepository.findByIntentNo(intent.getIntentNo())).thenReturn(Optional.of(intent));
        OrderExternalPaymentConfirmCommand command = new OrderExternalPaymentConfirmCommand();
        command.setIntentNo(intent.getIntentNo());
        command.setExternalTransactionNo("already-confirmed");

        assertThrows(IllegalArgumentException.class,
                () -> orderService.confirmExternalPayment(command, order.getCustomerId()));
        org.mockito.Mockito.verifyNoInteractions(orderRepository, paymentService, inventoryService);
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

        org.mockito.Mockito.lenient().when(productPricingService.quote(any(PriceContext.class)))
                .thenReturn(new PriceQuote("normal", null, 99L, 3L, "POS", 1, null, "袋", 11L,
                        LocalDate.now(), new BigDecimal("100.00"), "RETAIL", null, false, false));
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
                () -> com.agri_system.application.order.service.PosOrderTestFixture.withPos(orderService).createPOSOrder(command, 8L));

        assertEquals("ORDER_AMOUNT_INVALID: 钱包可支付金额不足，当前最多可支付 ¥50.00", ex.getMessage());
        verify(walletService, never()).deductSpecialFund(any(), any(), any(), any(), any(), any());
        verify(walletService, never()).deductBalance(any(), any(), any(), any(), any(), any());
        verify(inventoryService, never()).outbound(any(), any());
    }

    @Test
    void createPosOrderDeductsInventoryWithPersistedOrderLine() {
        POSOrderCreateCommand command = cashPosCommand(false);
        stubCashPosOrderPersistence();
        assertEquals(500L, com.agri_system.application.order.service.PosOrderTestFixture.withPos(orderService).createPOSOrder(command, 8L));
        assertSaleOutbound();
    }

    @Test
    void createPosOrderWarehousePickupAlsoDeductsInventoryAtCheckout() {
        POSOrderCreateCommand command = cashPosCommand(true);
        stubCashPosOrderPersistence();
        assertEquals(500L, com.agri_system.application.order.service.PosOrderTestFixture.withPos(orderService).createPOSOrder(command, 8L));
        assertSaleOutbound();
    }

    @Test
    void createPosOrderPropagatesInsufficientInventoryInsteadOfCompletingSale() {
        stubCashPosOrderPersistence();
        org.mockito.Mockito.doThrow(new IllegalArgumentException("库存不足"))
                .when(inventoryService).outbound(any(), eq(8L));
        assertEquals("库存不足", assertThrows(IllegalArgumentException.class,
                () -> com.agri_system.application.order.service.PosOrderTestFixture.withPos(orderService).createPOSOrder(cashPosCommand(false), 8L)).getMessage());
        verify(eventPublisher, never()).publishEvent(any(OrderFinishedEvent.class));
    }

    @Test
    void quoteFailureCannotFallBackToSubmittedPrice() {
        Staff staff = new Staff(); staff.setId(8L); staff.setStoreId(3L);
        Product product = new Product(); product.setId(99L); product.setMainUnit("袋");
        when(staffRepository.findById(8L)).thenReturn(Optional.of(staff));
        when(productRepository.findById(99L)).thenReturn(Optional.of(product));
        when(orderDomainService.generateOrderNo()).thenReturn("FAILED-QUOTE");
        when(productPricingService.quote(any(PriceContext.class)))
                .thenThrow(new IllegalArgumentException("商品未配置零售价"));
        assertEquals("商品未配置零售价", assertThrows(IllegalArgumentException.class,
                () -> com.agri_system.application.order.service.PosOrderTestFixture.withPos(orderService).createPOSOrder(cashPosCommand(false), 8L)).getMessage());
        verify(itemRepository, never()).save(any());
        verify(inventoryService, never()).outbound(any(), any());
    }

    @Test
    @org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
    void wholeOrderDiscountRequiresReasonBeforeSavingOrder() {
        stubCashPosOrderPersistence();
        ReflectionTestUtils.setField(orderService, "manualPriceAuditService",
                new ManualPriceAuditService(org.mockito.Mockito.mock(com.agri_system.domain.order.repository.OrderItemPriceAuditRepository.class)));
        POSOrderCreateCommand command = cashPosCommand(false);
        command.setDiscountAmount(new BigDecimal("9.00"));
        assertThrows(IllegalArgumentException.class, () -> com.agri_system.application.order.service.PosOrderTestFixture.withPos(orderService).createPOSOrder(command, 8L));
        verify(orderRepository, never()).save(any());
    }

    @Test
    @org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
    void staleDisplayedQuoteCannotSilentlyChangeCheckoutAmount() {
        stubCashPosOrderPersistence();
        POSOrderCreateCommand command = cashPosCommand(false);
        command.getItems().get(0).setQuotedUnitPrice(new BigDecimal("90.00"));
        assertThrows(IllegalArgumentException.class, () -> com.agri_system.application.order.service.PosOrderTestFixture.withPos(orderService).createPOSOrder(command, 8L));
        verify(orderRepository, never()).save(any());
        verify(inventoryService, never()).outbound(any(), any());
    }

    @Test
    @org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
    void unsupportedPackageCannotBeSoldAsIndividuallyPricedLines() {
        stubCashPosOrderPersistence();
        POSOrderCreateCommand command = cashPosCommand(false);
        command.getItems().get(0).setPackageId(7L);
        assertThrows(IllegalArgumentException.class, () -> com.agri_system.application.order.service.PosOrderTestFixture.withPos(orderService).createPOSOrder(command, 8L));
        verify(orderRepository, never()).save(any());
    }

    @Test
    void fractionalSaleRoundsLineMoneyBeforeCheckout() {
        stubCashPosOrderPersistence();
        POSOrderCreateCommand command = cashPosCommand(false);
        command.getItems().get(0).setQuantity(new BigDecimal("0.3333"));
        command.setReceiveAmount(new BigDecimal("33.33"));
        com.agri_system.application.order.service.PosOrderTestFixture.withPos(orderService).createPOSOrder(command, 8L);
        ArgumentCaptor<OrderItem> item = ArgumentCaptor.forClass(OrderItem.class);
        verify(itemRepository).save(item.capture());
        assertEquals(new BigDecimal("33.33"), item.getValue().getTotalPrice());
    }

    @Test
    void packageSaleUsesPackageTotalAndPersistsCompositionSnapshot() {
        stubCashPosOrderPersistence();
        var command = cashPosCommand(false);
        var input = command.getItems().get(0); input.setProductId(null); input.setItemType(2); input.setPackageId(7L);
        input.setQuantity(new BigDecimal("2")); input.setUnit("套"); input.setUnitPrice(null);
        input.setQuotedUnitPrice(new BigDecimal("80.00")); input.setPackageQuoteId("package-content");
        var material = new com.agri_system.application.productpackage.dto.vo.PackagePriceQuote.Item(
                99L,"复合肥",new BigDecimal("14"),"袋",11L,new BigDecimal("11.428571428571"),
                new BigDecimal("160.00"),0,new BigDecimal("11.428571428571"));
        var quote = new com.agri_system.application.productpackage.dto.vo.PackagePriceQuote("package-content",7L,"独立定价套餐",
                new BigDecimal("2"),new BigDecimal("80.00"),new BigDecimal("60.00"),false,new BigDecimal("80.00"),
                new BigDecimal("160.00"),BigDecimal.ZERO,java.util.List.of(material));
        when(packagePricingService.quoteForSale(eq(7L),isNull(),eq(3L),eq(new BigDecimal("2")),isNull())).thenReturn(quote);
        com.agri_system.application.order.service.PosOrderTestFixture.withPos(orderService).createPOSOrder(command,8L);
        ArgumentCaptor<OrderItem> saved = ArgumentCaptor.forClass(OrderItem.class); verify(itemRepository).save(saved.capture());
        assertEquals(new BigDecimal("160.00"),saved.getValue().getTotalPrice());
        assertEquals(new BigDecimal("14"),saved.getValue().getQuantity());
        assertEquals(7L,saved.getValue().getPackageId()); assertEquals(1,saved.getValue().getPackageGroupNo());
        assertEquals("独立定价套餐",saved.getValue().getPackageName());
        assertEquals(new BigDecimal("2"),saved.getValue().getPackageQuantity());
        assertEquals(new BigDecimal("80.00"),saved.getValue().getPackageUnitPrice());
    }

    @Test
    void packageAndProductMixedSaleKeepPickupFlagsOnExpandedGoods() {
        stubCashPosOrderPersistence();
        var command = cashPosCommand(true);
        var pack = command.getItems().get(0);
        pack.setProductId(null); pack.setItemType(2); pack.setPackageId(7L);
        pack.setQuantity(new BigDecimal("2")); pack.setUnitPrice(null); pack.setPackageQuoteId("mixed");
        var standalone = new OrderItemDTO();
        standalone.setProductId(99L); standalone.setQuantity(BigDecimal.ONE); standalone.setUnit("袋");
        standalone.setWarehousePickup(false);
        command.setItems(java.util.List.of(pack, standalone));
        Product second = new Product(); second.setId(98L); second.setName("配套农药");
        second.setMainUnit("瓶"); second.setAllowSpecialFund(0);
        when(productRepository.findById(98L)).thenReturn(Optional.of(second));
        var material1 = new com.agri_system.application.productpackage.dto.vo.PackagePriceQuote.Item(
                99L,"复合肥",new BigDecimal("14"),"袋",11L,new BigDecimal("6.857142857143"),
                new BigDecimal("96.00"),0,new BigDecimal("6.857142857143"));
        var material2 = new com.agri_system.application.productpackage.dto.vo.PackagePriceQuote.Item(
                98L,"配套农药",new BigDecimal("2"),"瓶",12L,new BigDecimal("32.00"),
                new BigDecimal("64.00"),0,new BigDecimal("32.00"));
        var quote = new com.agri_system.application.productpackage.dto.vo.PackagePriceQuote("mixed",7L,"套餐",
                new BigDecimal("2"),new BigDecimal("80.00"),null,false,new BigDecimal("80.00"),
                new BigDecimal("160.00"),BigDecimal.ZERO,java.util.List.of(material1,material2));
        when(packagePricingService.quoteForSale(eq(7L),isNull(),eq(3L),eq(new BigDecimal("2")),isNull())).thenReturn(quote);

        com.agri_system.application.order.service.PosOrderTestFixture.withPos(orderService).createPOSOrder(command,8L);

        verify(inventoryService, org.mockito.Mockito.times(3)).outbound(any(),eq(8L));
        verify(pickupFulfillmentService).createRequest(500L,900L,null);
        verify(pickupFulfillmentService).createRequest(500L,901L,null);
        verify(pickupFulfillmentService,never()).createRequest(500L,902L,null);
        ArgumentCaptor<Order> saved = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository, org.mockito.Mockito.atLeastOnce()).save(saved.capture());
        assertEquals(new BigDecimal("260.00"),saved.getValue().getPayAmount());
    }

    @Test
    @org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
    void couponAndBalanceChargeNetAmountOnlyOnce() {
        var facts = couponPosFixture("10.00");
        var command = couponPosCommand(4);
        command.setWalletAuthCode("TEST90");
        com.agri_system.application.order.service.PosOrderTestFixture.withPos(orderService).createPOSOrder(command, 8L);
        assertEquals(new BigDecimal("110.00"), facts.wallet().get(), "100元商品减10元券只能扣90元");
        assertPaymentTotal(facts.payments(), 4, "90.00");
        assertPaymentTotal(facts.payments(), 5, "10.00");
        assertEquals(2, facts.coupon().getStatus());
        assertEquals(500L, facts.coupon().getUsedOrderId());
    }

    @Test
    @org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
    void couponAndCashRecordCashNetAmount() {
        var facts = couponPosFixture("10.00");
        com.agri_system.application.order.service.PosOrderTestFixture.withPos(orderService).createPOSOrder(couponPosCommand(1), 8L);
        assertPaymentTotal(facts.payments(), 1, "90.00");
        assertPaymentTotal(facts.payments(), 5, "10.00");
        assertEquals(new BigDecimal("200.00"), facts.wallet().get());
    }

    @Test
    @org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
    void couponAndMixedCashWechatChargeAllocatedNetAmount() {
        var facts = couponPosFixture("10.00");
        var command = couponPosCommand(10);
        command.setPayments(java.util.List.of(paymentDetail(1, "40.00"), paymentDetail(2, "50.00")));
        com.agri_system.application.order.service.PosOrderTestFixture.withPos(orderService).createPOSOrder(command, 8L);
        assertPaymentTotal(facts.payments(), 1, "40.00");
        assertPaymentTotal(facts.payments(), 2, "50.00");
        assertPaymentTotal(facts.payments(), 5, "10.00");
        assertEquals(3, facts.payments().size());
    }

    @Test
    @org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
    void fullCouponDoesNotChargeWalletOrRequireCode() {
        var facts = couponPosFixture("100.00");
        com.agri_system.application.order.service.PosOrderTestFixture.withPos(orderService).createPOSOrder(couponPosCommand(4), 8L);
        assertEquals(new BigDecimal("200.00"), facts.wallet().get());
        assertPaymentTotal(facts.payments(), 5, "100.00");
        assertEquals(1, facts.payments().size());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {5, 8, 10, 999})
    @org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
    void mixedPaymentRejectsTypesThatCannotBeSubmittedAsRealTender(int type) {
        stubCashPosOrderPersistence();
        var command = cashPosCommand(false); command.setPaymentType(10);
        command.setPayments(java.util.List.of(paymentDetail(type, "100.00")));
        assertThrows(IllegalArgumentException.class, () -> com.agri_system.application.order.service.PosOrderTestFixture.withPos(orderService).createPOSOrder(command, 8L));
    }

    @Test
    @org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
    void mixedPaymentRejectsNegativeEntryInsteadOfDroppingItAfterSummation() {
        stubCashPosOrderPersistence();
        var command = cashPosCommand(false); command.setPaymentType(10);
        command.setPayments(java.util.List.of(paymentDetail(1, "150.00"), paymentDetail(6, "-50.00")));
        assertThrows(IllegalArgumentException.class, () -> com.agri_system.application.order.service.PosOrderTestFixture.withPos(orderService).createPOSOrder(command, 8L));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"0", "0.001"})
    @org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
    void mixedPaymentRequiresEachEntryPositiveAndExactlyExpressibleInCents(String invalid) {
        stubCashPosOrderPersistence();
        var command = cashPosCommand(false); command.setPaymentType(10);
        command.setPayments(java.util.List.of(paymentDetail(1, new BigDecimal("100.00").subtract(new BigDecimal(invalid)).toPlainString()),
                paymentDetail(2, invalid)));
        assertThrows(IllegalArgumentException.class, () -> com.agri_system.application.order.service.PosOrderTestFixture.withPos(orderService).createPOSOrder(command, 8L));
    }

    @Test
    @org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
    void walletTenderRecordsOnlySpecialFundThatWasActuallyAvailable() {
        var facts = couponPosFixture("10.00");
        var fund = new java.util.concurrent.atomic.AtomicReference<>(new BigDecimal("5.00"));
        when(walletService.getSpecialFundBalance(12L)).thenAnswer(invocation -> fund.get());
        when(orderDomainService.calculateMaxSpecialFundAmount(any())).thenReturn(new BigDecimal("20.00"));
        org.mockito.Mockito.doAnswer(invocation -> {
            fund.updateAndGet(amount -> amount.subtract(invocation.getArgument(1))); return null;
        }).when(walletService).deductSpecialFund(eq(12L),any(),any(),any(),any(),any());
        var command = couponPosCommand(4); command.setUseSpecialFund(true); command.setWalletAuthCode("TEST85");
        com.agri_system.application.order.service.PosOrderTestFixture.withPos(orderService).createPOSOrder(command,8L);
        assertPaymentTotal(facts.payments(),8,"5.00");
        assertPaymentTotal(facts.payments(),4,"85.00");
        assertEquals(new BigDecimal("115.00"),facts.wallet().get());
        assertEquals(new BigDecimal("0.00"),fund.get());
    }

    @Test
    @org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
    void mixedWalletTenderRequiresAuthorizationBeforeDeduction() {
        var facts = couponPosFixture("10.00");
        var command = couponPosCommand(10);
        command.setPayments(java.util.List.of(paymentDetail(1,"40.00"),paymentDetail(4,"50.00")));
        assertThrows(IllegalArgumentException.class,()->com.agri_system.application.order.service.PosOrderTestFixture.withPos(orderService).createPOSOrder(command,8L));
        assertEquals(new BigDecimal("200.00"),facts.wallet().get());
    }

    @Test
    @org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
    void mixedWalletTenderRejectsExpiredUsedOrWrongAmountAuthorization() {
        var facts = couponPosFixture("10.00");
        when(walletAuthCodeRepository.consumeAuthCode(eq(12L),any(),any(),eq(500L))).thenReturn(0);
        var command = couponPosCommand(10); command.setWalletAuthCode("EXPIRED");
        command.setPayments(java.util.List.of(paymentDetail(1,"40.00"),paymentDetail(4,"50.00")));
        assertThrows(IllegalStateException.class,()->com.agri_system.application.order.service.PosOrderTestFixture.withPos(orderService).createPOSOrder(command,8L));
        assertEquals(new BigDecimal("200.00"),facts.wallet().get());
    }

    @Test
    @org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
    void mixedWalletTenderConsumesAuthorizationExactlyOnce() {
        var facts = couponPosFixture("10.00");
        var command = couponPosCommand(10); command.setWalletAuthCode("TEST50");
        command.setPayments(java.util.List.of(paymentDetail(1,"40.00"),paymentDetail(4,"50.00")));
        com.agri_system.application.order.service.PosOrderTestFixture.withPos(orderService).createPOSOrder(command,8L);
        assertEquals(new BigDecimal("150.00"),facts.wallet().get());
        assertPaymentTotal(facts.payments(),4,"50.00");
        assertEquals(1,facts.authUses().get());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"owner", "expired", "not_started", "minimum", "category", "product"})
    @org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
    void selectedCouponMustSatisfySameEligibilityAsPreview(String invalid) {
        var facts = couponPosFixture("10.00");
        switch (invalid) {
            case "owner" -> facts.coupon().setCustomerId(13L);
            case "expired" -> facts.coupon().setEndTime(LocalDateTime.now().minusMinutes(1));
            case "not_started" -> facts.coupon().setStartTime(LocalDateTime.now().plusDays(1));
            case "minimum" -> facts.coupon().setMinOrderAmount(new BigDecimal("200.00"));
            case "category" -> facts.coupon().setLimitCategoryId(999L);
            case "product" -> facts.coupon().setLimitProductId(999L);
        }
        assertThrows(IllegalArgumentException.class,()->com.agri_system.application.order.service.PosOrderTestFixture.withPos(orderService).createPOSOrder(couponPosCommand(1),8L));
        assertEquals(1,facts.coupon().getStatus());
        assertEquals(0,facts.payments().size());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"plain", "coupon", "discount", "details"})
    @org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
    void pureCreditPreservesNetSettlementAndAllowsExistingDiscounts(String kind) {
        var facts = couponPosFixture("10.00");
        var debt = new java.util.concurrent.atomic.AtomicReference<>(BigDecimal.ZERO);
        org.mockito.Mockito.doAnswer(invocation -> { debt.updateAndGet(v->v.add(invocation.getArgument(2))); return null; })
                .when(creditService).addDebt(eq(12L),eq(500L),any());
        var command = couponPosCommand(6);
        if (!"coupon".equals(kind)) command.setCouponId(null);
        if ("discount".equals(kind)) { command.setDiscountAmount(new BigDecimal("10.00")); command.setRemark("破损包装优惠"); }
        if ("details".equals(kind)) command.setPayments(java.util.List.of(paymentDetail(6,"100.00")));
        com.agri_system.application.order.service.PosOrderTestFixture.withPos(orderService).createPOSOrder(command,8L);
        var orders = ArgumentCaptor.forClass(Order.class); verify(orderRepository,org.mockito.Mockito.atLeastOnce()).save(orders.capture());
        BigDecimal expected = new BigDecimal("coupon".equals(kind) || "discount".equals(kind) ? "90.00" : "100.00");
        assertEquals(expected,orders.getValue().getPayAmount());
        assertEquals(expected,debt.get());
        assertPaymentTotal(facts.payments(),6,expected.toPlainString());
        assertEquals(new BigDecimal("200.00"),facts.wallet().get());
        InOrder locks=org.mockito.Mockito.inOrder(customerRepository,itemRepository,creditService,inventoryService);
        locks.verify(customerRepository).findByIdWithLock(12L);
        // 新单应收尚未发布：先防止订单行FK共享锁升级环，客户锁仍隔离本客户旧账户竞争。
        locks.verify(inventoryService).lockProducts(eq(3L),any());
        locks.verify(itemRepository).save(any());
        locks.verify(creditService).addDebt(12L,500L,expected);
        locks.verify(inventoryService).outbound(any(),eq(8L));

    }

    @Test
    @org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
    void fullCouponCreditSaleDoesNotCreateZeroValueDebtAccount() {
        var facts=couponPosFixture("100.00");
        org.mockito.Mockito.doAnswer(invocation->{if(((BigDecimal)invocation.getArgument(2)).signum()<=0) throw new IllegalArgumentException("新增欠款必须为正");return null;})
                .when(creditService).addDebt(any(),any(),any());
        var command=couponPosCommand(6);
        com.agri_system.application.order.service.PosOrderTestFixture.withPos(orderService).createPOSOrder(command,8L);
        assertPaymentTotal(facts.payments(),5,"100.00");assertEquals(1,facts.payments().size());
    }

    private record CouponPosFacts(com.agri_system.domain.coupon.model.CustomerCoupon coupon,
                                  java.util.List<com.agri_system.domain.order.model.OrderPayment> payments,
                                  java.util.concurrent.atomic.AtomicReference<BigDecimal> wallet,
                                  java.util.concurrent.atomic.AtomicInteger authUses) {}

    /** 真实订单及支付服务，仅外部存储用可观察状态替身。 */
    private CouponPosFacts couponPosFixture(String value) {
        stubCashPosOrderPersistence();
        var payments = new java.util.ArrayList<com.agri_system.domain.order.model.OrderPayment>();
        var persistedItems = new java.util.ArrayList<OrderItem>();
        when(paymentRepository.findByOrderId(500L)).thenAnswer(invocation -> new java.util.ArrayList<>(payments));
        when(paymentRepository.save(any())).thenAnswer(invocation -> {
            com.agri_system.domain.order.model.OrderPayment payment = invocation.getArgument(0);
            payments.add(payment); return payment;
        });
        when(itemRepository.save(any())).thenAnswer(invocation -> {
            OrderItem item = invocation.getArgument(0); item.setId(900L); persistedItems.add(item); return item;
        });
        when(itemRepository.findByOrderId(500L)).thenAnswer(invocation -> persistedItems);
        var coupon = new com.agri_system.domain.coupon.model.CustomerCoupon();
        coupon.setId(81L); coupon.setCustomerId(12L); coupon.setType(1);
        coupon.setAmount(new BigDecimal(value)); coupon.setStatus(1);
        coupon.setStartTime(LocalDateTime.now().minusDays(1)); coupon.setEndTime(LocalDateTime.now().plusDays(1));
        when(couponRepository.findById(81L)).thenReturn(Optional.of(coupon));
        when(couponRepository.findByIdForUpdate(81L)).thenReturn(Optional.of(coupon));
        var wallet = new java.util.concurrent.atomic.AtomicReference<>(new BigDecimal("200.00"));
        when(walletService.getBalance(12L)).thenAnswer(invocation -> wallet.get());
        when(walletService.getSpecialFundBalance(12L)).thenReturn(BigDecimal.ZERO);
        when(orderDomainService.calculateMaxSpecialFundAmount(any())).thenReturn(BigDecimal.ZERO);
        org.mockito.Mockito.doAnswer(invocation -> {
            wallet.updateAndGet(amount -> amount.subtract(invocation.getArgument(1))); return null;
        }).when(walletService).deductBalance(eq(12L),any(),any(),any(),any(),any());
        var authUses = new java.util.concurrent.atomic.AtomicInteger();
        when(walletAuthCodeRepository.consumeAuthCode(eq(12L), any(), any(), eq(500L))).thenAnswer(invocation -> {
            authUses.incrementAndGet(); return 1;
        });
        ReflectionTestUtils.setField(orderService, "paymentService", new OrderPaymentService(paymentRepository,
                couponRepository, walletService, orderDomainService, itemRepository, productRepository,
                orderRepository, customerRepository,
                org.mockito.Mockito.mock(com.agri_system.domain.credit.repository.CreditAccountRepository.class),
                org.mockito.Mockito.mock(com.agri_system.domain.credit.repository.CreditRepaymentLogRepository.class), afterSalesRepository, afterSalesItemRepository));
        return new CouponPosFacts(coupon, payments, wallet, authUses);
    }

    private POSOrderCreateCommand couponPosCommand(int paymentType) {
        var command = cashPosCommand(false); command.setCustomerId(12L); command.setCouponId(81L);
        command.setPaymentType(paymentType); command.setReceiveAmount(new BigDecimal("90.00")); return command;
    }

    private PaymentDetailDTO paymentDetail(int type, String amount) {
        var detail = new PaymentDetailDTO(); detail.setPaymentType(type); detail.setAmount(new BigDecimal(amount)); return detail;
    }

    private void assertPaymentTotal(java.util.List<com.agri_system.domain.order.model.OrderPayment> payments, int type, String expected) {
        assertEquals(new BigDecimal(expected), payments.stream().filter(p -> Integer.valueOf(type).equals(p.getPaymentType()))
                .map(com.agri_system.domain.order.model.OrderPayment::getAmount).reduce(BigDecimal.ZERO,BigDecimal::add));
    }

    private void assertSaleOutbound() {
        ArgumentCaptor<com.agri_system.application.inventory.dto.command.InventoryOutCommand> captor =
                ArgumentCaptor.forClass(com.agri_system.application.inventory.dto.command.InventoryOutCommand.class);
        verify(inventoryService).outbound(captor.capture(), eq(8L));
        assertEquals(500L, captor.getValue().getReferenceId());
        assertEquals(900L, captor.getValue().getReferenceLineId());
        assertEquals("ORDER", captor.getValue().getReferenceType());
        assertEquals(99L, captor.getValue().getProductId());
        assertEquals(3L, captor.getValue().getStoreId());
        assertEquals("袋", captor.getValue().getUnit());
        assertEquals(0, captor.getValue().getQuantity().compareTo(BigDecimal.ONE));
    }

    @Test
    void createPosOrderRecordsManualPriceChangeAfterItemSaveAndKeepsSubmittedPrice() {
        POSOrderCreateCommand command = cashPosCommand(false);
        stubCashPosOrderPersistence();
        command.getItems().get(0).setPriceChangeReason("老客户议价");
        PriceQuote quote = new PriceQuote("quote-pos-1", null, 99L, 3L, "POS", null, null, "袋", 11L,
                LocalDate.now(), new BigDecimal("90.00"), "v1", new BigDecimal("80.00"), false, false);
        when(productPricingService.quote(any(PriceContext.class))).thenReturn(quote);

        Long orderId = com.agri_system.application.order.service.PosOrderTestFixture.withPos(orderService).createPOSOrder(command, 8L);

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
        assertEquals("老客户议价", reasonCaptor.getValue());

        ArgumentCaptor<OrderItem> itemCaptor = ArgumentCaptor.forClass(OrderItem.class);
        verify(itemRepository).save(itemCaptor.capture());
        assertEquals(0, itemCaptor.getValue().getUnitPrice().compareTo(new BigDecimal("100.00")));
    }

    private POSOrderCreateCommand deliveryPosCommand(boolean warehouse) throws Exception {
        var command=cashPosCommand(warehouse);
        var json=new com.fasterxml.jackson.databind.ObjectMapper()
                .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,false);
        var node=json.valueToTree(command);
        ((com.fasterxml.jackson.databind.node.ObjectNode)node).put("deliveryType",2)
                .put("receiverName","张三").put("receiverPhone","13800000000").put("receiverAddress","东街3号");
        return json.treeToValue(node,POSOrderCreateCommand.class);
    }

    @Test void posDeliveryRejectsMissingReceiverBeforeAnyBusinessWrites() throws Exception {
        var command=deliveryPosCommand(false);
        var json=new com.fasterxml.jackson.databind.ObjectMapper()
                .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,false);
        var node=(com.fasterxml.jackson.databind.node.ObjectNode)json.valueToTree(command);
        node.put("receiverPhone"," ");command=json.treeToValue(node,POSOrderCreateCommand.class);
        final var submitted=command;
        var error=assertThrows(IllegalArgumentException.class,()->com.agri_system.application.order.service.PosOrderTestFixture.withPos(orderService).createPOSOrder(submitted,8L));
        org.junit.jupiter.api.Assertions.assertTrue(error.getMessage().contains("收货"));
        verifyNoBusinessWrites();
    }

    @Test void posDeliveryCreatesWaitingDeliveryWhileSettlingSaleExactlyOnce() throws Exception {
        stubCashPosOrderPersistence();
        com.agri_system.application.order.service.PosOrderTestFixture.withPos(orderService).createPOSOrder(deliveryPosCommand(true),8L);
        var orders=ArgumentCaptor.forClass(Order.class);verify(orderRepository,org.mockito.Mockito.atLeastOnce()).save(orders.capture());
        assertEquals(1,orders.getValue().getOrderType());assertEquals(4,orders.getValue().getOrderStatus());
        assertEquals("张三",orders.getValue().getReceiverName());
        var delivery=ArgumentCaptor.forClass(com.agri_system.domain.order.model.OrderDelivery.class);
        verify(deliveryRepository).save(delivery.capture());
        assertEquals(500L,delivery.getValue().getOrderId());assertEquals(2,delivery.getValue().getDeliveryType());
        assertEquals(2,delivery.getValue().getDeliveryStatus());
        assertSaleOutbound();verify(eventPublisher).publishEvent(any(OrderFinishedEvent.class));
        verify(pickupFulfillmentService,never()).createRequest(any(),any(),any());
    }

    @Test void defaultPosSaleRemainsImmediateWithoutDeliveryRelation() {
        stubCashPosOrderPersistence();com.agri_system.application.order.service.PosOrderTestFixture.withPos(orderService).createPOSOrder(cashPosCommand(false),8L);
        var orders=ArgumentCaptor.forClass(Order.class);verify(orderRepository,org.mockito.Mockito.atLeastOnce()).save(orders.capture());
        assertEquals(6,orders.getValue().getOrderStatus());verify(deliveryRepository,never()).save(any());
        verify(eventPublisher).publishEvent(any(OrderFinishedEvent.class));
    }

    @Test
    @org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
    void posRejectsSplitBottleBeforeSavingOrderOrPayment() {
        stubCashPosOrderPersistence(); installActualQuantityValidation();
        var command = cashPosCommand(false); command.getItems().get(0).setQuantity(new BigDecimal("0.1"));
        var error = assertThrows(IllegalArgumentException.class, () -> com.agri_system.application.order.service.PosOrderTestFixture.withPos(orderService).createPOSOrder(command, 8L));
        org.junit.jupiter.api.Assertions.assertTrue(error.getMessage().contains("整数")); verifyNoBusinessWrites();
    }

    @Test
    @org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
    void onlineOrderRejectsSplitBottleBeforeSavingUnpaidOrder() {
        stubCashPosOrderPersistence(); installActualQuantityValidation();
        var store = new com.agri_system.domain.store.model.Store(); store.setId(3L); store.setStatus(1); store.setSupportPickup(1);
        when(storeRepository.findById(3L)).thenReturn(Optional.of(store));
        when(productPricingService.resolveOrderItemUnitPrice(any(), any(), any(), any())).thenReturn(new BigDecimal("100"));
        var command = new com.agri_system.application.order.dto.command.OrderCreateCommand();
        command.setStoreId(3L); command.setOrderType(2);
        var item = cashPosCommand(false).getItems().get(0); item.setQuantity(new BigDecimal("0.1")); command.setItems(java.util.List.of(item));
        ReflectionTestUtils.setField(orderService,"onlineOrderCreationService",new OnlineOrderCreationService(
                orderRepository,itemRepository,deliveryRepository,orderDomainService,inventoryService,productRepository,productPricingService,storeRepository,customerRepository));
        var error = assertThrows(IllegalArgumentException.class, () -> orderService.createOrder(command, null));
        org.junit.jupiter.api.Assertions.assertTrue(error.getMessage().contains("整数")); verifyNoBusinessWrites();
    }

    @Test
    @org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
    void packageMaterialRejectsSplitBottleBeforeSavingOrder() {
        stubCashPosOrderPersistence(); installActualQuantityValidation();
        var command = cashPosCommand(false); var input = command.getItems().get(0);
        input.setProductId(null); input.setItemType(2); input.setPackageId(7L); input.setQuantity(BigDecimal.ONE);
        input.setUnit("套"); input.setUnitPrice(null); input.setPackageQuoteId("fractional-package");
        var material = new com.agri_system.application.productpackage.dto.vo.PackagePriceQuote.Item(
                99L, "复合肥", new BigDecimal("0.1"), "袋", 11L, BigDecimal.TEN, BigDecimal.ONE, 0, BigDecimal.TEN);
        var quote = new com.agri_system.application.productpackage.dto.vo.PackagePriceQuote("fractional-package",7L,"套餐",
                BigDecimal.ONE, BigDecimal.ONE,null,false,BigDecimal.ONE,BigDecimal.ONE,BigDecimal.ZERO,java.util.List.of(material));
        when(packagePricingService.quoteForSale(any(),any(),any(),any(),any())).thenReturn(quote);
        var error = assertThrows(IllegalArgumentException.class, () -> com.agri_system.application.order.service.PosOrderTestFixture.withPos(orderService).createPOSOrder(command, 8L));
        org.junit.jupiter.api.Assertions.assertTrue(error.getMessage().contains("整数")); verifyNoBusinessWrites();
    }

    private void installActualQuantityValidation() {
        var repository = org.mockito.Mockito.mock(com.agri_system.domain.product.repository.ProductUnitRepository.class);
        var unitService = new com.agri_system.application.product.service.ProductUnitService(repository, productRepository);
        var bottle = new com.agri_system.domain.product.model.ProductUnit();
        bottle.setId(10L); bottle.setProductId(99L); bottle.setUnitName("瓶"); bottle.setIsBaseUnit(1);
        bottle.setStatus(1); bottle.setAllowSale(1); bottle.setBaseQuantityPerUnit(BigDecimal.ONE);
        var box = new com.agri_system.domain.product.model.ProductUnit();
        box.setId(11L); box.setProductId(99L); box.setUnitName("袋"); box.setIsBaseUnit(0);
        box.setStatus(1); box.setAllowSale(1); box.setBaseQuantityPerUnit(new BigDecimal("12"));
        when(repository.findEnabledByProductId(99L)).thenReturn(java.util.List.of(bottle,box));
        var ledger = new com.agri_system.application.inventory.service.InventoryLedgerService(null,null,null,null,null,
                unitService,null,null,null,null);
        inventoryService = org.mockito.Mockito.mock(InventoryApplicationService.class, org.mockito.Mockito.CALLS_REAL_METHODS);
        org.springframework.test.util.ReflectionTestUtils.setField(inventoryService, "ledger", ledger);
        org.mockito.Mockito.doReturn(java.util.List.of()).when(inventoryService).outbound(any(),any());
        org.mockito.Mockito.doNothing().when(inventoryService).lockProducts(any(),any());
        org.springframework.test.util.ReflectionTestUtils.setField(orderService, "inventoryService", inventoryService);
    }

    private void verifyNoBusinessWrites() {
        verify(orderRepository,never()).save(any());verify(paymentRepository,never()).save(any());
        verify(itemRepository,never()).save(any());verify(inventoryService,never()).outbound(any(),any());
        verify(creditService,never()).addDebt(any(),any(),any());verify(eventPublisher,never()).publishEvent(any(Object.class));
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

        org.mockito.Mockito.lenient().when(productPricingService.quote(any(PriceContext.class)))
                .thenReturn(new PriceQuote("normal", null, 99L, 3L, "POS", 1, null, "袋", 11L,
                        LocalDate.now(), new BigDecimal("100.00"), "RETAIL", null, false, false));
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
        var ids = new java.util.concurrent.atomic.AtomicLong(900L);
        when(itemRepository.save(any(OrderItem.class))).thenAnswer(invocation -> {
            OrderItem saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(ids.getAndIncrement());
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
