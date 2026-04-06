package com.agri_system.application.after_sales.service;

import com.agri_system.application.after_sales.dto.command.AdminReturnCommand;
import com.agri_system.application.inventory.service.InventoryApplicationService;
import com.agri_system.application.order.service.OrderPaymentService;
import com.agri_system.domain.after_sales.repository.AfterSalesItemRepository;
import com.agri_system.domain.after_sales.repository.AfterSalesRepository;
import com.agri_system.domain.after_sales.service.AfterSalesDomainService;
import com.agri_system.domain.order.enums.PaymentMethod;
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

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
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

    @Test
    void shouldBlockCreditOrderAdminReturn() {
        Order creditOrder = new Order();
        creditOrder.setId(10L);
        creditOrder.setStoreId(1L);
        creditOrder.setOrderStatus(6);
        creditOrder.setPaymentMethod(PaymentMethod.COUPON.getCode()); // 赊销
        when(orderRepository.findById(10L)).thenReturn(Optional.of(creditOrder));
        when(afterSalesRepository.findProcessingByOrderId(10L)).thenReturn(Optional.empty());

        AdminReturnCommand command = new AdminReturnCommand();
        command.setOrderId(10L);
        command.setRefundAmount(new BigDecimal("10.00"));
        AdminReturnCommand.ReturnItem item = new AdminReturnCommand.ReturnItem();
        item.setOrderItemId(1L);
        item.setQuantity(BigDecimal.ONE);
        item.setRefundAmount(new BigDecimal("10.00"));
        command.setItems(java.util.List.of(item));

        assertThrows(IllegalArgumentException.class,
                () -> afterSalesService.createFromAdmin(command, 1L),
                "赊销订单应该被禁止发起售后退货");
    }
}

