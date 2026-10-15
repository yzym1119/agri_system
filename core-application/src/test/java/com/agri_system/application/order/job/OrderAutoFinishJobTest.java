package com.agri_system.application.order.job;

import com.agri_system.application.order.service.OrderApplicationService;
import com.agri_system.domain.order.model.Order;
import com.agri_system.domain.order.model.OrderDelivery;
import com.agri_system.domain.order.repository.OrderRepository;
import com.agri_system.domain.order.repository.OrderDeliveryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class OrderAutoFinishJobTest {
    @Mock OrderRepository orderRepository;
    @Mock OrderDeliveryRepository deliveryRepository;
    @Mock OrderApplicationService orderService;
    @InjectMocks OrderAutoFinishJob job;
    @Test void oldExpressUsesUnifiedReceiptAndLocalDeliveryStaysManual() {
        var local=order(1L);var express=order(2L);var recent=order(3L);
        when(orderRepository.findShippedOrdersBefore(any())).thenReturn(List.of(local,express,recent));
        lenient().when(deliveryRepository.findByOrderId(1L)).thenReturn(Optional.of(delivery(2,20)));
        lenient().when(deliveryRepository.findByOrderId(2L)).thenReturn(Optional.of(delivery(3,20)));
        lenient().when(deliveryRepository.findByOrderId(3L)).thenReturn(Optional.of(delivery(3,2)));
        job.autoFinishOrders();
        assertEquals(5,local.getOrderStatus());assertEquals(5,recent.getOrderStatus());
        verify(orderService).confirmReceipt(2L,null);verify(orderService,never()).confirmReceipt(1L,null);
        verify(orderService,never()).confirmReceipt(3L,null);verify(orderRepository,never()).save(any());
    }
    private Order order(Long id) {var order=new Order();order.setId(id);order.setOrderStatus(5);return order;}
    private OrderDelivery delivery(int type,int days) {
        var delivery=new OrderDelivery();delivery.setDeliveryType(type);delivery.setDeliveryStatus(3);
        delivery.setDeliveryAt(LocalDateTime.now().minusDays(days));return delivery;
    }
}
