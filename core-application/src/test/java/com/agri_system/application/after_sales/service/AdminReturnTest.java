package com.agri_system.application.after_sales.service;

import com.agri_system.application.after_sales.dto.command.AdminReturnCommand;
import com.agri_system.application.after_sales.dto.command.AfterSalesRefundCommand;
import com.agri_system.application.inventory.service.InventoryApplicationService;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        orderItem.setUnitPrice(new BigDecimal("60.00"));

        product = new Product();
        product.setId(300L);
        product.setAvgCostPrice(new BigDecimal("40.00"));

        when(orderRepository.findById(order.getId())).thenAnswer(inv -> Optional.of(order));
        when(orderItemRepository.findByOrderId(order.getId())).thenReturn(List.of(orderItem));
        when(productRepository.findById(product.getId())).thenReturn(Optional.of(product));
        when(afterSalesDomainService.generateAfterSalesNo()).thenReturn("AS202401010001");
        when(afterSalesRepository.findProcessingByOrderId(order.getId())).thenReturn(Optional.empty());
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
        when(afterSalesRepository.findById(999L)).thenReturn(Optional.of(stored));
        when(afterSalesItemRepository.findByAfterSalesId(999L)).thenReturn(List.of(buildAfterSalesItem()));

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
        verify(inventoryApplicationService, times(1)).inbound(any(), eq(8L));

        AfterSalesRefundCommand refundCommand = new AfterSalesRefundCommand();
        refundCommand.setAfterSalesId(afterSalesId);
        afterSalesService.refund(refundCommand, 8L);

        verify(orderPaymentService, times(1))
                .refund(eq(order), eq(new BigDecimal("120.00")), eq(8L));
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

