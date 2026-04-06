package com.agri_system.application.after_sales.service;

import com.agri_system.application.after_sales.dto.command.ClientApplyCommand;
import com.agri_system.application.inventory.service.InventoryApplicationService;
import com.agri_system.application.order.service.OrderPaymentService;
import com.agri_system.domain.after_sales.repository.AfterSalesItemRepository;
import com.agri_system.domain.after_sales.repository.AfterSalesRepository;
import com.agri_system.domain.after_sales.service.AfterSalesDomainService;
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

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ClientLimitTest {

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

    @Test
    void shouldRejectClientApplyWhenOrderAlreadyShipped() {
        Order order = new Order();
        order.setId(1L);
        order.setCustomerId(2L);
        order.setOrderStatus(5); // SHIPPED
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(afterSalesRepository.findProcessingByOrderId(order.getId())).thenReturn(Optional.empty());

        ClientApplyCommand command = new ClientApplyCommand();
        command.setOrderId(1L);
        command.setReason("不想要了");

        assertThrows(IllegalArgumentException.class,
                () -> afterSalesService.applyFromClient(command, 2L),
                "已发货订单不允许拦截售后");
    }
}

