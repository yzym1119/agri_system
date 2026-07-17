package com.agri_system.application.after_sales.service;

import com.agri_system.application.after_sales.dto.command.AdminReturnCommand;
import com.agri_system.application.after_sales.dto.command.AfterSalesRefundCommand;
import com.agri_system.application.inventory.service.InventoryApplicationService;
import com.agri_system.application.performance.event.OrderRefundedEvent;
import com.agri_system.application.order.service.OrderPaymentService;
import com.agri_system.domain.after_sales.model.AfterSales;
import com.agri_system.domain.after_sales.model.AfterSalesItem;
import com.agri_system.domain.after_sales.repository.AfterSalesItemRepository;
import com.agri_system.domain.after_sales.repository.AfterSalesRepository;
import com.agri_system.domain.after_sales.service.AfterSalesDomainService;
import com.agri_system.domain.order.model.Order;
import com.agri_system.domain.order.model.OrderItem;
import com.agri_system.domain.order.repository.OrderItemRepository;
import com.agri_system.domain.order.repository.OrderRepository;
import com.agri_system.domain.product.model.Product;
import com.agri_system.domain.product.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminReturnTest {

    @InjectMocks
    private AfterSalesApplicationService afterSalesService;

    @Mock
    private AfterSalesRepository afterSalesRepository;
    @Mock
    private AfterSalesItemRepository afterSalesItemRepository;
    @Mock
    private AfterSalesDomainService afterSalesDomainService;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderItemRepository orderItemRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private InventoryApplicationService inventoryApplicationService;
    @Mock
    private OrderPaymentService orderPaymentService;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private Order order;
    private OrderItem orderItem;
    private Product product;

    @BeforeEach
    void setUp() {
        order = new Order();
        order.setId(100L);
        order.setStoreId(1L);
        order.setCustomerId(10L);
        order.setOrderStatus(6); // FINISHED
        order.setPayAmount(new BigDecimal("120.00"));
        order.setPaymentMethod(2);

        orderItem = new OrderItem();
        orderItem.setId(200L);
        orderItem.setOrderId(order.getId());
        orderItem.setProductId(300L);
        orderItem.setQuantity(new BigDecimal("2"));
        orderItem.setUnit("瓶");
        orderItem.setUnitPrice(new BigDecimal("60.00"));

        product = new Product();
        product.setId(300L);
        product.setAvgCostPrice(new BigDecimal("40.00"));
        product.setMainUnit("箱");
        product.setAuxUnit("瓶");
        product.setMainToAuxRatio(new BigDecimal("4"));

        when(orderRepository.findById(order.getId())).thenAnswer(inv -> Optional.of(order));
        when(orderItemRepository.findByOrderId(order.getId())).thenReturn(List.of(orderItem));
        when(productRepository.findById(product.getId())).thenReturn(Optional.of(product));
        when(afterSalesDomainService.generateAfterSalesNo()).thenReturn("AS202401010001");
        when(afterSalesRepository.findProcessingByOrderId(order.getId())).thenReturn(Optional.empty());
        when(afterSalesRepository.findByOrderId(order.getId())).thenReturn(List.of());
    }

    @Test
    void shouldRestockAndRefundForAdminReturn() {
        AfterSales stored = new AfterSales();
        stored.setId(999L);
        when(afterSalesRepository.save(any(AfterSales.class))).thenAnswer(invocation -> {
            AfterSales entity = invocation.getArgument(0);
            if (entity.getId() == null) {
                entity.setId(999L);
            }
            stored.setId(entity.getId());
            stored.setStatus(entity.getStatus());
            stored.setAfterSalesNo(entity.getAfterSalesNo());
            stored.setRefundAmount(entity.getRefundAmount());
            stored.setOrderId(entity.getOrderId());
            return entity;
        });

        AdminReturnCommand command = new AdminReturnCommand();
        command.setOrderId(order.getId());
        command.setRefundAmount(new BigDecimal("120.00"));
        command.setReason("客户退货");
        AdminReturnCommand.ReturnItem item = new AdminReturnCommand.ReturnItem();
        item.setOrderItemId(orderItem.getId());
        item.setQuantity(new BigDecimal("2"));
        item.setRefundAmount(new BigDecimal("120.00"));
        command.setItems(List.of(item));

        Long afterSalesId = afterSalesService.createFromAdmin(command, 8L);

        assertEquals(999L, afterSalesId);
        verify(inventoryApplicationService, times(1)).inbound(argThat(inCommand ->
                inCommand.getQuantity().compareTo(new BigDecimal("0.50000000")) == 0
                        && inCommand.getProductId().equals(product.getId())
        ), eq(8L));

        verify(orderPaymentService, times(1))
                .refund(eq(order), eq(new BigDecimal("120.00")), eq(8L));
        verify(eventPublisher, atLeastOnce()).publishEvent(any(OrderRefundedEvent.class));
    }

    @Test
    void shouldAllowAdminReturnForPartRefundedOrder() {
        order.setOrderStatus(9);
        when(afterSalesRepository.save(any(AfterSales.class))).thenAnswer(invocation -> {
            AfterSales entity = invocation.getArgument(0);
            entity.setId(999L);
            return entity;
        });

        AdminReturnCommand command = new AdminReturnCommand();
        command.setOrderId(order.getId());
        command.setRefundAmount(new BigDecimal("60.00"));
        command.setReason("二次退货");
        AdminReturnCommand.ReturnItem item = new AdminReturnCommand.ReturnItem();
        item.setOrderItemId(orderItem.getId());
        item.setQuantity(new BigDecimal("1"));
        item.setRefundAmount(new BigDecimal("60.00"));
        command.setItems(List.of(item));

        Long afterSalesId = afterSalesService.createFromAdmin(command, 8L);

        assertEquals(999L, afterSalesId);
        verify(orderPaymentService).refund(eq(order), eq(new BigDecimal("60.00")), eq(8L));
        verify(eventPublisher).publishEvent(any(OrderRefundedEvent.class));
    }

    @Test
    void shouldMarkOrderRefundedWhenCumulativeRefundReachesPayAmount() {
        order.setOrderStatus(9);
        AfterSales finished = new AfterSales();
        finished.setId(501L);
        finished.setOrderId(order.getId());
        finished.setStatus(AfterSales.STATUS_FINISHED);
        finished.setRefundAmount(new BigDecimal("60.00"));
        when(afterSalesRepository.findByOrderId(order.getId())).thenReturn(List.of(finished));
        when(afterSalesRepository.save(any(AfterSales.class))).thenAnswer(invocation -> {
            AfterSales entity = invocation.getArgument(0);
            entity.setId(999L);
            return entity;
        });

        AdminReturnCommand command = new AdminReturnCommand();
        command.setOrderId(order.getId());
        command.setRefundAmount(new BigDecimal("60.00"));
        command.setReason("补退剩余金额");
        AdminReturnCommand.ReturnItem item = new AdminReturnCommand.ReturnItem();
        item.setOrderItemId(orderItem.getId());
        item.setQuantity(new BigDecimal("1"));
        item.setRefundAmount(new BigDecimal("60.00"));
        command.setItems(List.of(item));

        afterSalesService.createFromAdmin(command, 8L);

        verify(orderRepository).save(argThat(savedOrder ->
                savedOrder.getId().equals(order.getId()) && savedOrder.getOrderStatus() == 8
        ));
    }

    @Test
    void shouldRejectAdminReturnWhenCumulativeQuantityExceedsPurchased() {
        AfterSales finished = new AfterSales();
        finished.setId(501L);
        finished.setOrderId(order.getId());
        finished.setStatus(AfterSales.STATUS_FINISHED);
        when(afterSalesRepository.findByOrderId(order.getId())).thenReturn(List.of(finished));

        AfterSalesItem historyItem = new AfterSalesItem();
        historyItem.setAfterSalesId(501L);
        historyItem.setOrderItemId(orderItem.getId());
        historyItem.setProductId(product.getId());
        historyItem.setQuantity(new BigDecimal("1.5000"));
        historyItem.setRefundAmount(new BigDecimal("90.00"));
        when(afterSalesItemRepository.findByAfterSalesId(501L)).thenReturn(List.of(historyItem));

        AdminReturnCommand command = new AdminReturnCommand();
        command.setOrderId(order.getId());
        command.setRefundAmount(new BigDecimal("60.00"));
        command.setReason("超量退货");
        AdminReturnCommand.ReturnItem item = new AdminReturnCommand.ReturnItem();
        item.setOrderItemId(orderItem.getId());
        item.setQuantity(new BigDecimal("1"));
        item.setRefundAmount(new BigDecimal("60.00"));
        command.setItems(List.of(item));

        assertThrows(IllegalArgumentException.class, () -> afterSalesService.createFromAdmin(command, 8L));
        verify(orderPaymentService, never()).refund(eq(order), eq(new BigDecimal("60.00")), eq(8L));
    }

    private AfterSalesItem buildAfterSalesItem() {
        AfterSalesItem item = new AfterSalesItem();
        item.setAfterSalesId(999L);
        item.setOrderItemId(orderItem.getId());
        item.setProductId(product.getId());
        item.setQuantity(new BigDecimal("2"));
        item.setRefundAmount(new BigDecimal("120.00"));
        return item;
    }
}
