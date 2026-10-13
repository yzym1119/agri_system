package com.agri_system.application.order.service;

import com.agri_system.application.credit.service.CreditApplicationService;
import com.agri_system.application.inventory.dto.command.InventoryInCommand;
import com.agri_system.application.inventory.service.InventoryApplicationService;
import com.agri_system.application.order.dto.command.OrderRefundCommand;
import com.agri_system.application.performance.event.OrderRefundedEvent;
import com.agri_system.application.product.service.ProductPricingService;
import com.agri_system.application.wallet.service.WalletApplicationService;
import com.agri_system.domain.after_sales.repository.AfterSalesItemRepository;
import com.agri_system.domain.after_sales.repository.AfterSalesRepository;
import com.agri_system.domain.customer.repository.CustomerRepository;
import com.agri_system.domain.order.model.Order;
import com.agri_system.domain.order.model.OrderItem;
import com.agri_system.domain.order.repository.OrderDeliveryRepository;
import com.agri_system.domain.order.repository.OrderItemRepository;
import com.agri_system.domain.order.repository.OrderPaymentIntentRepository;
import com.agri_system.domain.order.repository.OrderPaymentRepository;
import com.agri_system.domain.order.repository.OrderRepository;
import com.agri_system.domain.order.service.OrderDomainService;
import com.agri_system.domain.product.model.Product;
import com.agri_system.domain.product.repository.ProductRepository;
import com.agri_system.domain.store.repository.StaffRepository;
import com.agri_system.domain.store.repository.StoreRepository;
import com.agri_system.domain.wallet.repository.WalletAuthCodeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderRefundFlowTest {

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

    @InjectMocks
    private OrderApplicationService orderService;

    @Test
    void refundOrderShouldSupportPartialRefundWithItemDetails() {
        Order order = new Order();
        order.setId(10L);
        order.setStoreId(2L);
        order.setOrderStatus(2);
        order.setPayAmount(new BigDecimal("100.00"));
        order.setPaymentMethod(2);

        OrderItem orderItem = new OrderItem();
        orderItem.setId(101L);
        orderItem.setOrderId(10L);
        orderItem.setProductId(501L);
        orderItem.setQuantity(new BigDecimal("4"));
        orderItem.setUnit("瓶");
        orderItem.setUnitPrice(new BigDecimal("25.00"));
        orderItem.setTotalPrice(new BigDecimal("100.00"));
        orderItem.setCostPriceSnapshot(new BigDecimal("12.00"));

        Product product = new Product();
        product.setId(501L);
        product.setAuxUnit("瓶");
        product.setMainToAuxRatio(new BigDecimal("4"));

        OrderRefundCommand command = new OrderRefundCommand();
        command.setOrderId(10L);
        command.setRefundReason("部分退款");
        command.setRefundAmount(new BigDecimal("25.00"));
        OrderRefundCommand.RefundItem refundItem = new OrderRefundCommand.RefundItem();
        refundItem.setOrderItemId(101L);
        refundItem.setQuantity(new BigDecimal("1"));
        refundItem.setRefundAmount(new BigDecimal("25.00"));
        command.setItems(List.of(refundItem));

        when(orderRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(order));
        when(itemRepository.findByOrderId(10L)).thenReturn(List.of(orderItem));
        when(productRepository.findById(501L)).thenReturn(Optional.of(product));
        when(afterSalesRepository.findByOrderId(10L)).thenReturn(List.of());
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        orderService.refundOrder(command, 7L);
        ArgumentCaptor<List<com.agri_system.domain.after_sales.model.AfterSalesItem>> facts = ArgumentCaptor.forClass(List.class);
        verify(afterSalesItemRepository).saveBatch(facts.capture());
        assertEquals(101L, facts.getValue().get(0).getOrderItemId());
        assertEquals(new BigDecimal("1"), facts.getValue().get(0).getQuantity());
        assertEquals(new BigDecimal("25.00"), facts.getValue().get(0).getRefundAmount());

        verify(paymentService).refund(eq(order), eq(new BigDecimal("25.00")), eq(7L));

        ArgumentCaptor<InventoryInCommand> inventoryCaptor = ArgumentCaptor.forClass(InventoryInCommand.class);
        verify(inventoryService).inbound(inventoryCaptor.capture(), eq(7L));
        assertEquals(new BigDecimal("1"), inventoryCaptor.getValue().getQuantity());
        assertEquals("瓶", inventoryCaptor.getValue().getUnit());
        assertEquals(101, inventoryCaptor.getValue().getSourceLineNo());
        assertEquals("ORDER_REFUND", inventoryCaptor.getValue().getSourceType());
        assertEquals(10L, inventoryCaptor.getValue().getSourceId());

        ArgumentCaptor<OrderRefundedEvent> eventCaptor = ArgumentCaptor.forClass(OrderRefundedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertEquals(Map.of(101L, new BigDecimal("1")), eventCaptor.getValue().refundItems());
        assertEquals(new BigDecimal("25.00"), eventCaptor.getValue().refundAmount());
    }
    @Test
    void packageFirstReturnWithZeroCentLimitStillRestocksAndSavesFact() {
        Order order = new Order(); order.setId(10L); order.setStoreId(2L); order.setOrderStatus(2);
        order.setPayAmount(new BigDecimal("0.01")); order.setPaymentMethod(1);
        OrderItem material = new OrderItem(); material.setId(101L); material.setOrderId(10L);
        material.setPackageId(7L); material.setProductId(501L); material.setQuantity(new BigDecimal("3"));
        material.setUnit("瓶"); material.setTotalPrice(new BigDecimal("0.01"));
        Product product = new Product(); product.setId(501L);
        when(orderRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(order));
        when(itemRepository.findByOrderId(10L)).thenReturn(List.of(material));
        when(productRepository.findById(501L)).thenReturn(Optional.of(product));
        when(afterSalesRepository.save(any())).thenAnswer(invocation -> {
            com.agri_system.domain.after_sales.model.AfterSales fact = invocation.getArgument(0);
            fact.setId(300L); return fact;
        });
        OrderRefundCommand command = new OrderRefundCommand(); command.setOrderId(10L);
        command.setRefundReason("逐件退货"); command.setRefundAmount(new BigDecimal("0.00"));
        OrderRefundCommand.RefundItem requested = new OrderRefundCommand.RefundItem(); requested.setOrderItemId(101L);
        requested.setQuantity(BigDecimal.ONE); requested.setRefundAmount(new BigDecimal("0.00")); command.setItems(List.of(requested));

        orderService.refundOrder(command,7L);

        org.mockito.Mockito.verifyNoInteractions(paymentService);
        verify(inventoryService).inbound(any(),eq(7L));
        ArgumentCaptor<List<com.agri_system.domain.after_sales.model.AfterSalesItem>> facts = ArgumentCaptor.forClass(List.class);
        verify(afterSalesItemRepository).saveBatch(facts.capture());
        assertEquals(300L,facts.getValue().get(0).getAfterSalesId());
        assertEquals(BigDecimal.ONE,facts.getValue().get(0).getQuantity());
        ArgumentCaptor<OrderRefundedEvent> event = ArgumentCaptor.forClass(OrderRefundedEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertEquals("AFTER_SALES:300",event.getValue().refundKey());
    }

}
