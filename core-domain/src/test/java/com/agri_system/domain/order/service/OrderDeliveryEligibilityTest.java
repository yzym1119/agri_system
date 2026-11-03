package com.agri_system.domain.order.service;

import com.agri_system.domain.after_sales.model.AfterSales;
import com.agri_system.domain.after_sales.repository.AfterSalesRepository;
import com.agri_system.domain.order.model.Order;
import com.agri_system.domain.order.model.OrderDelivery;
import com.agri_system.domain.order.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrderDeliveryEligibilityTest {
    private final AfterSalesRepository afterSales=mock(AfterSalesRepository.class);
    private final OrderRepository orders=mock(OrderRepository.class);
    private Order order(int status){var o=new Order();o.setId(7L);o.setOrderStatus(status);return o;}
    private OrderDelivery delivery(Integer type,int status){var d=new OrderDelivery();d.setDeliveryType(type);d.setDeliveryStatus(status);return d;}
    @Test void 原配送状态及部分退货允许剩余物料交付() {
        when(afterSales.findByOrderId(7L)).thenReturn(List.of());when(orders.hasRemainingDeliveryQuantity(7L)).thenReturn(true);
        for(int type:List.of(2,3)) {
            assertTrue(OrderDeliveryEligibility.canDeliver(order(4),delivery(type,2),true,afterSales,orders));
            assertTrue(OrderDeliveryEligibility.canDeliver(order(5),delivery(type,3),false,afterSales,orders));
            assertTrue(OrderDeliveryEligibility.canDeliver(order(9),delivery(type,2),true,afterSales,orders));
            assertTrue(OrderDeliveryEligibility.canDeliver(order(9),delivery(type,3),false,afterSales,orders));
        }
        when(orders.hasRemainingDeliveryQuantity(7L)).thenReturn(false);
        assertFalse(OrderDeliveryEligibility.canDeliver(order(9),delivery(2,3),false,afterSales,orders));
    }
    @Test void 原处理中售后阻断且不继续读取剩余物料() {
        for(int state:List.of(10,40)) {
            var fact=new AfterSales();fact.setStatus(state);when(afterSales.findByOrderId(7L)).thenReturn(List.of(fact));
            assertFalse(OrderDeliveryEligibility.canDeliver(order(5),delivery(2,3),false,afterSales,orders));
        }
        verifyNoInteractions(orders);
        var done=new AfterSales();done.setStatus(50);when(afterSales.findByOrderId(7L)).thenReturn(List.of(done));when(orders.hasRemainingDeliveryQuantity(7L)).thenReturn(true);
        assertTrue(OrderDeliveryEligibility.canDeliver(order(9),delivery(2,3),false,afterSales,orders));
    }
    @Test void 无配送未知类型或无效状态不查询售后库存证据() {
        assertFalse(OrderDeliveryEligibility.canDeliver(order(5),null,false,afterSales,orders));
        for(Integer type:new Integer[]{null,1,99})assertFalse(OrderDeliveryEligibility.canDeliver(order(5),delivery(type,3),false,afterSales,orders));
        for(int status:List.of(3,6,7,8,10,40))assertFalse(OrderDeliveryEligibility.canDeliver(order(status),delivery(2,3),false,afterSales,orders));
        assertFalse(OrderDeliveryEligibility.canDeliver(order(5),delivery(2,4),false,afterSales,orders));
        verifyNoInteractions(afterSales,orders);
    }
}
