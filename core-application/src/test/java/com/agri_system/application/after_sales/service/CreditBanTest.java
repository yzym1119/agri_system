package com.agri_system.application.after_sales.service;

import com.agri_system.application.after_sales.dto.command.AdminReturnCommand;
import com.agri_system.application.inventory.service.InventoryApplicationService;
import com.agri_system.application.order.service.OrderPaymentService;
import com.agri_system.domain.after_sales.repository.AfterSalesItemRepository;
import com.agri_system.domain.after_sales.repository.AfterSalesRepository;
import com.agri_system.domain.after_sales.service.AfterSalesDomainService;
import com.agri_system.domain.order.enums.OrderMainPaymentMethod;
import com.agri_system.domain.order.model.Order;
import com.agri_system.domain.order.repository.OrderItemRepository;
import com.agri_system.domain.order.repository.OrderRepository;
import com.agri_system.domain.product.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CreditBanTest {

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
    @Mock private com.agri_system.application.order.service.OrderApplicationService orderApplicationService;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Test
    void creditAdminReturnPersistsDebtReductionAndPhysicalReturnTogether() {
        Order order=new Order();order.setId(10L);order.setStoreId(1L);order.setCustomerId(12L);order.setOrderStatus(6);order.setPaymentMethod(6);order.setPayAmount(new BigDecimal("100.00"));
        when(orderRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(order));when(orderRepository.findById(10L)).thenReturn(Optional.of(order));
        var original=new com.agri_system.domain.order.model.OrderItem();original.setId(20L);original.setOrderId(10L);original.setProductId(30L);original.setQuantity(new BigDecimal("10"));original.setUnit("袋");original.setUnitPrice(new BigDecimal("10.00"));original.setTotalPrice(new BigDecimal("100.00"));
        when(orderItemRepository.findByOrderId(10L)).thenReturn(java.util.List.of(original));
        var product=new com.agri_system.domain.product.model.Product();product.setId(30L);product.setAvgCostPrice(new BigDecimal("5"));when(productRepository.findById(30L)).thenReturn(Optional.of(product));
        var saved=new java.util.concurrent.atomic.AtomicReference<com.agri_system.domain.after_sales.model.AfterSales>();
        when(afterSalesRepository.save(any())).thenAnswer(invocation->{com.agri_system.domain.after_sales.model.AfterSales fact=invocation.getArgument(0);fact.setId(40L);saved.set(fact);return fact;});
        when(orderPaymentService.refund(eq(order),eq(new BigDecimal("30.00")),eq(7L))).thenReturn(new OrderPaymentService.RefundResult(new BigDecimal("0.00"),new BigDecimal("30.00")));
        var command=new AdminReturnCommand();command.setOrderId(10L);command.setRefundAmount(new BigDecimal("30.00"));command.setQuoteId("confirmed-original-debt");command.setReason("退回3袋");
        var item=new AdminReturnCommand.ReturnItem();item.setOrderItemId(20L);item.setQuantity(new BigDecimal("3"));item.setRefundAmount(new BigDecimal("30.00"));command.setItems(java.util.List.of(item));
        assertEquals(40L,afterSalesService.createFromAdmin(command,7L));
        assertEquals(new BigDecimal("30.00"),saved.get().getRefundAmount());assertEquals(new BigDecimal("30.00"),saved.get().getCreditReductionAmount());assertEquals(new BigDecimal("0.00"),saved.get().getCashRefundAmount());
        assertEquals(com.agri_system.domain.after_sales.model.AfterSales.STATUS_FINISHED,saved.get().getStatus());
        var goods=org.mockito.ArgumentCaptor.forClass(com.agri_system.application.inventory.dto.command.InventoryInCommand.class);verify(inventoryApplicationService).inbound(goods.capture(),eq(7L));
        assertEquals(new BigDecimal("3"),goods.getValue().getQuantity());assertEquals("袋",goods.getValue().getUnit());assertEquals(20,goods.getValue().getSourceLineNo());
        var event=org.mockito.ArgumentCaptor.forClass(com.agri_system.application.performance.event.OrderRefundedEvent.class);verify(eventPublisher).publishEvent(event.capture());assertEquals(new BigDecimal("30.00"),event.getValue().refundAmount());
    }
}
