package com.agri_system.application.order.service;

import com.agri_system.application.order.dto.command.OrderItemDTO;
import com.agri_system.application.order.dto.command.POSOrderCreateCommand;
import com.agri_system.domain.order.model.Order;
import com.agri_system.domain.order.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * POS外部支付测试
 * 验证POS选微信支付时，直接成交不报错，且库存扣减正确
 * 
 * @author AI Generated
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class POSOfflinePayTest {
    
    @Autowired
    private OrderApplicationService orderService;
    
    @Autowired
    private OrderRepository orderRepository;
    
    @Test
    void testPOSWechatPay() {
        // Given: 准备POS订单（微信支付）
        POSOrderCreateCommand command = new POSOrderCreateCommand();
        command.setStoreId(1L);
        command.setCustomerId(1L);
        command.setPaymentType(2); // 微信支付
        command.setStaffId(1L);
        
        List<OrderItemDTO> items = new ArrayList<>();
        OrderItemDTO item = new OrderItemDTO();
        item.setProductId(1L);
        item.setQuantity(new BigDecimal("10"));
        item.setUnitPrice(new BigDecimal("50.00"));
        items.add(item);
        command.setItems(items);
        
        // When: 创建POS订单
        Long orderId = orderService.createPOSOrder(command, 1L);
        
        // Then: 验证订单状态为已支付
        Order order = orderRepository.findById(orderId).orElseThrow();
        assertEquals(2, order.getOrderStatus(), "订单状态应为已支付");
        assertEquals(2, order.getPaymentMethod(), "支付方式应为微信");
        
        // 验证库存已扣减（TODO: 需要验证库存扣减）
    }
}







